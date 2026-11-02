package com.cmdb.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "cmdb_asset_relation", uniqueConstraints = @UniqueConstraint(
        name = "uk_cmdb_asset_relation", columnNames = {"source_asset_id", "target_asset_id", "relation_type"}))
@Getter @Setter @NoArgsConstructor
public class AssetRelation {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "source_asset_id") private Asset sourceAsset;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "target_asset_id") private Asset targetAsset;
    @Column(name = "relation_type", nullable = false, length = 30) private String relationType;
    @Column(length = 500) private String description;
    @Column(length = 50) private String createdBy;
    private LocalDateTime createdAt;

    @PrePersist public void prePersist() { createdAt = LocalDateTime.now(); }
}
