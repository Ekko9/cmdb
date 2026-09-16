package com.cmdb.repo;

import com.cmdb.entity.RegionOption;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RegionOptionRepository extends JpaRepository<RegionOption, Long> {
    List<RegionOption> findByEnabledTrueOrderBySortOrderAscCountryAscRegionAsc();
}
