package com.cmdb.config;

import com.cmdb.entity.OperationAudit;
import com.cmdb.entity.OperationAuditArchive;
import com.cmdb.repo.OperationAuditArchiveRepository;
import com.cmdb.repo.OperationAuditRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class AuditArchiveService {
    private static final int BATCH_SIZE = 500;
    private final OperationAuditRepository audits;
    private final OperationAuditArchiveRepository archives;

    public AuditArchiveService(OperationAuditRepository audits, OperationAuditArchiveRepository archives) {
        this.audits = audits;
        this.archives = archives;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int archiveBatch(LocalDateTime cutoff, LocalDateTime archivedAt) {
        List<OperationAudit> batch = audits.findArchiveBatch(cutoff,
                PageRequest.of(0, BATCH_SIZE, Sort.by(Sort.Direction.ASC, "id")));
        if (batch.isEmpty()) return 0;

        List<OperationAuditArchive> archiveRows = new ArrayList<>(batch.size());
        for (OperationAudit source : batch) archiveRows.add(copy(source, archivedAt));
        archives.saveAllAndFlush(archiveRows);
        audits.deleteAllInBatch(batch);
        return batch.size();
    }

    private OperationAuditArchive copy(OperationAudit source, LocalDateTime archivedAt) {
        OperationAuditArchive target = new OperationAuditArchive();
        target.setId(source.getId());
        target.setOperator(source.getOperator());
        target.setRole(source.getRole());
        target.setAction(source.getAction());
        target.setResourceType(source.getResourceType());
        target.setResourceId(source.getResourceId());
        target.setResourceName(source.getResourceName());
        target.setResult(source.getResult());
        target.setMessage(source.getMessage());
        target.setClientIp(source.getClientIp());
        target.setCreatedAt(source.getCreatedAt());
        target.setArchivedAt(archivedAt);
        return target;
    }
}
