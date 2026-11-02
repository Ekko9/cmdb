package com.cmdb.controller;

import com.cmdb.config.AuditService;
import com.cmdb.config.BadRequestException;
import com.cmdb.config.ResourceNotFoundException;
import com.cmdb.entity.Asset;
import com.cmdb.entity.Project;
import com.cmdb.repo.*;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AssetBatchDeleteTest {
    @Test
    void batchDeleteAuditsEachAssetBeforeDeleting() {
        AssetRepository assets = mock(AssetRepository.class);
        AuditService audit = mock(AuditService.class);
        Project project = new Project(); project.setId(3L); project.setName("Operations");
        Asset first = asset(1L, "one", project), second = asset(2L, "two", project);
        when(assets.findAllById(anyIterable())).thenReturn(List.of(first, second));
        when(audit.operator(any())).thenReturn("admin");
        AssetController controller = new AssetController(assets, mock(ProjectRepository.class), mock(AssetTypeOptionRepository.class),
                mock(AssetChangeRepository.class), mock(ImportAuditRepository.class), mock(ImportFailureRepository.class),
                mock(RegionOptionRepository.class), audit);
        HttpServletRequest request = mock(HttpServletRequest.class);

        var result = controller.deleteBatch(request, List.of(1L, 2L));

        assertEquals(2, result.get("deletedCount"));
        verify(audit, times(2)).assetChange(anyLong(), anyString(), eq("DELETE"), isNull(), anyString(), isNull(), eq("admin"));
        verify(audit, times(2)).operation(eq(request), eq("DELETE"), eq("ASSET"), anyLong(), anyString(), eq("SUCCESS"), eq("批量删除资产"));
        verify(assets).deleteAll(List.of(first, second));
    }

    @Test
    void rejectsMissingAssetsWithoutDeletingAny() {
        AssetRepository assets = mock(AssetRepository.class);
        when(assets.findAllById(anyIterable())).thenReturn(List.of());
        AssetController controller = new AssetController(assets, mock(ProjectRepository.class), mock(AssetTypeOptionRepository.class),
                mock(AssetChangeRepository.class), mock(ImportAuditRepository.class), mock(ImportFailureRepository.class),
                mock(RegionOptionRepository.class), mock(AuditService.class));

        assertThrows(ResourceNotFoundException.class, () -> controller.deleteBatch(mock(HttpServletRequest.class), List.of(1L)));
        verify(assets, never()).deleteAll(anyIterable());
    }

    @Test
    void rejectsEmptyAndOversizedSelections() {
        AssetController controller = new AssetController(mock(AssetRepository.class), mock(ProjectRepository.class),
                mock(AssetTypeOptionRepository.class), mock(AssetChangeRepository.class), mock(ImportAuditRepository.class),
                mock(ImportFailureRepository.class), mock(RegionOptionRepository.class), mock(AuditService.class));
        assertThrows(BadRequestException.class, () -> controller.deleteBatch(mock(HttpServletRequest.class), List.of()));
        assertThrows(BadRequestException.class, () -> controller.deleteBatch(mock(HttpServletRequest.class),
                java.util.stream.LongStream.rangeClosed(1, 101).boxed().toList()));
    }

    private Asset asset(Long id, String name, Project project) {
        Asset asset = new Asset(); asset.setId(id); asset.setName(name); asset.setProject(project); return asset;
    }
}
