package com.cmdb.controller;

import com.cmdb.config.AuditService;
import com.cmdb.entity.Project;
import com.cmdb.repo.AssetRepository;
import com.cmdb.repo.ProjectRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/projects")
public class ProjectController {
    private final ProjectRepository projects;
    private final AssetRepository assets;
    private final AuditService auditService;

    public ProjectController(ProjectRepository projects, AssetRepository assets, AuditService auditService) {
        this.projects = projects;
        this.assets = assets;
        this.auditService = auditService;
    }

    @GetMapping
    @Transactional(readOnly = true)
    public List<Map<String, Object>> list() {
        List<Map<String, Object>> result = new ArrayList<>();
        for (Project project : projects.findAll()) {
            result.add(view(project));
        }
        return result;
    }

    @PostMapping
    public Map<String, Object> create(HttpServletRequest request, @RequestBody Project form) {
        validate(form);
        if (form.getCode() != null && projects.findByCode(form.getCode()).isPresent()) {
            throw new RuntimeException("项目编码已存在");
        }
        Project saved = projects.save(form);
        auditService.operation(request, "CREATE", "PROJECT", saved.getId(), saved.getName(), "SUCCESS", "新增项目");
        return view(saved);
    }

    @PutMapping("/{id}")
    public Map<String, Object> update(HttpServletRequest request, @PathVariable Long id, @RequestBody Project form) {
        validate(form);
        Project project = projects.findById(id).orElseThrow(() -> new RuntimeException("项目不存在"));
        if (form.getCode() != null) {
            Optional<Project> sameCode = projects.findByCode(form.getCode());
            if (sameCode.isPresent() && !sameCode.get().getId().equals(id)) {
                throw new RuntimeException("项目编码已存在");
            }
        }
        project.setName(form.getName());
        project.setCode(form.getCode());
        project.setOwner(form.getOwner());
        project.setDescription(form.getDescription());
        Project saved = projects.save(project);
        auditService.operation(request, "UPDATE", "PROJECT", saved.getId(), saved.getName(), "SUCCESS", "编辑项目");
        return view(saved);
    }

    @DeleteMapping("/{id}")
    public void delete(HttpServletRequest request, @PathVariable Long id) {
        Project project = projects.findById(id).orElseThrow(() -> new RuntimeException("项目不存在"));
        long assetCount = assets.countByProjectId(id);
        if (assetCount > 0) {
            throw new RuntimeException("项目下还有 " + assetCount + " 个资产，请先删除或迁移资产");
        }
        projects.delete(project);
        auditService.operation(request, "DELETE", "PROJECT", id, project.getName(), "SUCCESS", "删除项目");
    }

    private void validate(Project form) {
        if (form == null || form.getName() == null || form.getName().trim().isEmpty()) {
            throw new RuntimeException("项目名称不能为空");
        }
        form.setName(form.getName().trim());
        form.setCode(trimToNull(form.getCode()));
        form.setOwner(trimToNull(form.getOwner()));
        form.setDescription(trimToNull(form.getDescription()));
    }

    private String trimToNull(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private Map<String, Object> view(Project project) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", project.getId());
        result.put("name", project.getName());
        result.put("code", project.getCode());
        result.put("owner", project.getOwner());
        result.put("description", project.getDescription());
        result.put("assetCount", assets.countByProjectId(project.getId()));
        result.put("createdAt", project.getCreatedAt());
        return result;
    }
}
