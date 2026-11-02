package com.cmdb.controller;

import com.cmdb.config.AuditService;
import com.cmdb.config.BadRequestException;
import com.cmdb.entity.User;
import com.cmdb.repo.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class UserControllerTest {
    @Test
    void preventsDisablingTheLastEnabledAdmin() {
        User admin = user(1L, "admin", "ADMIN", true);
        UserRepository users = mock(UserRepository.class);
        when(users.lockEnabledAdmins()).thenReturn(List.of(admin));
        when(users.findById(1L)).thenReturn(Optional.of(admin));
        UserController controller = controller(users);

        assertThrows(BadRequestException.class, () -> controller.update(
                request(), 1L, Map.of("enabled", "false")));
        verify(users, never()).save(any(User.class));
    }

    @Test
    void allowsDemotingAnAdminWhenAnotherEnabledAdminRemains() {
        User admin = user(1L, "admin", "ADMIN", true);
        User otherAdmin = user(2L, "backup-admin", "ADMIN", true);
        UserRepository users = mock(UserRepository.class);
        when(users.lockEnabledAdmins()).thenReturn(List.of(admin, otherAdmin));
        when(users.findById(1L)).thenReturn(Optional.of(admin));
        when(users.save(admin)).thenReturn(admin);
        UserController controller = controller(users);

        controller.update(request(), 1L, Map.of("role", "OPERATOR"));

        assertEquals("OPERATOR", admin.getRole());
        verify(users).save(admin);
    }

    @Test
    void rejectsMalformedEnabledFlagInsteadOfSilentlyDisablingUser() {
        User admin = user(1L, "admin", "ADMIN", true);
        UserRepository users = mock(UserRepository.class);
        when(users.lockEnabledAdmins()).thenReturn(List.of(admin));
        when(users.findById(1L)).thenReturn(Optional.of(admin));
        UserController controller = controller(users);

        assertThrows(BadRequestException.class, () -> controller.update(
                request(), 1L, Map.of("enabled", "yes")));
        verify(users, never()).save(any(User.class));
    }

    @Test
    void preventsDeletingTheLastEnabledAdmin() {
        User lastAdmin = user(2L, "backup-admin", "ADMIN", true);
        UserRepository users = mock(UserRepository.class);
        when(users.lockEnabledAdmins()).thenReturn(List.of(lastAdmin));
        when(users.findById(2L)).thenReturn(Optional.of(lastAdmin));
        UserController controller = controller(users);

        assertThrows(BadRequestException.class, () -> controller.delete(request(), 2L));
        verify(users, never()).delete(any(User.class));
    }

    private UserController controller(UserRepository users) {
        return new UserController(users, new BCryptPasswordEncoder(), mock(AuditService.class));
    }

    private HttpServletRequest request() {
        return mock(HttpServletRequest.class);
    }

    private User user(Long id, String username, String role, boolean enabled) {
        User user = new User();
        user.setId(id);
        user.setUsername(username);
        user.setRole(role);
        user.setEnabled(enabled);
        user.setDisplayName(username);
        user.setPassword("encoded");
        return user;
    }
}
