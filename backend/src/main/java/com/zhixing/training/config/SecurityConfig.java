package com.zhixing.training.config;

import com.zhixing.training.auth.AuthService;
import com.zhixing.training.auth.SessionRepository;
import com.zhixing.training.auth.UserRepository;
import com.zhixing.training.auth.UserRecord;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.oauth2.server.resource.web.BearerTokenAuthenticationFilter;
import org.springframework.core.convert.converter.Converter;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.OctetSequenceKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {
    @Bean PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(12); }

    @Bean SecretKey jwtSecret(JwtProperties props, Environment env) {
        String configured = props.secret();
        if (configured != null && !configured.isBlank()) {
            byte[] decoded = Base64.getDecoder().decode(configured);
            if (decoded.length < 32) throw new IllegalStateException("TRAINING_JWT_SECRET must decode to at least 32 bytes");
            return new SecretKeySpec(decoded, "HmacSHA256");
        }
        if (!env.matchesProfiles("local", "test") || env.matchesProfiles("mysql")) throw new IllegalStateException("Non-local profiles require TRAINING_JWT_SECRET");
        byte[] generated = new byte[32]; new SecureRandom().nextBytes(generated);
        return new SecretKeySpec(generated, "HmacSHA256");
    }
    @Bean JwtEncoder jwtEncoder(SecretKey key) {
        OctetSequenceKey jwk = new OctetSequenceKey.Builder(key).algorithm(JWSAlgorithm.HS256).keyID("zhixing").build();
        return new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(jwk)));
    }
    @Bean JwtDecoder jwtDecoder(SecretKey key, JwtProperties props) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
        decoder.setJwtValidator(new org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator<>(
            org.springframework.security.oauth2.jwt.JwtValidators.createDefaultWithIssuer(props.issuer()),
            new org.springframework.security.oauth2.jwt.JwtClaimValidator<java.util.List<String>>("aud", a -> a != null && a.contains(props.audience()))));
        return decoder;
    }

    @Bean SecurityFilterChain security(HttpSecurity http, UserRepository users, SessionRepository sessions, com.zhixing.training.api.SecurityErrors errors, Environment env, com.zhixing.training.audit.AuditService audit) throws Exception {
        CookieCsrfTokenRepository csrf = new CookieCsrfTokenRepository();
        boolean secure = env.getProperty("zhixing.cookie-secure", Boolean.class, !env.matchesProfiles("local", "test"));
        csrf.setCookieCustomizer(c -> c.httpOnly(true).sameSite("Strict").secure(secure).path("/api/v1"));
        var csrfHandler = new org.springframework.security.web.csrf.XorCsrfTokenRequestAttributeHandler();
        http.csrf(c -> c.csrfTokenRepository(csrf).csrfTokenRequestHandler(csrfHandler))
                .cors(cors -> {})
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(e -> e.authenticationEntryPoint((req, res, ex) -> errors.write(res,401,"UNAUTHENTICATED","请先登录"))
                        .accessDeniedHandler((req, res, ex) -> errors.write(res,403,"FORBIDDEN","无权操作或请求验证失败")))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/v1/auth/csrf", "/api/v1/auth/login", "/api/v1/auth/refresh", "/api/v1/auth/logout", "/api/v1/auth/account-setup", "/actuator/health", "/error").permitAll()
                        .requestMatchers(HttpMethod.GET,"/api/v1/certificates/verify").permitAll()
                        .requestMatchers("/api/v1/employee/**").hasAnyRole("EMPLOYEE", "ADMIN")
                        .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
                        .requestMatchers("/api/v1/resources/**").authenticated()
                        .requestMatchers("/api/v1/me", "/api/v1/me/**", "/api/v1/auth/me", "/api/v1/auth/logout-all").authenticated()
                        .anyRequest().denyAll())
                .oauth2ResourceServer(o -> o.jwt(j -> j.jwtAuthenticationConverter(new CurrentUserConverter(users, sessions)))
                    .authenticationEntryPoint((req,res,ex) -> errors.write(res,401,"SESSION_EXPIRED","登录已过期，请重新登录")))
                .headers(h -> h.frameOptions(f -> f.deny()));
        http.addFilterBefore(new com.zhixing.training.audit.AdminAuditFilter(audit), org.springframework.security.web.csrf.CsrfFilter.class);
        return http.build();
    }

    static final class CurrentUserConverter implements Converter<Jwt, AbstractAuthenticationToken> {
        private final UserRepository users; private final SessionRepository sessions;
        CurrentUserConverter(UserRepository users, SessionRepository sessions) { this.users = users; this.sessions = sessions; }
        @Override public AbstractAuthenticationToken convert(Jwt jwt) {
            try {
                long id = Long.parseLong(jwt.getSubject()); UUID sid = UUID.fromString(jwt.getClaimAsString("sid"));
                UserRecord user = users.findById(id).filter(UserRecord::active).orElseThrow(() -> new BadCredentialsException("inactive"));
                if (!sessions.isActive(sid, id)) throw new BadCredentialsException("session revoked");
                String role = user.roleCode().toUpperCase();
                return new JwtAuthenticationToken(jwt, List.of(new SimpleGrantedAuthority("ROLE_" + role)), user.employeeNo());
            } catch (RuntimeException e) { if (e instanceof BadCredentialsException b) throw b; throw new BadCredentialsException("invalid token", e); }
        }
    }
}
