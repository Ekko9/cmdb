package com.cmdb.controller;

import com.cmdb.config.AuditService;
import com.cmdb.config.BadRequestException;
import com.cmdb.config.ResourceNotFoundException;
import com.cmdb.config.PasswordPolicy;
import com.cmdb.entity.User;
import com.cmdb.repo.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/users")
public class UserController {
    private static final Set<String> ROLES = new HashSet<>(Arrays.asList("ADMIN", "OPERATOR", "VIEWER"));
    private static final String USERNAME_PATTERN = "^[A-Za-z0-9._-]{3,50}$";

    private final UserRepository users;
    private final BCryptPasswordEncoder encoder;
    private final AuditService auditService;

    public UserController(UserRepository users, BCryptPasswordEncoder encoder, AuditService auditService) {
        this.users = users;
        this.encoder = encoder;
        this.auditService = auditService;
    }

    @GetMapping
    public List<Map<String, Object>> list() {
        List<Map<String, Object>> result = new ArrayList<>();
        for (User user : users.findAll()) {
            result.add(safe(user));
        }
        return result;
    }

    @PostMapping
    public Map<String, Object> create(HttpServletRequest request, @RequestBody Map<String, String> body) {
        String username = trimToNull(body.get("username"));
        if (username == null || !username.matches(USERNAME_PATTERN)) {
            throw new BadRequestException("用户名需为 3 至 50 位字母、数字、点、下划线或连字符");
        }
        if (users.findByUsername(username).isPresent()) throw new BadRequestException("用户名已存在");

        User user = new User();
        user.setUsername(username);
        user.setPassword(encoder.encode(requirePassword(body.get("password"))));
        user.setPasswordChangedAt(LocalDateTime.now());
        user.setDisplayName(displayName(body.get("displayName"), username));
        user.setRole(normalizeRole(body.get("role")));
        user.setEnabled(true);
        User saved = users.save(user);
        auditService.operation(request, "CREATE", "USER", saved.getId(), saved.getUsername(), "SUCCESS", "新增用户");
        return safe(saved);
    }

    @PutMapping("/{id}")
    @Transactional
    public Map<String, Object> update(HttpServletRequest request, @PathVariable Long id, @RequestBody Map<String, String> body) {
        List<User> enabledAdmins = users.lockEnabledAdmins();
        User user = users.findById(id).orElseThrow(() -> new ResourceNotFoundException("用户不存在"));
        String role = body.containsKey("role") ? normalizeRole(body.get("role")) : user.getRole();
        boolean enabled = body.containsKey("enabled") ? parseEnabled(body.get("enabled")) : Boolean.TRUE.equals(user.getEnabled());
        if (Boolean.TRUE.equals(user.getEnabled()) && "ADMIN".equalsIgnoreCase(user.getRole())
                && (!enabled || !"ADMIN".equalsIgnoreCase(role)) && enabledAdmins.size() <= 1) {
            throw new BadRequestException("不能停用或降权最后一位启用的管理员");
        }
        if (body.containsKey("displayName")) user.setDisplayName(displayName(body.get("displayName"), user.getUsername()));
        user.setRole(role);
        user.setEnabled(enabled);
        if (body.containsKey("password") && body.get("password") != null && !body.get("password").trim().isEmpty()) {
            user.setPassword(encoder.encode(requirePassword(body.get("password"))));
            user.setPasswordChangedAt(LocalDateTime.now());
        }
        User saved = users.save(user);
        auditService.operation(request, "UPDATE", "USER", saved.getId(), saved.getUsername(), "SUCCESS", "编辑用户");
        return safe(saved);
    }

    @DeleteMapping("/{id}")
    @Transactional
    public void delete(HttpServletRequest request, @PathVariable Long id) {
        List<User> enabledAdmins = users.lockEnabledAdmins();
        User user = users.findById(id).orElseThrow(() -> new ResourceNotFoundException("用户不存在"));
        if ("admin".equalsIgnoreCase(user.getUsername())) throw new BadRequestException("admin 账号不允许删除");
        if (Boolean.TRUE.equals(user.getEnabled()) && "ADMIN".equalsIgnoreCase(user.getRole())
                && enabledAdmins.size() <= 1) {
            throw new BadRequestException("不能删除最后一位启用的管理员");
        }
        users.delete(user);
        auditService.operation(request, "DELETE", "USER", id, user.getUsername(), "SUCCESS", "删除用户");
    }

    private String requirePassword(String password) {
        if (!PasswordPolicy.isValid(password)) {
            throw new BadRequestException("密码需为 8 至 72 位，且必须包含大写字母、小写字母、数字和特殊符号");
        }
        return password;
    }

    private String normalizeRole(String role) {
        String value = defaultText(role, "OPERATOR").toUpperCase(Locale.ROOT);
        if (!ROLES.contains(value)) throw new BadRequestException("角色必须是 ADMIN、OPERATOR 或 VIEWER");
        return value;
    }

    private String defaultText(String value, String fallback) {
        String trimmed = trimToNull(value);
        return trimmed == null ? fallback : trimmed;
    }

    private String displayName(String value, String fallback) {
        String result = defaultText(value, fallback);
        if (result.length() > 100) throw new BadRequestException("显示名称不能超过 100 个字符");
        return result;
    }

    private boolean parseEnabled(String value) {
        if ("true".equalsIgnoreCase(value)) return true;
        if ("false".equalsIgnoreCase(value)) return false;
        throw new BadRequestException("用户启用状态必须为 true 或 false");
    }

    private String trimToNull(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private Map<String, Object> safe(User user) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", user.getId());
        result.put("username", user.getUsername());
        result.put("displayName", user.getDisplayName());
        result.put("role", user.getRole());
        result.put("enabled", user.getEnabled());
        result.put("createdAt", user.getCreatedAt());
        return result;
    }
}
