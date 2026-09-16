package com.cmdb.repo;

import com.cmdb.entity.AssetChange;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AssetChangeRepository extends JpaRepository<AssetChange, Long> {
    List<AssetChange> findTop100ByAssetIdOrderByCreatedAtDescIdDesc(Long assetId);
}
