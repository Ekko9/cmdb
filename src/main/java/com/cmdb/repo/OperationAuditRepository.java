package com.cmdb.repo;

import com.cmdb.entity.OperationAudit;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface OperationAuditRepository extends JpaRepository<OperationAudit, Long>, JpaSpecificationExecutor<OperationAudit> {
    Page<OperationAudit> findAllByOrderByCreatedAtDescIdDesc(Pageable pageable);
}
