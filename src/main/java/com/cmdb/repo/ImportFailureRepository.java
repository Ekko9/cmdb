package com.cmdb.repo;

import com.cmdb.entity.ImportAudit;
import com.cmdb.entity.ImportFailure;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ImportFailureRepository extends JpaRepository<ImportFailure, Long> {
    List<ImportFailure> findByImportAuditOrderByRowNumberAscIdAsc(ImportAudit importAudit);
}
