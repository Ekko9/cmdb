package com.cmdb.repo;

import com.cmdb.entity.AssetLifecycleChange;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface AssetLifecycleChangeRepository extends JpaRepository<AssetLifecycleChange, Long> {
    List<AssetLifecycleChange> findTop100ByAssetIdOrderByCreatedAtDescIdDesc(Long assetId);
}
