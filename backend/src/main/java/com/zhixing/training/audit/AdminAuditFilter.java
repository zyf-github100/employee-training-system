package com.zhixing.training.audit;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import java.io.IOException;
import java.util.Set;

// Registered only inside the security chain, before CSRF and authorization.
// No request body, query string, password, cookie or bearer token is stored.
public class AdminAuditFilter extends OncePerRequestFilter {
    private final AuditService audit;
    public AdminAuditFilter(AuditService audit){this.audit=audit;}
    @Override protected boolean shouldNotFilter(HttpServletRequest request) {
        String path=request.getRequestURI();
        if(path.equals("/api/v1/auth/account-setup"))return !request.getMethod().equals("POST");
        if(path.startsWith("/api/v1/admin/employee-imports/")&&path.endsWith("/report"))return false;
        if(path.startsWith("/api/v1/admin/certificates/")&&path.endsWith("/pdf"))return false;
        return !path.startsWith("/api/v1/admin/") || Set.of("GET","HEAD","OPTIONS").contains(request.getMethod());
    }
    @Override protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain)throws ServletException,IOException {
        int failure=0;
        try { chain.doFilter(request,response); }
        catch(IOException|ServletException|RuntimeException e) {failure=500;throw e;}
        finally {
            var auth=SecurityContextHolder.getContext().getAuthentication();Long actor=null;
            if(auth!=null && auth.getPrincipal() instanceof Jwt jwt) {
                try {actor=Long.parseLong(jwt.getSubject());}catch(NumberFormatException ignored){}
            }
            if(request.getAttribute("audit.actorId") instanceof Long id)actor=id;
            try {audit.record(actor,request.getMethod(),request.getRequestURI(),failure==0?response.getStatus():failure);}
            catch(RuntimeException e){logger.error("Unable to persist administrator request audit",e);}
        }
    }
}
