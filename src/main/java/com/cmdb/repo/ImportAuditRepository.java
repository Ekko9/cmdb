package com.cmdb.repo;

import com.cmdb.entity.ImportAudit;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ImportAuditRepository extends JpaRepository<ImportAudit, Long> {
    Page<ImportAudit> findAllByOrderByCreatedAtDescIdDesc(Pageable pageable);
}
