package com.cmdb.controller;

import com.cmdb.config.AuditService;
import com.cmdb.config.BadRequestException;
import com.cmdb.config.ResourceNotFoundException;
import com.cmdb.entity.Asset;
import com.cmdb.entity.AssetLifecycleChange;
import com.cmdb.repo.AssetLifecycleChangeRepository;
import com.cmdb.repo.AssetRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/assets")
public class AssetLifecycleController {
    private static final Map<String, Set<String>> TRANSITIONS = Map.of(
            "PLANNED", Set.of("IN_SERVICE", "RETIRED"),
            "IN_SERVICE", Set.of("RETIRING"),
            "RETIRING", Set.of("IN_SERVICE", "RETIRED"),
            "RETIRED", Set.of());
    private final AssetRepository assets;
    private final AssetLifecycleChangeRepository changes;
    private final AuditService audit;

    public AssetLifecycleController(AssetRepository assets, AssetLifecycleChangeRepository changes, AuditService audit) {
        this.assets = assets;
        this.changes = changes;
        this.audit = audit;
    }

    @PutMapping("/{id}/lifecycle")
    @Transactional
    public Map<String, Object> transition(HttpServletRequest request, @PathVariable Long id, @RequestBody Map<String, Object> body) {
        Asset asset = assets.findById(id).orElseThrow(() -> new ResourceNotFoundException("资产不存在"));
        String target = body.get("state") == null ? "" : String.valueOf(body.get("state")).trim().toUpperCase(Locale.ROOT);
        String reason = body.get("reason") == null ? null : String.valueOf(body.get("reason")).trim();
        if (reason != null && reason.length() > 500) throw new BadRequestException("流转原因不能超过 500 个字符");
        if (!TRANSITIONS.getOrDefault(asset.getLifecycleState(), Set.of()).contains(target)) {
            throw new BadRequestException("不允许生命周期从 " + asset.getLifecycleState() + " 流转到 " + target);
        }
        AssetLifecycleChange change = new AssetLifecycleChange();
        change.setAssetId(id);
        change.setAssetName(asset.getName());
        change.setFromState(asset.getLifecycleState());
        change.setToState(target);
        change.setReason(reason);
        change.setOperator(audit.operator(request));
        changes.save(change);
        asset.setLifecycleState(target);
        assets.save(asset);
        audit.operation(request, "LIFECYCLE", "ASSET", id, asset.getName(), "SUCCESS", change.getFromState() + " -> " + target);
        return Map.of("id", id, "lifecycleState", target);
    }

    @GetMapping("/{id}/lifecycle/history")
    @Transactional(readOnly = true)
    public List<Map<String, Object>> history(@PathVariable Long id) {
        if (!assets.existsById(id)) throw new ResourceNotFoundException("资产不存在");
        List<Map<String, Object>> result = new ArrayList<>();
        for (AssetLifecycleChange change : changes.findTop100ByAssetIdOrderByCreatedAtDescIdDesc(id)) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", change.getId()); row.put("fromState", change.getFromState()); row.put("toState", change.getToState());
            row.put("reason", change.getReason()); row.put("operator", change.getOperator()); row.put("createdAt", change.getCreatedAt());
            result.add(row);
        }
        return result;
    }
}
