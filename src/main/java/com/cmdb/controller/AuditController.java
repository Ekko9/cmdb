package com.cmdb.controller;

import com.cmdb.entity.OperationAudit;
import com.cmdb.repo.OperationAuditRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/audits")
public class AuditController {
    private final OperationAuditRepository audits;

    public AuditController(OperationAuditRepository audits) {
        this.audits = audits;
    }

    @GetMapping
    @Transactional(readOnly = true)
    public Map<String, Object> list(@RequestParam(defaultValue = "0") int page,
                                    @RequestParam(defaultValue = "20") int size) {
        Page<OperationAudit> auditPage = audits.findAllByOrderByCreatedAtDescIdDesc(
                PageRequest.of(Math.max(0, page), Math.min(Math.max(1, size), 100)));
        List<Map<String, Object>> content = new ArrayList<>();
        for (OperationAudit audit : auditPage.getContent()) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", audit.getId());
            item.put("operator", audit.getOperator());
            item.put("role", audit.getRole());
            item.put("action", audit.getAction());
            item.put("resourceType", audit.getResourceType());
            item.put("resourceId", audit.getResourceId());
            item.put("resourceName", audit.getResourceName());
            item.put("result", audit.getResult());
            item.put("message", audit.getMessage());
            item.put("clientIp", audit.getClientIp());
            item.put("createdAt", audit.getCreatedAt());
            content.add(item);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("content", content);
        result.put("page", auditPage.getNumber());
        result.put("size", auditPage.getSize());
        result.put("totalElements", auditPage.getTotalElements());
        result.put("totalPages", auditPage.getTotalPages());
        return result;
    }
}
