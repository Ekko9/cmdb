package com.cmdb.controller;

import com.cmdb.entity.User;
import com.cmdb.repo.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/account")
public class AccountController {
    private static final String PASSWORD_PATTERN = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[^A-Za-z0-9]).{8,}$";

    private final UserRepository users;
    private final BCryptPasswordEncoder encoder;

    public AccountController(UserRepository users, BCryptPasswordEncoder encoder) {
        this.users = users;
        this.encoder = encoder;
    }

    @PutMapping("/password")
    public void changePassword(HttpServletRequest request, @RequestBody Map<String, String> body) {
        String username = (String) request.getAttribute("username");
        User user = users.findByUsername(username == null ? "" : username)
                .orElseThrow(() -> new RuntimeException("用户不存在"));
        String currentPassword = body == null ? null : body.get("currentPassword");
        String newPassword = body == null ? null : body.get("newPassword");
        String confirmPassword = body == null ? null : body.get("confirmPassword");
        if (currentPassword == null || !encoder.matches(currentPassword, user.getPassword())) {
            throw new RuntimeException("当前密码不正确");
        }
        if (newPassword == null || !newPassword.matches(PASSWORD_PATTERN)) {
            throw new RuntimeException("密码至少 8 位，且必须包含大写字母、小写字母、数字和特殊符号");
        }
        if (!newPassword.equals(confirmPassword)) {
            throw new RuntimeException("两次输入的新密码不一致");
        }
        if (encoder.matches(newPassword, user.getPassword())) {
            throw new RuntimeException("新密码不能与当前密码相同");
        }
        user.setPassword(encoder.encode(newPassword));
        users.save(user);
    }
}
