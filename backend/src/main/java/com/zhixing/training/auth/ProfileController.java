package com.zhixing.training.auth;

import com.zhixing.training.api.ApiException;
import com.zhixing.training.api.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/me")
public class ProfileController {
    private final UserRepository users; private final PasswordEncoder encoder; private final SessionRepository sessions;
    public ProfileController(UserRepository users, PasswordEncoder encoder, SessionRepository sessions) { this.users=users;this.encoder=encoder;this.sessions=sessions; }
    @GetMapping public ApiResponse<AuthDtos.UserView> me(@AuthenticationPrincipal Jwt jwt) {
        return ApiResponse.ok(AuthDtos.UserView.from(users.findById(Long.parseLong(jwt.getSubject())).orElseThrow()));
    }
    @PutMapping("/profile")
    public ApiResponse<AuthDtos.UserView> profile(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody AuthDtos.ProfileRequest body) {
        long id = Long.parseLong(jwt.getSubject());
        if (!users.updateProfile(id, body.version(), body.name().trim(), blank(body.email()), blank(body.phone()), blank(body.jobTitle())))
            throw new ApiException(HttpStatus.CONFLICT, "VERSION_CONFLICT", "资料已被其他操作更新，请刷新后重试");
        return ApiResponse.ok(AuthDtos.UserView.from(users.findById(id).orElseThrow()));
    }
    @PutMapping("/password")
    @org.springframework.transaction.annotation.Transactional
    public ApiResponse<Void> password(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody AuthDtos.PasswordRequest body) {
        long id = Long.parseLong(jwt.getSubject()); UserRecord user = users.findById(id).orElseThrow();
        AuthService.validatePasswordBytes(body.currentPassword()); AuthService.validatePasswordBytes(body.newPassword());
        if (!encoder.matches(body.currentPassword(), user.passwordHash())) throw new ApiException(HttpStatus.BAD_REQUEST,"PASSWORD_INVALID","当前密码不正确");
        if (!body.newPassword().matches(".*[A-Za-z].*") || !body.newPassword().matches(".*\\d.*")) throw new ApiException(HttpStatus.BAD_REQUEST,"PASSWORD_WEAK","新密码需同时包含字母和数字");
        users.updatePassword(id, encoder.encode(body.newPassword())); sessions.revokeAll(id); return ApiResponse.ok();
    }
    private static String blank(String value) { return value == null || value.isBlank() ? null : value.trim(); }
}
