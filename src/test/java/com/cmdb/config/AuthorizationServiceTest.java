package com.cmdb.config;

import com.cmdb.entity.User;
import com.cmdb.repo.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class AuthorizationServiceTest {
    @Test
    void disabledUserHasNoRole() {
        UserRepository repository = Mockito.mock(UserRepository.class);
        User user = new User();
        user.setUsername("viewer");
        user.setRole("VIEWER");
        user.setEnabled(false);
        Mockito.when(repository.findByUsername("viewer")).thenReturn(Optional.of(user));

        assertNull(new AuthorizationService(repository).roleOf("viewer"));
    }

    @Test
    void viewerCannotWriteAssets() {
        UserRepository repository = Mockito.mock(UserRepository.class);
        HttpServletRequest request = Mockito.mock(HttpServletRequest.class);
        Mockito.when(request.getRequestURI()).thenReturn("/api/assets");
        Mockito.when(request.getMethod()).thenReturn("POST");

        assertFalse(new AuthorizationService(repository).isAllowed(request, "VIEWER"));
    }
}
