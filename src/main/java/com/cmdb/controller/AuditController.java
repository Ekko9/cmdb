package com.cmdb.controller;

import com.cmdb.config.AuditArchiveService;
import com.cmdb.config.AuditService;
import com.cmdb.config.BadRequestException;
import com.cmdb.config.CsvExportSanitizer;
import com.cmdb.entity.OperationAudit;
import com.cmdb.entity.OperationAuditArchive;
import com.cmdb.repo.OperationAuditArchiveRepository;
import com.cmdb.repo.OperationAuditRepository;
import jakarta.persistence.criteria.Root;
import jakarta.servlet.http.HttpServletRequest;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
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
    private final OperationAuditArchiveRepository archives;
    private final AuditArchiveService archiveService;
    private final AuditService auditService;

    public AuditController(OperationAuditRepository audits, OperationAuditArchiveRepository archives,
                           AuditArchiveService archiveService, AuditService auditService) {
        this.audits = audits;
        this.archives = archives;
        this.archiveService = archiveService;
        this.auditService = auditService;
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
        PageRequest pageable = PageRequest.of(Math.max(0, page), Math.min(Math.max(1, size), 100),
                Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by(Sort.Direction.DESC, "id")));
        if (archived) {
            Page<OperationAuditArchive> resultPage = archives.findAll(
                    filter(operator, action, resourceType, result, keyword, from, to, null), pageable);
            return page(resultPage.map(this::row));
        }
        Page<OperationAudit> resultPage = audits.findAll(
                filter(operator, action, resourceType, result, keyword, from, to, false), pageable);
        return page(resultPage.map(this::row));
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
        PageRequest pageable = PageRequest.of(0, MAX_EXPORT_ROWS + 1,
                Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by(Sort.Direction.DESC, "id")));
        List<AuditRow> rows;
        if (archived) {
            rows = archives.findAll(filter(operator, action, resourceType, result, keyword, from, to, null), pageable)
                    .map(this::row).getContent();
        } else {
            rows = audits.findAll(filter(operator, action, resourceType, result, keyword, from, to, false), pageable)
                    .map(this::row).getContent();
        }
        if (rows.size() > MAX_EXPORT_ROWS) {
            throw new BadRequestException("单次最多导出 " + MAX_EXPORT_ROWS + " 条审计记录，请缩小筛选范围");
        }
        StringWriter writer = new StringWriter();
        try (CSVPrinter printer = new CSVPrinter(writer, CSVFormat.DEFAULT.builder()
                .setHeader("时间", "操作人", "角色", "动作", "资源类型", "资源 ID", "资源名称", "结果", "说明", "来源 IP", "已归档").build())) {
            for (AuditRow row : rows) {
                printer.printRecord(row.createdAt(), clean(row.operator()), clean(row.role()), row.action(), row.resourceType(),
                        clean(row.resourceId()), clean(row.resourceName()), row.result(), clean(row.message()),
                        clean(row.clientIp()), row.archived() ? "是" : "否");
            }
        }
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType("text/csv;charset=UTF-8"));
        headers.setContentDisposition(ContentDisposition.attachment()
                .filename("cmdb-operation-audits.csv", StandardCharsets.UTF_8).build());
        return ResponseEntity.ok().headers(headers).body(("\uFEFF" + writer).getBytes(StandardCharsets.UTF_8));
    }

    @PostMapping("/archive")
    public Map<String, Object> archive(HttpServletRequest request, @RequestParam String before) {
        LocalDateTime cutoff = parseDate(before, "before");
        if (cutoff.isAfter(LocalDateTime.now())) throw new BadRequestException("归档截止时间不能晚于当前时间");
        LocalDateTime archivedAt = LocalDateTime.now();
        int archivedCount = 0;
        while (true) {
            int count = archiveService.archiveBatch(cutoff, archivedAt);
            archivedCount += count;
            if (count == 0) break;
        }
        auditService.operation(request, "ARCHIVE", "AUDIT", null, null, "SUCCESS",
                "归档截止 " + cutoff + "，记录数 " + archivedCount);
        return Map.of("archivedCount", archivedCount, "before", cutoff);
    }

    private <T> Specification<T> filter(String operator, String action, String resourceType, String result,
                                        String keyword, String from, String to, Boolean archived) {
        LocalDateTime fromTime = parseDate(from, "from");
        LocalDateTime toTime = parseDate(to, "to");
        if (to != null && to.length() == 16 && toTime != null) toTime = toTime.withSecond(59).withNano(999_999_000);
        final LocalDateTime filterFrom = fromTime;
        final LocalDateTime filterTo = toTime;
        if (filterFrom != null && filterTo != null && filterFrom.isAfter(filterTo)) {
            throw new BadRequestException("开始时间不能晚于结束时间");
        }
        return (root, query, builder) -> {
            List<jakarta.persistence.criteria.Predicate> predicates = new ArrayList<>();
            if (archived != null) predicates.add(builder.equal(root.get("archived"), archived));
            addEquals(predicates, root, builder, "operator", operator);
            addEquals(predicates, root, builder, "action", action);
            addEquals(predicates, root, builder, "resourceType", resourceType);
            addEquals(predicates, root, builder, "result", result);
            if (filterFrom != null) predicates.add(builder.greaterThanOrEqualTo(root.get("createdAt"), filterFrom));
            if (filterTo != null) predicates.add(builder.lessThanOrEqualTo(root.get("createdAt"), filterTo));
            if (keyword != null && !keyword.isBlank()) {
                String like = "%" + keyword.trim().toLowerCase(Locale.ROOT) + "%";
                predicates.add(builder.or(builder.like(builder.lower(root.get("resourceName")), like),
                        builder.like(builder.lower(root.get("resourceId")), like),
                        builder.like(builder.lower(root.get("message")), like),
                        builder.like(builder.lower(root.get("clientIp")), like)));
            }
            return builder.and(predicates.toArray(new jakarta.persistence.criteria.Predicate[0]));
        };
    }

    private <T> void addEquals(List<jakarta.persistence.criteria.Predicate> predicates, Root<T> root,
                               jakarta.persistence.criteria.CriteriaBuilder builder, String field, String value) {
        if (value != null && !value.isBlank()) {
            predicates.add(builder.equal(builder.lower(root.get(field)), value.trim().toLowerCase(Locale.ROOT)));
        }
    }

    private LocalDateTime parseDate(String value, String label) {
        if (value == null || value.isBlank()) return null;
        try {
            return LocalDateTime.parse(value);
        } catch (DateTimeParseException exception) {
            throw new BadRequestException(label + " 必须是 ISO 格式日期时间");
        }
    }

    private Map<String, Object> page(Page<AuditRow> resultPage) {
        List<Map<String, Object>> content = new ArrayList<>();
        for (AuditRow row : resultPage.getContent()) content.add(view(row));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("content", content);
        result.put("page", resultPage.getNumber());
        result.put("size", resultPage.getSize());
        result.put("totalElements", resultPage.getTotalElements());
        result.put("totalPages", resultPage.getTotalPages());
        return result;
    }

    private AuditRow row(OperationAudit audit) {
        return new AuditRow(audit.getId(), audit.getOperator(), audit.getRole(), audit.getAction(), audit.getResourceType(),
                audit.getResourceId(), audit.getResourceName(), audit.getResult(), audit.getMessage(), audit.getClientIp(),
                audit.getCreatedAt(), false);
    }

    private AuditRow row(OperationAuditArchive audit) {
        return new AuditRow(audit.getId(), audit.getOperator(), audit.getRole(), audit.getAction(), audit.getResourceType(),
                audit.getResourceId(), audit.getResourceName(), audit.getResult(), audit.getMessage(), audit.getClientIp(),
                audit.getCreatedAt(), true);
    }

    private Map<String, Object> view(AuditRow audit) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", audit.id());
        item.put("operator", audit.operator());
        item.put("role", audit.role());
        item.put("action", audit.action());
        item.put("resourceType", audit.resourceType());
        item.put("resourceId", audit.resourceId());
        item.put("resourceName", audit.resourceName());
        item.put("result", audit.result());
        item.put("message", audit.message());
        item.put("clientIp", audit.clientIp());
        item.put("createdAt", audit.createdAt());
        item.put("archived", audit.archived());
        return item;
    }

    private String clean(String value) {
        return CsvExportSanitizer.sanitize(value);
    }

    private record AuditRow(Long id, String operator, String role, String action, String resourceType,
                            String resourceId, String resourceName, String result, String message,
                            String clientIp, LocalDateTime createdAt, boolean archived) {
    }
}
