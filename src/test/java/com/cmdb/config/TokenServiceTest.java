package com.cmdb.config;

import com.cmdb.entity.User;
import com.cmdb.repo.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TokenServiceTest {
    @Test
    void passwordChangeInvalidatesPreviouslyIssuedToken() {
        UserRepository users = mock(UserRepository.class);
        User user = new User();
        user.setUsername("operator.one");
        user.setPasswordChangedAt(LocalDateTime.of(2026, 11, 6, 10, 0));
        when(users.findByUsername("operator.one")).thenReturn(Optional.of(user));
        TokenService service = new TokenService(users);
        ReflectionTestUtils.setField(service, "secret", "0123456789abcdef0123456789abcdef");
        ReflectionTestUtils.setField(service, "expireHours", 12L);
        service.validateSecret();

        String token = service.create("operator.one");
        assertEquals("operator.one", service.username(token));

        user.setPasswordChangedAt(user.getPasswordChangedAt().plusSeconds(1));
        assertNull(service.username(token));
    }
}
