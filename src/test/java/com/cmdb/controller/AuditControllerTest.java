package com.cmdb.controller;

import com.cmdb.config.AuditService;
import com.cmdb.config.BadRequestException;
import com.cmdb.repo.OperationAuditRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AuditControllerTest {
    @Test
    void rejectsInvalidDateRange() {
        AuditController controller = new AuditController(mock(OperationAuditRepository.class), mock(AuditService.class));
        assertThrows(BadRequestException.class, () -> controller.list(0, 20, null, null, null, null, null,
                "2026-11-02T10:00:00", "2026-11-01T10:00:00", false));
    }

    @Test
    void archiveProcessesBatchesAndWritesAudit() {
        OperationAuditRepository audits = mock(OperationAuditRepository.class);
        AuditService auditService = mock(AuditService.class);
        when(audits.findAll(any(org.springframework.data.jpa.domain.Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()), new PageImpl<>(List.of()));
        when(audits.saveAllAndFlush(anyIterable())).thenAnswer(call -> call.getArgument(0));
        when(auditService.operator(any())).thenReturn("admin");
        AuditController controller = new AuditController(audits, auditService);
        HttpServletRequest request = mock(HttpServletRequest.class);

        var result = controller.archive(request, "2025-11-02T00:00:00");

        assertEquals(0, result.get("archivedCount"));
        verify(audits, times(1)).findAll(any(org.springframework.data.jpa.domain.Specification.class), any(Pageable.class));
        verify(auditService).operation(request, "ARCHIVE", "AUDIT", null, null, "SUCCESS", "归档截止 2025-11-02T00:00，记录数 0");
    }
}
