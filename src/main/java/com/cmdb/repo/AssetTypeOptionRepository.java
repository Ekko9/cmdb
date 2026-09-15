package com.cmdb.repo;

import com.cmdb.entity.AssetTypeOption;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AssetTypeOptionRepository extends JpaRepository<AssetTypeOption, Long> {
    List<AssetTypeOption> findByEnabledTrueOrderBySortOrderAscCodeAsc();
}
