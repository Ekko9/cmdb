package com.cmdb.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "cmdb_asset_lifecycle_change", indexes = @Index(name = "idx_asset_lifecycle_asset_time", columnList = "asset_id,created_at"))
@Getter @Setter @NoArgsConstructor
public class AssetLifecycleChange {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "asset_id", nullable = false) private Long assetId;
    @Column(name = "asset_name", nullable = false, length = 100) private String assetName;
    @Column(name = "from_state", nullable = false, length = 30) private String fromState;
    @Column(name = "to_state", nullable = false, length = 30) private String toState;
    @Column(length = 500) private String reason;
    @Column(length = 50) private String operator;
    private LocalDateTime createdAt;

    @PrePersist public void prePersist() { createdAt = LocalDateTime.now(); }
}
