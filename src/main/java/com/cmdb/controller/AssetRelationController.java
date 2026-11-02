package com.cmdb.controller;

import com.cmdb.config.AuditService;
import com.cmdb.config.BadRequestException;
import com.cmdb.config.ResourceNotFoundException;
import com.cmdb.entity.Asset;
import com.cmdb.entity.AssetRelation;
import com.cmdb.repo.AssetRelationRepository;
import com.cmdb.repo.AssetRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/assets")
public class AssetRelationController {
    private static final Set<String> TYPES = Set.of("DEPENDS_ON", "HOSTED_ON", "CONNECTS_TO", "CONTAINS", "BACKUP_OF");
    private final AssetRepository assets;
    private final AssetRelationRepository relations;
    private final AuditService audit;

    public AssetRelationController(AssetRepository assets, AssetRelationRepository relations, AuditService audit) {
        this.assets = assets; this.relations = relations; this.audit = audit;
    }

    @GetMapping("/topology")
    @Transactional(readOnly = true)
    public Map<String, Object> topology(@RequestParam Long projectId) {
        List<Asset> projectAssets = assets.findByProjectId(projectId);
        Map<Long, Asset> nodes = new LinkedHashMap<>();
        for (Asset asset : projectAssets) nodes.put(asset.getId(), asset);
        List<Map<String, Object>> edges = new ArrayList<>();
        for (AssetRelation relation : relations.findForProject(projectId)) {
            if (!nodes.containsKey(relation.getSourceAsset().getId()) || !nodes.containsKey(relation.getTargetAsset().getId())) continue;
            Map<String, Object> edge = new LinkedHashMap<>(); edge.put("id", relation.getId());
            edge.put("sourceId", relation.getSourceAsset().getId()); edge.put("targetId", relation.getTargetAsset().getId());
            edge.put("type", relation.getRelationType()); edge.put("description", relation.getDescription()); edges.add(edge);
        }
        List<Map<String, Object>> nodeViews = new ArrayList<>();
        for (Asset asset : nodes.values()) {
            Map<String, Object> node = new LinkedHashMap<>(); node.put("id", asset.getId()); node.put("name", asset.getName());
            node.put("assetType", asset.getAssetType()); node.put("status", asset.getStatus()); node.put("lifecycleState", asset.getLifecycleState());
            node.put("privateIp", asset.getPrivateIp()); nodeViews.add(node);
        }
        return Map.of("projectId", projectId, "nodes", nodeViews, "edges", edges);
    }

    @PostMapping("/{id}/relations")
    @Transactional
    public Map<String, Object> create(HttpServletRequest request, @PathVariable Long id, @RequestBody Map<String, Object> body) {
        Asset source = assets.findById(id).orElseThrow(() -> new ResourceNotFoundException("资产不存在"));
        Long targetId;
        try { targetId = Long.valueOf(String.valueOf(body.get("targetId"))); }
        catch (RuntimeException ex) { throw new BadRequestException("目标资产 ID 无效"); }
        Asset target = assets.findById(targetId).orElseThrow(() -> new ResourceNotFoundException("目标资产不存在"));
        String type = body.get("type") == null ? "" : String.valueOf(body.get("type")).trim().toUpperCase(Locale.ROOT);
        if (source.getId().equals(target.getId())) throw new BadRequestException("资产不能关联自身");
        if (!Objects.equals(source.getProject().getId(), target.getProject().getId())) throw new BadRequestException("关系两端必须属于同一项目");
        if (!TYPES.contains(type)) throw new BadRequestException("关系类型无效");
        if (relations.existsBySourceAssetIdAndTargetAssetIdAndRelationType(id, targetId, type)) throw new BadRequestException("该资产关系已存在");
        AssetRelation relation = new AssetRelation(); relation.setSourceAsset(source); relation.setTargetAsset(target); relation.setRelationType(type);
        relation.setCreatedBy(audit.operator(request));
        if (body.get("description") != null) {
            String description = String.valueOf(body.get("description")).trim();
            if (description.length() > 500) throw new BadRequestException("关系说明不能超过 500 个字符");
            relation.setDescription(description);
        }
        relation = relations.save(relation);
        audit.operation(request, "CREATE_RELATION", "ASSET_RELATION", relation.getId(), source.getName() + " -> " + target.getName(), "SUCCESS", type);
        return Map.of("id", relation.getId(), "sourceId", id, "targetId", targetId, "type", type);
    }

    @DeleteMapping("/{id}/relations/{relationId}")
    @Transactional
    public void delete(HttpServletRequest request, @PathVariable Long id, @PathVariable Long relationId) {
        AssetRelation relation = relations.findById(relationId).orElseThrow(() -> new ResourceNotFoundException("资产关系不存在"));
        if (!relation.getSourceAsset().getId().equals(id)) throw new BadRequestException("关系不属于该源资产");
        relations.delete(relation);
        audit.operation(request, "DELETE_RELATION", "ASSET_RELATION", relationId, relation.getSourceAsset().getName(), "SUCCESS", relation.getRelationType());
    }
}
