package com.cmdb.repo;

import com.cmdb.entity.AssetRelation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;

public interface AssetRelationRepository extends JpaRepository<AssetRelation, Long> {
    @Query("select r from AssetRelation r where r.sourceAsset.project.id = :projectId or r.targetAsset.project.id = :projectId")
    List<AssetRelation> findForProject(@Param("projectId") Long projectId);
    boolean existsBySourceAssetIdAndTargetAssetIdAndRelationType(Long sourceId, Long targetId, String relationType);
    void deleteBySourceAssetIdOrTargetAssetId(Long sourceId, Long targetId);
}
