package com.cmdb.repo;
import com.cmdb.entity.Asset;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface AssetRepository extends JpaRepository<Asset, Long> {
    @Override
    @EntityGraph(attributePaths = "project")
    List<Asset> findAll();

    @EntityGraph(attributePaths = "project")
    List<Asset> findByProjectId(Long projectId);

    long countByProjectId(Long projectId);

    long countByStatus(String status);
    long countByEnvironment(String environment);
}
