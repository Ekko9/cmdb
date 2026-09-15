package com.cmdb.controller;

import com.cmdb.entity.Project;
import com.cmdb.repo.AssetRepository;
import com.cmdb.repo.ProjectRepository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/projects")
public class ProjectController {
    private final ProjectRepository projects;
    private final AssetRepository assets;

    public ProjectController(ProjectRepository projects, AssetRepository assets) {
        this.projects = projects;
        this.assets = assets;
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
    public Map<String, Object> create(@RequestBody Project form) {
        validate(form);
        if (form.getCode() != null && projects.findByCode(form.getCode()).isPresent()) {
            throw new RuntimeException("项目编码已存在");
        }
        return view(projects.save(form));
    }

    @PutMapping("/{id}")
    public Map<String, Object> update(@PathVariable Long id, @RequestBody Project form) {
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
        return view(projects.save(project));
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        Project project = projects.findById(id).orElseThrow(() -> new RuntimeException("项目不存在"));
        long assetCount = assets.countByProjectId(id);
        if (assetCount > 0) {
            throw new RuntimeException("项目下还有 " + assetCount + " 个资产，请先删除或迁移资产");
        }
        projects.delete(project);
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
