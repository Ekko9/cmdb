package com.cmdb.repo;

import com.cmdb.entity.OperationAuditArchive;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface OperationAuditArchiveRepository extends JpaRepository<OperationAuditArchive, Long>,
        JpaSpecificationExecutor<OperationAuditArchive> {
}
