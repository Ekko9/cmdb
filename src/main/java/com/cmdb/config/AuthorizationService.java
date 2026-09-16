package com.cmdb.config;

import com.cmdb.entity.User;
import com.cmdb.repo.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Service;

import java.util.Locale;

@Service
public class AuthorizationService {
    private final UserRepository users;

    public AuthorizationService(UserRepository users) {
        this.users = users;
    }

    public String roleOf(String username) {
        if (username == null) return null;
        return users.findByUsername(username)
                .filter(user -> Boolean.TRUE.equals(user.getEnabled()))
                .map(User::getRole)
                .orElse(null);
    }

    public boolean isAllowed(HttpServletRequest request, String role) {
        String path = request.getRequestURI();
        String method = request.getMethod().toUpperCase(Locale.ROOT);
        if (path.startsWith("/api/users") || path.startsWith("/api/audits")) {
            return "ADMIN".equalsIgnoreCase(role);
        }
        if (path.startsWith("/api/account/password")) {
            return "PUT".equals(method);
        }
        if (path.startsWith("/api/assets/import") && "POST".equals(method)) {
            return canOperate(role);
        }
        if (path.startsWith("/api/assets/imports") && method.startsWith("GET")) {
            return "ADMIN".equalsIgnoreCase(role) || "OPERATOR".equalsIgnoreCase(role);
        }
        if (!isWrite(method)) {
            return true;
        }
        if (path.startsWith("/api/assets") || path.startsWith("/api/projects")) {
            return canOperate(role);
        }
        return false;
    }

    private boolean canOperate(String role) {
        return "ADMIN".equalsIgnoreCase(role) || "OPERATOR".equalsIgnoreCase(role);
    }

    private boolean isWrite(String method) {
        return "POST".equals(method) || "PUT".equals(method)
                || "PATCH".equals(method) || "DELETE".equals(method);
    }
}
