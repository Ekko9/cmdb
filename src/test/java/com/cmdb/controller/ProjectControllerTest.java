package com.cmdb.controller;

import com.cmdb.config.AuditService;
import com.cmdb.config.BadRequestException;
import com.cmdb.entity.Project;
import com.cmdb.repo.AssetRepository;
import com.cmdb.repo.ProjectRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ProjectControllerTest {
    @Test
    void mapsDatabaseUniqueCodeConflictToClientError() {
        ProjectRepository projects = mock(ProjectRepository.class);
        when(projects.findByCode("OPS")).thenReturn(Optional.empty());
        when(projects.saveAndFlush(any(Project.class))).thenThrow(new DataIntegrityViolationException("duplicate"));
        ProjectController controller = new ProjectController(projects, mock(AssetRepository.class), mock(AuditService.class));
        Project project = new Project();
        project.setName("Operations");
        project.setCode("OPS");

        assertThrows(BadRequestException.class, () -> controller.create(mock(HttpServletRequest.class), project));
    }

    @Test
    void rejectsFieldsLongerThanDatabaseColumns() {
        ProjectController controller = new ProjectController(mock(ProjectRepository.class),
                mock(AssetRepository.class), mock(AuditService.class));
        Project project = new Project();
        project.setName("x".repeat(101));

        assertThrows(BadRequestException.class, () -> controller.create(mock(HttpServletRequest.class), project));
    }
}
