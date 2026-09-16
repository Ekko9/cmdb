package com.cmdb.repo;

import com.cmdb.entity.OperationAudit;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OperationAuditRepository extends JpaRepository<OperationAudit, Long> {
    Page<OperationAudit> findAllByOrderByCreatedAtDescIdDesc(Pageable pageable);
}
