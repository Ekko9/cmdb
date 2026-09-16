package com.cmdb.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "cmdb_operation_audit")
@Getter
@Setter
@NoArgsConstructor
public class OperationAudit {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(length = 50)
    private String operator;
    @Column(length = 30)
    private String role;
    @Column(nullable = false, length = 50)
    private String action;
    @Column(nullable = false, length = 50)
    private String resourceType;
    @Column(length = 80)
    private String resourceId;
    @Column(length = 200)
    private String resourceName;
    @Column(nullable = false, length = 30)
    private String result;
    @Column(length = 1000)
    private String message;
    @Column(length = 64)
    private String clientIp;
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        createdAt = LocalDateTime.now();
    }
}
