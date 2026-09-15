package com.cmdb.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

@Service
public class TokenService {
    @Value("${cmdb.jwt-secret}") private String secret;
    @Value("${cmdb.jwt-expire-hours:12}") private long expireHours;
    public String create(String username) {
        long expires = System.currentTimeMillis() + expireHours * 3600000L;
        String payload = username + ":" + expires;
        return Base64.getUrlEncoder().withoutPadding().encodeToString(payload.getBytes(StandardCharsets.UTF_8)) + "." + sign(payload);
    }
    public String username(String token) {
        try {
            String[] parts = token.split("\\.");
            if (parts.length != 2 || !sign(new String(Base64.getUrlDecoder().decode(parts[0]), StandardCharsets.UTF_8)).equals(parts[1])) return null;
            String[] payload = new String(Base64.getUrlDecoder().decode(parts[0]), StandardCharsets.UTF_8).split(":", 2);
            return Long.parseLong(payload[1]) > System.currentTimeMillis() ? payload[0] : null;
        } catch (Exception e) { return null; }
    }
    private String sign(String value) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) { throw new IllegalStateException(e); }
    }
}
