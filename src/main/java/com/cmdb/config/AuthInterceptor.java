package com.cmdb.config;

import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class AuthInterceptor implements HandlerInterceptor {
    private final TokenService tokenService;
    private final AuthorizationService authorizationService;

    public AuthInterceptor(TokenService tokenService, AuthorizationService authorizationService) {
        this.tokenService = tokenService;
        this.authorizationService = authorizationService;
    }

    @Override public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String uri = request.getRequestURI();
        if (HttpMethod.OPTIONS.matches(request.getMethod()) || uri.startsWith("/api/auth") || !uri.startsWith("/api/")) return true;
        String header = request.getHeader("Authorization");
        String username = header != null && header.startsWith("Bearer ") ? tokenService.username(header.substring(7)) : null;
        if (username == null) { unauthorized(response, "登录已失效"); return false; }
        String role = authorizationService.roleOf(username);
        if (role == null) {
            unauthorized(response, "用户已停用或不存在");
            return false;
        }
        if (uri.startsWith("/actuator/")) {
            if (!"ADMIN".equalsIgnoreCase(role)) {
                forbidden(response);
                return false;
            }
        } else if (!authorizationService.isAllowed(request, role)) {
            forbidden(response);
            return false;
        }
        request.setAttribute("username", username);
        request.setAttribute("role", role);
        return true;
    }

    private void unauthorized(HttpServletResponse response, String message) throws Exception {
        response.setStatus(401);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"message\":\"" + message + "\"}");
    }

    private void forbidden(HttpServletResponse response) throws Exception {
        response.setStatus(403);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"message\":\"当前角色没有执行此操作的权限\"}");
    }
}
