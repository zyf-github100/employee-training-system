package com.zhixing.training.auth;

import com.zhixing.training.api.ApiException;
import com.zhixing.training.api.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.web.csrf.CsrfToken;
import java.time.Duration;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
    private final AuthService auth; private final UserRepository users; private final boolean secureCookie;
    private final LoginRateLimiter rateLimiter;
    public AuthController(AuthService auth, UserRepository users, org.springframework.core.env.Environment env, LoginRateLimiter rateLimiter) {
        this.auth = auth; this.users = users; this.rateLimiter = rateLimiter;
        this.secureCookie = env.getProperty("zhixing.cookie-secure", Boolean.class, !env.matchesProfiles("local", "test"));
    }

    @GetMapping("/csrf") public ApiResponse<String> csrf(CsrfToken token) { return ApiResponse.ok(token.getToken()); }

    @PostMapping("/login")
    public ApiResponse<AuthDtos.LoginResponse> login(@Valid @RequestBody AuthDtos.LoginRequest body, HttpServletRequest req, HttpServletResponse res) {
        rateLimiter.check(req.getRemoteAddr());
        AuthService.Issued issued = auth.login(body.employeeNo(), body.password(), req.getHeader("User-Agent"), req.getRemoteAddr());
        setRefreshCookie(res, issued.refreshToken(), issued.refreshExpiresAt());
        return ApiResponse.ok(issued.response());
    }

    @PostMapping("/refresh")
    public ApiResponse<AuthDtos.LoginResponse> refresh(@CookieValue(value = AuthService.REFRESH_COOKIE, required = false) String refresh,
                                                       HttpServletRequest req, HttpServletResponse res) {
        AuthService.Issued issued = auth.refresh(refresh, req.getHeader("User-Agent"), req.getRemoteAddr());
        setRefreshCookie(res, issued.refreshToken(), issued.refreshExpiresAt());
        return ApiResponse.ok(issued.response());
    }

    @PostMapping("/logout") public ApiResponse<Void> logout(@CookieValue(value = AuthService.REFRESH_COOKIE, required = false) String refresh, HttpServletResponse res) {
        auth.logout(refresh); clearRefreshCookie(res); return ApiResponse.ok();
    }
    @PostMapping("/logout-all") public ApiResponse<Void> logoutAll(@AuthenticationPrincipal Jwt jwt, HttpServletResponse res) {
        auth.logoutAll(Long.parseLong(jwt.getSubject())); clearRefreshCookie(res); return ApiResponse.ok();
    }
    @GetMapping("/me") public ApiResponse<AuthDtos.UserView> me(@AuthenticationPrincipal Jwt jwt) {
        UserRecord u = users.findById(Long.parseLong(jwt.getSubject())).orElseThrow(() -> new ApiException(org.springframework.http.HttpStatus.NOT_FOUND,"USER_NOT_FOUND","用户不存在"));
        return ApiResponse.ok(AuthDtos.UserView.from(u));
    }

    private void setRefreshCookie(HttpServletResponse response, String value, java.time.Instant expiresAt) {
        ResponseCookie cookie = ResponseCookie.from(AuthService.REFRESH_COOKIE, value).httpOnly(true).secure(secureCookie).sameSite("Strict").path("/api/v1/auth").maxAge(Duration.between(java.time.Instant.now(), expiresAt)).build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }
    private void clearRefreshCookie(HttpServletResponse response) {
        ResponseCookie cookie = ResponseCookie.from(AuthService.REFRESH_COOKIE, "").httpOnly(true).secure(secureCookie).sameSite("Strict").path("/api/v1/auth").maxAge(0).build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }
}
