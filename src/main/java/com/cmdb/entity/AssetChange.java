package com.cmdb.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "cmdb_asset_change")
@Getter
@Setter
@NoArgsConstructor
public class AssetChange {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false)
    private Long assetId;
    @Column(nullable = false, length = 100)
    private String assetName;
    @Column(nullable = false, length = 30)
    private String changeType;
    @Column(length = 50)
    private String fieldName;
    @Column(length = 1000)
    private String oldValue;
    @Column(length = 1000)
    private String newValue;
    @Column(length = 50)
    private String operator;
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        createdAt = LocalDateTime.now();
    }
}
