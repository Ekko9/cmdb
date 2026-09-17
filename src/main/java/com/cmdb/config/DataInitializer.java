package com.cmdb.config;

import com.cmdb.entity.User;
import com.cmdb.entity.Project;
import com.cmdb.repo.ProjectRepository;
import com.cmdb.repo.UserRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.beans.factory.annotation.Value;

@Configuration
public class DataInitializer {
    private static final String PASSWORD_PATTERN = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[^A-Za-z0-9]).{8,}$";

    @Value("${cmdb.initial-admin-password}") private String initialAdminPassword;

    @Bean public BCryptPasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(); }
    @Bean public CommandLineRunner init(UserRepository users, ProjectRepository projects, BCryptPasswordEncoder encoder) {
        return args -> {
            if (!users.findByUsername("admin").isPresent()) {
                if (initialAdminPassword == null || !initialAdminPassword.matches(PASSWORD_PATTERN)) {
                    throw new IllegalStateException("首次初始化必须通过 ADMIN_PASSWORD 设置符合密码策略的管理员密码");
                }
                User user = new User();
                user.setUsername("admin");
                user.setPassword(encoder.encode(initialAdminPassword));
                user.setDisplayName("系统管理员");
                user.setRole("ADMIN");
                user.setEnabled(true);
                users.save(user);
            }
            if (projects.count() == 0) {
                Project project = new Project();
                project.setName("默认项目");
                project.setCode("DEFAULT");
                project.setOwner("系统管理员");
                project.setDescription("CMDB 默认项目空间");
                projects.save(project);
            }
        };
    }
}
