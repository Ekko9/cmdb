package com.cmdb.entity;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import javax.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "cmdb_asset")
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
    @Column(length = 100) private String region;
    @Column(length = 500) private String description;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "project_id") private Project project;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    @PrePersist public void prePersist() { createdAt = updatedAt = LocalDateTime.now(); }
    @PreUpdate public void preUpdate() { updatedAt = LocalDateTime.now(); }
}
