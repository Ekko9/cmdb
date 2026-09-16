package com.cmdb.controller;

import com.cmdb.entity.User;
import com.cmdb.repo.UserRepository;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/users")
public class UserController {
    private static final Set<String> ROLES = new HashSet<>(Arrays.asList("ADMIN", "OPERATOR", "VIEWER"));
    private static final String PASSWORD_PATTERN = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[^A-Za-z0-9]).{8,}$";

    private final UserRepository users;
    private final BCryptPasswordEncoder encoder;

    public UserController(UserRepository users, BCryptPasswordEncoder encoder) {
        this.users = users;
        this.encoder = encoder;
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
    public Map<String, Object> create(@RequestBody Map<String, String> body) {
        String username = trimToNull(body.get("username"));
        if (username == null) throw new RuntimeException("用户名不能为空");
        if (users.findByUsername(username).isPresent()) throw new RuntimeException("用户名已存在");

        User user = new User();
        user.setUsername(username);
        user.setPassword(encoder.encode(requirePassword(body.get("password"))));
        user.setDisplayName(defaultText(body.get("displayName"), username));
        user.setRole(normalizeRole(body.get("role")));
        user.setEnabled(true);
        return safe(users.save(user));
    }

    @PutMapping("/{id}")
    public Map<String, Object> update(@PathVariable Long id, @RequestBody Map<String, String> body) {
        User user = users.findById(id).orElseThrow(() -> new RuntimeException("用户不存在"));
        if (body.containsKey("displayName")) user.setDisplayName(defaultText(body.get("displayName"), user.getUsername()));
        if (body.containsKey("role")) user.setRole(normalizeRole(body.get("role")));
        if (body.containsKey("enabled")) user.setEnabled(Boolean.valueOf(body.get("enabled")));
        if (body.containsKey("password") && body.get("password") != null && !body.get("password").trim().isEmpty()) {
            user.setPassword(encoder.encode(requirePassword(body.get("password"))));
        }
        return safe(users.save(user));
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        User user = users.findById(id).orElseThrow(() -> new RuntimeException("用户不存在"));
        if ("admin".equalsIgnoreCase(user.getUsername())) throw new RuntimeException("admin 账号不允许删除");
        users.delete(user);
    }

    private String requirePassword(String password) {
        if (password == null || !password.matches(PASSWORD_PATTERN)) {
            throw new RuntimeException("密码至少 8 位，且必须包含大写字母、小写字母、数字和特殊符号");
        }
        return password;
    }

    private String normalizeRole(String role) {
        String value = defaultText(role, "OPERATOR").toUpperCase(Locale.ROOT);
        if (!ROLES.contains(value)) throw new RuntimeException("角色必须是 ADMIN、OPERATOR 或 VIEWER");
        return value;
    }

    private String defaultText(String value, String fallback) {
        String trimmed = trimToNull(value);
        return trimmed == null ? fallback : trimmed;
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
