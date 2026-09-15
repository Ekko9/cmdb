package com.cmdb.repo;
import com.cmdb.entity.Project;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
public interface ProjectRepository extends JpaRepository<Project, Long> {
    Optional<Project> findByCode(String code);
    List<Project> findByNameIgnoreCase(String name);
}
