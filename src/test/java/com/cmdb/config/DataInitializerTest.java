package com.cmdb.config;

import com.cmdb.entity.User;
import com.cmdb.repo.ProjectRepository;
import com.cmdb.repo.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class DataInitializerTest {
    @Test
    void toleratesAnotherInstanceCreatingAdminFirst() throws Exception {
        UserRepository users = mock(UserRepository.class);
        ProjectRepository projects = mock(ProjectRepository.class);
        User existing = new User();
        existing.setUsername("admin");
        when(users.findByUsername("admin")).thenReturn(Optional.empty(), Optional.of(existing));
        when(users.saveAndFlush(any(User.class))).thenThrow(new DataIntegrityViolationException("duplicate"));
        when(projects.count()).thenReturn(1L);
        DataInitializer initializer = new DataInitializer();
        ReflectionTestUtils.setField(initializer, "initialAdminPassword", "StrongPass1!");

        assertDoesNotThrow(() -> initializer.init(users, projects, new BCryptPasswordEncoder()).run());
    }
}
