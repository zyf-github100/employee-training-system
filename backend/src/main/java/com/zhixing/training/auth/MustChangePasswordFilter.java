package com.zhixing.training.auth;

import com.zhixing.training.api.SecurityErrors;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/** Prevents a newly provisioned account from using business APIs before changing its password. */
public final class MustChangePasswordFilter extends OncePerRequestFilter {
    private final UserRepository users;
    private final SecurityErrors errors;

    public MustChangePasswordFilter(UserRepository users, SecurityErrors errors) {
        this.users = users;
        this.errors = errors;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication instanceof JwtAuthenticationToken token && token.getToken().getSubject() != null) {
            long userId;
            try { userId = Long.parseLong(token.getToken().getSubject()); }
            catch (NumberFormatException ignored) { chain.doFilter(request, response); return; }
            boolean mustChange = users.findById(userId).map(UserRecord::mustChangePassword).orElse(false);
            String path = request.getRequestURI();
            boolean allowed = path.equals("/api/v1/me") || path.equals("/api/v1/me/password")
                    || path.equals("/api/v1/auth/logout") || path.equals("/api/v1/auth/logout-all")
                    || path.equals("/api/v1/auth/csrf");
            if (mustChange && !allowed) {
                errors.write(response, 403, "PASSWORD_CHANGE_REQUIRED", "首次登录请先修改密码");
                return;
            }
        }
        chain.doFilter(request, response);
    }
}
