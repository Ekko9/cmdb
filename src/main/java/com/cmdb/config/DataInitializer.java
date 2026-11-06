package com.cmdb.config;

import com.cmdb.entity.User;
import com.cmdb.entity.Project;
import com.cmdb.repo.ProjectRepository;
import com.cmdb.repo.UserRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.beans.factory.annotation.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Configuration
public class DataInitializer {
    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

    @Value("${cmdb.initial-admin-password}") private String initialAdminPassword;

    @Bean public BCryptPasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(); }
    @Bean public CommandLineRunner init(UserRepository users, ProjectRepository projects, BCryptPasswordEncoder encoder) {
        return args -> {
            if (!users.findByUsername("admin").isPresent()) {
                if (!PasswordPolicy.isValid(initialAdminPassword)) {
                    throw new IllegalStateException("首次初始化必须通过 ADMIN_PASSWORD 设置符合密码策略的管理员密码");
                }
                User user = new User();
                user.setUsername("admin");
                user.setPassword(encoder.encode(initialAdminPassword));
                user.setDisplayName("系统管理员");
                user.setRole("ADMIN");
                user.setEnabled(true);
                try {
                    users.saveAndFlush(user);
                } catch (DataIntegrityViolationException exception) {
                    if (users.findByUsername("admin").isEmpty()) throw exception;
                    log.info("管理员账号已由其他实例完成初始化");
                }
            }
            if (projects.count() == 0) {
                Project project = new Project();
                project.setName("默认项目");
                project.setCode("DEFAULT");
                project.setOwner("系统管理员");
                project.setDescription("CMDB 默认项目空间");
                try {
                    projects.saveAndFlush(project);
                } catch (DataIntegrityViolationException exception) {
                    if (projects.findByCode("DEFAULT").isEmpty()) throw exception;
                    log.info("默认项目已由其他实例完成初始化");
                }
            }
        };
    }
}
