package com.cmdb.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class ActuatorAuthFilter extends OncePerRequestFilter {
    private final TokenService tokenService;
    private final AuthorizationService authorizationService;

    public ActuatorAuthFilter(TokenService tokenService, AuthorizationService authorizationService) {
        this.tokenService = tokenService;
        this.authorizationService = authorizationService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String uri = request.getRequestURI();
        if (!uri.startsWith("/actuator/") || isPublicHealth(uri)) {
            filterChain.doFilter(request, response);
            return;
        }

        String header = request.getHeader("Authorization");
        String username = header != null && header.startsWith("Bearer ") ? tokenService.username(header.substring(7)) : null;
        String role = authorizationService.roleOf(username);
        if (!"ADMIN".equalsIgnoreCase(role)) {
            response.setStatus(username == null ? 401 : 403);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write(username == null
                    ? "{\"message\":\"登录已失效\"}"
                    : "{\"message\":\"当前角色没有执行此操作的权限\"}");
            return;
        }
        filterChain.doFilter(request, response);
    }

    private boolean isPublicHealth(String uri) {
        return uri.startsWith("/actuator/health/liveness") || uri.startsWith("/actuator/health/readiness");
    }
}
