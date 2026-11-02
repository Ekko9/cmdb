package com.cmdb.controller;

import com.cmdb.config.AuditService;
import com.cmdb.config.BadRequestException;
import com.cmdb.config.CsvExportSanitizer;
import com.cmdb.entity.OperationAudit;
import com.cmdb.repo.OperationAuditRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.*;

@RestController
@RequestMapping("/api/audits")
public class AuditController {
    private static final int MAX_EXPORT_ROWS = 10_000;
    private final OperationAuditRepository audits;
    private final AuditService auditService;

    public AuditController(OperationAuditRepository audits, AuditService auditService) {
        this.audits = audits; this.auditService = auditService;
    }

    @GetMapping
    @Transactional(readOnly = true)
    public Map<String, Object> list(@RequestParam(defaultValue = "0") int page,
                                    @RequestParam(defaultValue = "20") int size,
                                    @RequestParam(required = false) String operator,
                                    @RequestParam(required = false) String action,
                                    @RequestParam(required = false) String resourceType,
                                    @RequestParam(required = false) String result,
                                    @RequestParam(required = false) String keyword,
                                    @RequestParam(required = false) String from,
                                    @RequestParam(required = false) String to,
                                    @RequestParam(defaultValue = "false") boolean archived) {
        Specification<OperationAudit> filter = filter(operator, action, resourceType, result, keyword, from, to, archived);
        Page<OperationAudit> auditPage = audits.findAll(filter, PageRequest.of(Math.max(0, page), Math.min(Math.max(1, size), 100),
                Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by(Sort.Direction.DESC, "id"))));
        List<Map<String, Object>> content = new ArrayList<>();
        for (OperationAudit audit : auditPage.getContent()) content.add(view(audit));
        Map<String, Object> resultMap = new LinkedHashMap<>();
        resultMap.put("content", content); resultMap.put("page", auditPage.getNumber()); resultMap.put("size", auditPage.getSize());
        resultMap.put("totalElements", auditPage.getTotalElements()); resultMap.put("totalPages", auditPage.getTotalPages());
        return resultMap;
    }

    @GetMapping("/export")
    @Transactional(readOnly = true)
    public ResponseEntity<byte[]> export(@RequestParam(required = false) String operator,
                                         @RequestParam(required = false) String action,
                                         @RequestParam(required = false) String resourceType,
                                         @RequestParam(required = false) String result,
                                         @RequestParam(required = false) String keyword,
                                         @RequestParam(required = false) String from,
                                         @RequestParam(required = false) String to,
                                         @RequestParam(defaultValue = "false") boolean archived) throws IOException {
        List<OperationAudit> rows = audits.findAll(filter(operator, action, resourceType, result, keyword, from, to, archived),
                PageRequest.of(0, MAX_EXPORT_ROWS + 1, Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by(Sort.Direction.DESC, "id")))).getContent();
        if (rows.size() > MAX_EXPORT_ROWS) throw new BadRequestException("单次最多导出 " + MAX_EXPORT_ROWS + " 条审计记录，请缩小筛选范围");
        StringWriter writer = new StringWriter();
        try (CSVPrinter printer = new CSVPrinter(writer, CSVFormat.DEFAULT.builder()
                .setHeader("时间", "操作人", "角色", "动作", "资源类型", "资源 ID", "资源名称", "结果", "说明", "来源 IP", "已归档").build())) {
            for (OperationAudit audit : rows) printer.printRecord(audit.getCreatedAt(), clean(audit.getOperator()), clean(audit.getRole()),
                    audit.getAction(), audit.getResourceType(), clean(audit.getResourceId()), clean(audit.getResourceName()), audit.getResult(),
                    clean(audit.getMessage()), clean(audit.getClientIp()), audit.isArchived() ? "是" : "否");
        }
        HttpHeaders headers = new HttpHeaders(); headers.setContentType(MediaType.parseMediaType("text/csv;charset=UTF-8"));
        headers.setContentDisposition(ContentDisposition.attachment().filename("cmdb-operation-audits.csv", StandardCharsets.UTF_8).build());
        return ResponseEntity.ok().headers(headers).body(("\uFEFF" + writer).getBytes(StandardCharsets.UTF_8));
    }

    @PostMapping("/archive")
    @Transactional
    public Map<String, Object> archive(HttpServletRequest request, @RequestParam String before) {
        LocalDateTime cutoff = parseDate(before, "before");
        if (cutoff.isAfter(LocalDateTime.now())) throw new BadRequestException("归档截止时间不能晚于当前时间");
        LocalDateTime now = LocalDateTime.now();
        int archivedCount = 0;
        Specification<OperationAudit> pending = (root, query, builder) -> builder.and(
                builder.isFalse(root.get("archived")), builder.lessThan(root.get("createdAt"), cutoff));
        while (true) {
            List<OperationAudit> batch = audits.findAll(pending, PageRequest.of(0, 500, Sort.by(Sort.Direction.ASC, "id"))).getContent();
            if (batch.isEmpty()) break;
            for (OperationAudit row : batch) { row.setArchived(true); row.setArchivedAt(now); }
            audits.saveAllAndFlush(batch);
            archivedCount += batch.size();
        }
        auditService.operation(request, "ARCHIVE", "AUDIT", null, null, "SUCCESS", "归档截止 " + cutoff + "，记录数 " + archivedCount);
        return Map.of("archivedCount", archivedCount, "before", cutoff);
    }

    private Specification<OperationAudit> filter(String operator, String action, String resourceType, String result,
                                                   String keyword, String from, String to, boolean archived) {
        LocalDateTime fromTime = parseDate(from, "from");
        LocalDateTime toTime = parseDate(to, "to");
        if (to != null && to.length() == 16 && toTime != null) toTime = toTime.withSecond(59).withNano(999_999_000);
        final LocalDateTime filterFrom = fromTime;
        final LocalDateTime filterTo = toTime;
        if (filterFrom != null && filterTo != null && filterFrom.isAfter(filterTo)) throw new BadRequestException("开始时间不能晚于结束时间");
        return (root, query, builder) -> {
            List<jakarta.persistence.criteria.Predicate> predicates = new ArrayList<>();
            predicates.add(builder.equal(root.get("archived"), archived));
            addEquals(predicates, root, builder, "operator", operator);
            addEquals(predicates, root, builder, "action", action);
            addEquals(predicates, root, builder, "resourceType", resourceType);
            addEquals(predicates, root, builder, "result", result);
            if (filterFrom != null) predicates.add(builder.greaterThanOrEqualTo(root.get("createdAt"), filterFrom));
            if (filterTo != null) predicates.add(builder.lessThanOrEqualTo(root.get("createdAt"), filterTo));
            if (keyword != null && !keyword.isBlank()) {
                String like = "%" + keyword.trim().toLowerCase(Locale.ROOT) + "%";
                predicates.add(builder.or(builder.like(builder.lower(root.get("resourceName")), like),
                        builder.like(builder.lower(root.get("resourceId")), like), builder.like(builder.lower(root.get("message")), like),
                        builder.like(builder.lower(root.get("clientIp")), like)));
            }
            return builder.and(predicates.toArray(new jakarta.persistence.criteria.Predicate[0]));
        };
    }

    private void addEquals(List<jakarta.persistence.criteria.Predicate> predicates,
                           jakarta.persistence.criteria.Root<OperationAudit> root,
                           jakarta.persistence.criteria.CriteriaBuilder builder, String field, String value) {
        if (value != null && !value.isBlank()) predicates.add(builder.equal(builder.lower(root.get(field)), value.trim().toLowerCase(Locale.ROOT)));
    }

    private LocalDateTime parseDate(String value, String label) {
        if (value == null || value.isBlank()) return null;
        try { return LocalDateTime.parse(value); }
        catch (DateTimeParseException ex) { throw new BadRequestException(label + " 必须是 ISO 格式日期时间"); }
    }

    private String clean(String value) { return CsvExportSanitizer.sanitize(value); }

    private Map<String, Object> view(OperationAudit audit) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", audit.getId()); item.put("operator", audit.getOperator()); item.put("role", audit.getRole());
        item.put("action", audit.getAction()); item.put("resourceType", audit.getResourceType()); item.put("resourceId", audit.getResourceId());
        item.put("resourceName", audit.getResourceName()); item.put("result", audit.getResult()); item.put("message", audit.getMessage());
        item.put("clientIp", audit.getClientIp()); item.put("createdAt", audit.getCreatedAt()); item.put("archived", audit.isArchived());
        return item;
    }
}
