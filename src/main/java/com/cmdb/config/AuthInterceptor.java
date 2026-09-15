package com.cmdb.config;

import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@Component
public class AuthInterceptor implements HandlerInterceptor {
    private final TokenService tokenService;
    public AuthInterceptor(TokenService tokenService) { this.tokenService = tokenService; }
    @Override public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if (HttpMethod.OPTIONS.matches(request.getMethod()) || request.getRequestURI().startsWith("/api/auth") || !request.getRequestURI().startsWith("/api/")) return true;
        String header = request.getHeader("Authorization");
        String username = header != null && header.startsWith("Bearer ") ? tokenService.username(header.substring(7)) : null;
        if (username == null) { response.setStatus(401); response.setContentType("application/json;charset=UTF-8"); response.getWriter().write("{\"message\":\"登录已失效\"}"); return false; }
        request.setAttribute("username", username); return true;
    }
}
