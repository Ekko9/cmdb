package com.cmdb.entity;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import javax.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "sys_user")
@Getter @Setter @NoArgsConstructor
public class User {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, unique = true, length = 50) private String username;
    @Column(nullable = false) private String password;
    @Column(nullable = false, length = 100) private String displayName;
    @Column(length = 30) private String role = "OPERATOR";
    @Column(nullable = false) private Boolean enabled = true;
    private LocalDateTime createdAt;
    @PrePersist public void prePersist() { createdAt = LocalDateTime.now(); }
}
