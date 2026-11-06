package com.cmdb.config;

import com.cmdb.entity.OperationAudit;
import com.cmdb.entity.OperationAuditArchive;
import com.cmdb.repo.OperationAuditArchiveRepository;
import com.cmdb.repo.OperationAuditRepository;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AuditArchiveServiceTest {
    @Test
    void copiesAndDeletesOneCommittedBatch() {
        OperationAuditRepository audits = mock(OperationAuditRepository.class);
        OperationAuditArchiveRepository archives = mock(OperationAuditArchiveRepository.class);
        OperationAudit source = new OperationAudit();
        source.setId(7L);
        source.setAction("UPDATE");
        source.setResourceType("ASSET");
        source.setResult("SUCCESS");
        source.setCreatedAt(LocalDateTime.of(2025, 1, 1, 0, 0));
        when(audits.findArchiveBatch(any(), any(Pageable.class))).thenReturn(List.of(source));
        when(archives.saveAllAndFlush(anyIterable())).thenAnswer(call -> call.getArgument(0));
        AuditArchiveService service = new AuditArchiveService(audits, archives);
        LocalDateTime archivedAt = LocalDateTime.of(2026, 11, 6, 12, 0);

        assertEquals(1, service.archiveBatch(LocalDateTime.of(2026, 1, 1, 0, 0), archivedAt));

        verify(archives).saveAllAndFlush(argThat(rows -> {
            OperationAuditArchive archived = rows.iterator().next();
            return archived.getId().equals(7L) && archivedAt.equals(archived.getArchivedAt());
        }));
        verify(audits).deleteAllInBatch(List.of(source));
    }
}
