package com.cmdb.controller;

import com.cmdb.config.TokenService;
import com.cmdb.config.AuditService;
import com.cmdb.config.BadRequestException;
import com.cmdb.config.PasswordPolicy;
import com.cmdb.entity.User;
import com.cmdb.repo.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDateTime;
import java.util.*;

@RestController @RequestMapping("/api/auth")
public class AuthController {
    private static final String PASSWORD_PATTERN = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[^A-Za-z0-9]).{8,}$";
    private static final String USERNAME_PATTERN = "^[A-Za-z0-9._-]{3,50}$";

    private final UserRepository users;
    private final TokenService tokens;
    private final BCryptPasswordEncoder encoder;
    private final AuditService auditService;

    public AuthController(UserRepository users, TokenService tokens, BCryptPasswordEncoder encoder, AuditService auditService) {
        this.users = users;
        this.tokens = tokens;
        this.encoder = encoder;
        this.auditService = auditService;
    }

    @PostMapping("/register")
    public Map<String, Object> register(HttpServletRequest request, @RequestBody Map<String, String> body) {
        String username = body == null ? null : trimToNull(body.get("username"));
        String password = body == null ? null : body.get("password");
        String displayName = body == null ? null : trimToNull(body.get("displayName"));
        if (username == null || !username.matches(USERNAME_PATTERN)) {
            throw new BadRequestException("用户名需为 3 至 50 位字母、数字、点、下划线或连字符");
        }
        if (password == null || !password.matches(PASSWORD_PATTERN)) {
            throw new BadRequestException("密码至少 8 位，且必须包含大写字母、小写字母、数字和特殊符号");
        }
        if (displayName != null && displayName.length() > 100) {
            throw new BadRequestException("显示名称不能超过 100 个字符");
        }
        if (users.findByUsername(username).isPresent()) {
            throw new BadRequestException("用户名已存在");
        }

        User user = new User();
        user.setUsername(username);
        user.setPassword(encoder.encode(password));
        user.setDisplayName(displayName == null ? username : displayName);
        user.setRole("VIEWER");
        user.setEnabled(false);
        user.setPasswordChangedAt(LocalDateTime.now());
        User saved = users.save(user);
        auditService.operation(request, "REGISTER", "USER", saved.getId(), saved.getUsername(), "PENDING", "新注册账号等待管理员启用");

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("message", "注册申请已提交，请联系管理员审核启用");
        result.put("username", saved.getUsername());
        result.put("enabled", false);
        return result;
    }

    @PostMapping("/login") public Map<String,Object> login(@RequestBody Map<String,String> body) {
        String username = body == null ? null : body.get("username");
        String password = body == null ? null : body.get("password");
        User user = users.findByUsername(username == null ? "" : username.trim())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "用户名或密码错误"));
        if (!Boolean.TRUE.equals(user.getEnabled()) || password == null || !encoder.matches(password, user.getPassword())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "用户名或密码错误");
        }
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime expiresAt = PasswordPolicy.expiresAt(user.getPasswordChangedAt());
        int daysRemaining = PasswordPolicy.daysRemaining(user.getPasswordChangedAt(), now);
        Map<String,Object> result = new HashMap<>();
        result.put("token", tokens.create(user.getUsername()));
        Map<String,Object> userInfo = safe(user);
        userInfo.put("passwordExpiresAt", expiresAt);
        userInfo.put("passwordDaysRemaining", daysRemaining);
        userInfo.put("passwordExpired", PasswordPolicy.isExpired(user.getPasswordChangedAt(), now));
        userInfo.put("passwordChangeRecommended", daysRemaining <= PasswordPolicy.WARNING_DAYS);
        result.put("user", userInfo);
        return result;
    }

    private Map<String,Object> safe(User user) { Map<String,Object> map = new LinkedHashMap<>(); map.put("id",user.getId()); map.put("username",user.getUsername()); map.put("displayName",user.getDisplayName()); map.put("role",user.getRole()); return map; }

    private String trimToNull(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
