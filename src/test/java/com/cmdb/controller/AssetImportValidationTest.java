package com.cmdb.controller;

import com.cmdb.config.AuditService;
import com.cmdb.entity.Project;
import com.cmdb.repo.*;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AssetImportValidationTest {
    @Test
    void mergePreviewDetectsCrossRowIdentityConflict() throws Exception {
        AssetRepository assets = mock(AssetRepository.class);
        ProjectRepository projects = mock(ProjectRepository.class);
        Project project = new Project();
        project.setId(1L);
        project.setName("默认项目");
        when(projects.findByNameIgnoreCase("默认项目")).thenReturn(List.of(project));
        AssetController controller = new AssetController(assets, projects, mock(AssetTypeOptionRepository.class),
                mock(AssetChangeRepository.class), mock(ImportAuditRepository.class), mock(ImportFailureRepository.class),
                mock(RegionOptionRepository.class), mock(AuditService.class));
        String csv = "名称,类型,环境,内网IP,外网IP,主机名,项目名称,状态,区域,描述\n"
                + "asset-a,SERVER,PRODUCTION,10.0.0.1,,,默认项目,ONLINE,,\n"
                + "asset-b,SERVER,PRODUCTION,10.0.0.2,,,默认项目,ONLINE,,\n"
                + "asset-a,SERVER,PRODUCTION,10.0.0.2,,,默认项目,ONLINE,,\n";
        MockMultipartFile file = new MockMultipartFile("file", "assets.csv", "text/csv",
                csv.getBytes(StandardCharsets.UTF_8));

        Map<String, Object> result = controller.validateImport(file, "MERGE");

        assertEquals(3, result.get("totalRows"));
        assertEquals(2, result.get("validRows"));
        assertEquals(1, result.get("failedRows"));
    }

    @Test
    void normalAssetCreationRejectsTheSameDuplicatesAsImport() {
        AssetRepository assets = mock(AssetRepository.class);
        ProjectRepository projects = mock(ProjectRepository.class);
        Project project = new Project();
        project.setId(1L);
        project.setName("默认项目");
        com.cmdb.entity.Asset existing = new com.cmdb.entity.Asset();
        existing.setId(9L);
        existing.setName("asset-a");
        existing.setProject(project);
        when(projects.findById(1L)).thenReturn(Optional.of(project));
        when(assets.findByNameIgnoreCaseAndProjectId("asset-a", 1L)).thenReturn(Optional.of(existing));
        AssetController controller = new AssetController(assets, projects, mock(AssetTypeOptionRepository.class),
                mock(AssetChangeRepository.class), mock(ImportAuditRepository.class), mock(ImportFailureRepository.class),
                mock(RegionOptionRepository.class), mock(AuditService.class));

        assertThrows(com.cmdb.config.BadRequestException.class, () -> controller.create(
                mock(jakarta.servlet.http.HttpServletRequest.class), Map.of("name", "asset-a", "projectId", 1L)));
        verify(assets, never()).saveAndFlush(any());
    }

    @Test
    void previewDoesNotExposeOrSwallowUnexpectedRepositoryFailures() {
        AssetRepository assets = mock(AssetRepository.class);
        ProjectRepository projects = mock(ProjectRepository.class);
        Project project = new Project();
        project.setId(1L);
        project.setName("默认项目");
        when(projects.findByNameIgnoreCase("默认项目")).thenReturn(List.of(project));
        when(assets.findByNameIgnoreCaseAndProjectId("asset-a", 1L))
                .thenThrow(new IllegalStateException("database unavailable"));
        AssetController controller = new AssetController(assets, projects, mock(AssetTypeOptionRepository.class),
                mock(AssetChangeRepository.class), mock(ImportAuditRepository.class), mock(ImportFailureRepository.class),
                mock(RegionOptionRepository.class), mock(AuditService.class));
        String csv = "名称,类型,环境,内网IP,外网IP,主机名,项目名称,状态,区域,描述\n"
                + "asset-a,SERVER,PRODUCTION,10.0.0.1,,,默认项目,ONLINE,,\n";
        MockMultipartFile file = new MockMultipartFile("file", "assets.csv", "text/csv",
                csv.getBytes(StandardCharsets.UTF_8));

        assertThrows(IllegalStateException.class, () -> controller.validateImport(file, "MERGE"));
    }
}
