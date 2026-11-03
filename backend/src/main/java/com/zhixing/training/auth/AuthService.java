package com.zhixing.training.auth;

import com.zhixing.training.api.ApiException;
import com.zhixing.training.config.JwtProperties;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;
import java.util.UUID;

@Service
public class AuthService {
    public static final String REFRESH_COOKIE = "ZHIXING_REFRESH";
    private final UserRepository users;
    private final SessionRepository sessions;
    private final PasswordEncoder encoder;
    private final JwtEncoder jwt;
    private final JwtProperties props;
    private final SecureRandom random = new SecureRandom();
    private final String dummyHash;
    public record Issued(AuthDtos.LoginResponse response, String refreshToken, Instant refreshExpiresAt) { }

    public AuthService(UserRepository users, SessionRepository sessions, PasswordEncoder encoder, JwtEncoder jwt, JwtProperties props) {
        this.users = users; this.sessions = sessions; this.encoder = encoder; this.jwt = jwt; this.props = props;
        this.dummyHash = encoder.encode(randomToken());
    }

    @Transactional(noRollbackFor = ApiException.class)
    public Issued login(String employeeNo, String password, String agent, String ip) {
        String normalized = employeeNo.trim().toUpperCase(Locale.ROOT);
        validatePasswordBytes(password);
        UserRecord user = users.lockByEmployeeNo(normalized).orElse(null);
        // Run BCrypt even for an unknown account so the two failure paths have comparable cost.
        if (user != null && user.lockedUntil() != null && user.lockedUntil().isAfter(Instant.now())) throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "ACCOUNT_LOCKED", "登录失败次数过多，请稍后重试");
        boolean matched = encoder.matches(password, user == null || !user.active() || user.passwordHash().startsWith("!") ? dummyHash : user.passwordHash());
        if (user == null || !matched || !user.active()) {
            if (user != null && user.active()) {
                int count = user.lockedUntil() != null ? 1 : user.failedLoginCount() + 1;
                users.recordLoginFailure(user, count, count >= 5 ? Instant.now().plus(Duration.ofMinutes(15)) : null);
            }
            throw unauthorized();
        }
        UUID sid = UUID.randomUUID();
        String refresh = randomToken();
        Instant expires = Instant.now().plus(Duration.ofHours(Math.max(1, props.refreshHours())));
        sessions.insert(sid, user.id(), hash(refresh), expires, agent, ip);
        users.recordLoginSuccess(user.id());
        return new Issued(new AuthDtos.LoginResponse(accessToken(user, sid), Duration.ofMinutes(props.accessMinutes()).toSeconds(), AuthDtos.UserView.from(user)), refresh, expires);
    }

    @Transactional
    public Issued refresh(String raw, String agent, String ip) {
        if (raw == null || raw.isBlank()) throw unauthorized();
        SessionRepository.Session old = sessions.findActiveByHash(hash(raw)).orElseThrow(this::unauthorized);
        UserRecord user = users.findById(old.userId()).filter(UserRecord::active).orElseThrow(this::unauthorized);
        UUID sid = UUID.randomUUID(); String next = randomToken();
        Instant expires = old.expiresAt();
        sessions.rotate(old.id(), sid, user.id(), hash(next), expires, agent, ip);
        return new Issued(new AuthDtos.LoginResponse(accessToken(user, sid), Duration.ofMinutes(props.accessMinutes()).toSeconds(), AuthDtos.UserView.from(user)), next, expires);
    }

    public void logout(String raw) {
        if (raw != null && !raw.isBlank()) sessions.findActiveByHash(hash(raw)).ifPresent(s -> sessions.revoke(s.id()));
    }
    public void logoutAll(long userId) { sessions.revokeAll(userId); }
    public String accessToken(UserRecord user, UUID sid) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder().issuer(props.issuer()).audience(java.util.List.of(props.audience())).issuedAt(now)
                .expiresAt(now.plus(Duration.ofMinutes(props.accessMinutes()))).subject(Long.toString(user.id())).id(UUID.randomUUID().toString())
                .claim("sid", sid.toString()).claim("role", user.roleCode()).build();
        return jwt.encode(JwtEncoderParameters.from(org.springframework.security.oauth2.jwt.JwsHeader.with(org.springframework.security.oauth2.jose.jws.MacAlgorithm.HS256).build(), claims)).getTokenValue();
    }
    private String randomToken() { byte[] bytes = new byte[32]; random.nextBytes(bytes); return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes); }
    public static String hash(String value) { try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); } catch (Exception e) { throw new IllegalStateException(e); } }
    public static void validatePasswordBytes(String value) {
        if (value.getBytes(StandardCharsets.UTF_8).length > 72) throw new ApiException(HttpStatus.BAD_REQUEST,"PASSWORD_TOO_LONG","密码 UTF-8 编码不能超过 72 字节");
    }
    private ApiException unauthorized() { return new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "工号或密码不正确"); }
}
