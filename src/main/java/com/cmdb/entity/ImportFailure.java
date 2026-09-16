package com.cmdb.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "cmdb_import_failure")
@Getter
@Setter
@NoArgsConstructor
public class ImportFailure {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "import_id")
    private ImportAudit importAudit;
    @Column(nullable = false)
    private Integer rowNumber;
    @Column(nullable = false, length = 1000)
    private String reason;
    @Column(length = 2000)
    private String rawData;
}
