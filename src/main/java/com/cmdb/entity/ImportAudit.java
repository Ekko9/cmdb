package com.cmdb.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "cmdb_import_audit")
@Getter
@Setter
@NoArgsConstructor
public class ImportAudit {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, length = 255)
    private String filename;
    @Column(length = 20)
    private String fileType;
    @Column(nullable = false, length = 30)
    private String status = "RUNNING";
    private Integer totalRows = 0;
    private Integer successCount = 0;
    private Integer failedCount = 0;
    @Column(length = 50)
    private String operator;
    private LocalDateTime createdAt;
    private LocalDateTime completedAt;

    @PrePersist
    public void prePersist() {
        createdAt = LocalDateTime.now();
    }
}
