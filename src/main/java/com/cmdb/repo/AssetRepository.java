package com.cmdb.repo;
import com.cmdb.entity.Asset;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import java.util.List;
public interface AssetRepository extends JpaRepository<Asset, Long>, JpaSpecificationExecutor<Asset> {
    @Override
    @EntityGraph(attributePaths = "project")
    List<Asset> findAll();

    @Override
    @EntityGraph(attributePaths = "project")
    Page<Asset> findAll(Pageable pageable);

    @EntityGraph(attributePaths = "project")
    List<Asset> findByProjectId(Long projectId);

    long countByProjectId(Long projectId);

    long countByStatus(String status);
    long countByEnvironment(String environment);

    boolean existsByNameIgnoreCaseAndProjectId(String name, Long projectId);
    boolean existsByPrivateIpIgnoreCase(String privateIp);
    boolean existsByPublicIpIgnoreCase(String publicIp);
}
