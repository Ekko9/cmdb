package com.cmdb.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.security.MessageDigest;
import jakarta.annotation.PostConstruct;
import com.cmdb.entity.User;
import com.cmdb.repo.UserRepository;

@Service
public class TokenService {
    private final UserRepository users;
    @Value("${cmdb.jwt-secret}") private String secret;
    @Value("${cmdb.jwt-expire-hours:12}") private long expireHours;

    public TokenService(UserRepository users) {
        this.users = users;
    }
    @PostConstruct
    void validateSecret() {
        if (secret == null || secret.length() < 32 || "change-me-before-startup".equals(secret)) {
            throw new IllegalStateException("JWT_SECRET 必须设置为至少 32 个字符的随机字符串");
        }
        if (expireHours < 1 || expireHours > 720) {
            throw new IllegalStateException("JWT_EXPIRE_HOURS 必须在 1 至 720 小时之间");
        }
    }
    public String create(String username) {
        User user = users.findByUsername(username)
                .orElseThrow(() -> new IllegalStateException("用户不存在，无法签发令牌"));
        long expires = System.currentTimeMillis() + expireHours * 3600000L;
        String payload = encode(username) + ":" + expires + ":" + passwordVersion(user);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(payload.getBytes(StandardCharsets.UTF_8)) + "." + sign(payload);
    }
    public String username(String token) {
        try {
            String[] parts = token.split("\\.");
            if (parts.length != 2) return null;
            String expected = sign(new String(Base64.getUrlDecoder().decode(parts[0]), StandardCharsets.UTF_8));
            if (!MessageDigest.isEqual(expected.getBytes(StandardCharsets.US_ASCII), parts[1].getBytes(StandardCharsets.US_ASCII))) return null;
            String[] payload = new String(Base64.getUrlDecoder().decode(parts[0]), StandardCharsets.UTF_8).split(":", 3);
            if (payload.length != 3 || Long.parseLong(payload[1]) <= System.currentTimeMillis()) return null;
            String username = new String(Base64.getUrlDecoder().decode(payload[0]), StandardCharsets.UTF_8);
            User user = users.findByUsername(username).orElse(null);
            if (user == null || !MessageDigest.isEqual(passwordVersion(user).getBytes(StandardCharsets.US_ASCII),
                    payload[2].getBytes(StandardCharsets.US_ASCII))) return null;
            return username;
        } catch (Exception e) { return null; }
    }

    private String passwordVersion(User user) {
        if (user.getPasswordChangedAt() == null) return "missing";
        return encode(user.getPasswordChangedAt().toString());
    }

    private String encode(String value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }
    private String sign(String value) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) { throw new IllegalStateException(e); }
    }
}
