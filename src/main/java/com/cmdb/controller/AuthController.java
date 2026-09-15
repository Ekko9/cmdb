package com.cmdb.controller;

import com.cmdb.config.TokenService;
import com.cmdb.entity.User;
import com.cmdb.repo.UserRepository;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController @RequestMapping("/api/auth")
public class AuthController {
    private final UserRepository users; private final TokenService tokens; private final BCryptPasswordEncoder encoder;
    public AuthController(UserRepository users, TokenService tokens, BCryptPasswordEncoder encoder) { this.users=users; this.tokens=tokens; this.encoder=encoder; }
    @PostMapping("/login") public Map<String,Object> login(@RequestBody Map<String,String> body) {
        String username = body == null ? null : body.get("username");
        String password = body == null ? null : body.get("password");
        User user = users.findByUsername(username == null ? "" : username.trim()).orElseThrow(() -> new RuntimeException("用户名或密码错误"));
        if (!Boolean.TRUE.equals(user.getEnabled()) || password == null || !encoder.matches(password, user.getPassword())) throw new RuntimeException("用户名或密码错误");
        Map<String,Object> result = new HashMap<>(); result.put("token", tokens.create(user.getUsername())); result.put("user", safe(user)); return result;
    }
    private Map<String,Object> safe(User user) { Map<String,Object> map = new LinkedHashMap<>(); map.put("id",user.getId()); map.put("username",user.getUsername()); map.put("displayName",user.getDisplayName()); map.put("role",user.getRole()); return map; }
}
