package com.cmdb.entity;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "cmdb_asset", uniqueConstraints = {
        @UniqueConstraint(name = "uk_cmdb_asset_project_name", columnNames = {"project_id", "name"}),
        @UniqueConstraint(name = "uk_cmdb_asset_private_ip", columnNames = "private_ip"),
        @UniqueConstraint(name = "uk_cmdb_asset_public_ip", columnNames = "public_ip")
})
@Getter @Setter @NoArgsConstructor
public class Asset {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false, length = 100) private String name;
    @Column(length = 50) private String assetType;
    @Column(length = 50) private String environment;
    @Column(length = 45) private String privateIp;
    @Column(length = 45) private String publicIp;
    @Column(length = 100) private String hostname;
    @Column(length = 30) private String status = "ONLINE";
    @Column(name = "lifecycle_state", nullable = false, length = 30) private String lifecycleState = "IN_SERVICE";
    @Column(length = 100) private String region;
    @Column(length = 500) private String description;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "project_id") private Project project;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    @PrePersist public void prePersist() { createdAt = updatedAt = LocalDateTime.now(); }
    @PreUpdate public void preUpdate() { updatedAt = LocalDateTime.now(); }
}
