package com.cmdb.repo;

import com.cmdb.entity.OperationAudit;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

import java.time.LocalDateTime;
import java.util.List;

public interface OperationAuditRepository extends JpaRepository<OperationAudit, Long>, JpaSpecificationExecutor<OperationAudit> {
    Page<OperationAudit> findAllByOrderByCreatedAtDescIdDesc(Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from OperationAudit a where a.archived = false and a.createdAt < :cutoff order by a.id")
    List<OperationAudit> findArchiveBatch(@Param("cutoff") LocalDateTime cutoff, Pageable pageable);
}
