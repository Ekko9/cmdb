package com.cmdb.controller;

import com.cmdb.config.AuditService;
import com.cmdb.config.BadRequestException;
import com.cmdb.config.TokenService;
import com.cmdb.entity.User;
import com.cmdb.repo.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AuthControllerTest {
    @Test
    void registrationCreatesDisabledViewerWithEncodedPassword() {
        UserRepository users = mock(UserRepository.class);
        when(users.findByUsername("new.user")).thenReturn(Optional.empty());
        when(users.save(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            user.setId(42L);
            return user;
        });
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        AuditService audit = mock(AuditService.class);
        AuthController controller = new AuthController(users, mock(TokenService.class), encoder, audit);
        HttpServletRequest request = new MockHttpServletRequest();

        Map<String, Object> response = controller.register(request, Map.of(
                "username", "new.user", "displayName", "New User", "password", "StrongPass1!"));

        verify(users).save(argThat(user -> "VIEWER".equals(user.getRole())
                && Boolean.FALSE.equals(user.getEnabled())
                && encoder.matches("StrongPass1!", user.getPassword())
                && user.getPasswordChangedAt() != null));
        assertEquals(false, response.get("enabled"));
        assertTrue(String.valueOf(response.get("message")).contains("管理员"));
        verify(audit).operation(request, "REGISTER", "USER", 42L, "new.user", "PENDING", "新注册账号等待管理员启用");
    }

    @Test
    void registrationRejectsWeakPasswordBeforeSaving() {
        UserRepository users = mock(UserRepository.class);
        AuthController controller = new AuthController(users, mock(TokenService.class),
                new BCryptPasswordEncoder(), mock(AuditService.class));

        assertThrows(BadRequestException.class, () -> controller.register(new MockHttpServletRequest(),
                Map.of("username", "new.user", "password", "weak")));
        verify(users, never()).save(any(User.class));
    }
}
