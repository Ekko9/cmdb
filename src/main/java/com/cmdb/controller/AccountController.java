package com.cmdb.controller;

import com.cmdb.config.AuditService;
import com.cmdb.config.BadRequestException;
import com.cmdb.config.PasswordPolicy;
import com.cmdb.entity.User;
import com.cmdb.repo.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/account")
public class AccountController {
    private final UserRepository users;
    private final BCryptPasswordEncoder encoder;
    private final AuditService auditService;

    public AccountController(UserRepository users, BCryptPasswordEncoder encoder, AuditService auditService) {
        this.users = users;
        this.encoder = encoder;
        this.auditService = auditService;
    }

    @PutMapping("/password")
    @Transactional
    public Map<String, Object> changePassword(HttpServletRequest request, @RequestBody Map<String, String> body) {
        String username = (String) request.getAttribute("username");
        User user = users.findByUsername(username == null ? "" : username)
                .orElseThrow(() -> new com.cmdb.config.ResourceNotFoundException("用户不存在"));
        String currentPassword = body == null ? null : body.get("currentPassword");
        String newPassword = body == null ? null : body.get("newPassword");
        String confirmPassword = body == null ? null : body.get("confirmPassword");
        if (currentPassword == null || !encoder.matches(currentPassword, user.getPassword())) {
            throw new BadRequestException("当前密码不正确");
        }
        if (!PasswordPolicy.isValid(newPassword)) {
            throw new BadRequestException("密码需为 8 至 72 位，且必须包含大写字母、小写字母、数字和特殊符号");
        }
        if (!newPassword.equals(confirmPassword)) {
            throw new BadRequestException("两次输入的新密码不一致");
        }
        if (encoder.matches(newPassword, user.getPassword())) {
            throw new BadRequestException("新密码不能与当前密码相同");
        }
        user.setPassword(encoder.encode(newPassword));
        user.setPasswordChangedAt(LocalDateTime.now());
        users.save(user);
        auditService.operation(request, "CHANGE_PASSWORD", "ACCOUNT", user.getId(), user.getUsername(), "SUCCESS", "用户自助修改密码");
        Map<String, Object> result = new java.util.LinkedHashMap<>();
        result.put("passwordExpiresAt", com.cmdb.config.PasswordPolicy.expiresAt(user.getPasswordChangedAt()));
        result.put("passwordDaysRemaining", com.cmdb.config.PasswordPolicy.daysRemaining(user.getPasswordChangedAt(), LocalDateTime.now()));
        result.put("passwordExpired", false);
        result.put("passwordChangeRecommended", false);
        return result;
    }
}
