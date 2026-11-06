package com.cmdb.controller;

import com.cmdb.config.AuditService;
import com.cmdb.config.AuditArchiveService;
import com.cmdb.config.BadRequestException;
import com.cmdb.repo.OperationAuditArchiveRepository;
import com.cmdb.repo.OperationAuditRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AuditControllerTest {
    @Test
    void rejectsInvalidDateRange() {
        AuditController controller = new AuditController(mock(OperationAuditRepository.class),
                mock(OperationAuditArchiveRepository.class), mock(AuditArchiveService.class), mock(AuditService.class));
        assertThrows(BadRequestException.class, () -> controller.list(0, 20, null, null, null, null, null,
                "2026-11-02T10:00:00", "2026-11-01T10:00:00", false));
    }

    @Test
    void archiveProcessesBatchesAndWritesAudit() {
        OperationAuditRepository audits = mock(OperationAuditRepository.class);
        OperationAuditArchiveRepository archives = mock(OperationAuditArchiveRepository.class);
        AuditArchiveService archiveService = mock(AuditArchiveService.class);
        AuditService auditService = mock(AuditService.class);
        when(archiveService.archiveBatch(any(), any())).thenReturn(500, 20, 0);
        AuditController controller = new AuditController(audits, archives, archiveService, auditService);
        HttpServletRequest request = mock(HttpServletRequest.class);

        var result = controller.archive(request, "2025-11-02T00:00:00");

        assertEquals(520, result.get("archivedCount"));
        verify(archiveService, times(3)).archiveBatch(eq(java.time.LocalDateTime.of(2025, 11, 2, 0, 0)), any());
        verify(auditService).operation(request, "ARCHIVE", "AUDIT", null, null, "SUCCESS", "归档截止 2025-11-02T00:00，记录数 520");
    }
}
