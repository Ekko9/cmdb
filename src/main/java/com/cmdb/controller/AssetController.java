package com.cmdb.controller;

import com.cmdb.config.AuditService;
import com.cmdb.config.BadRequestException;
import com.cmdb.config.CsvExportSanitizer;
import com.cmdb.config.IpAddressValidator;
import com.cmdb.config.ResourceNotFoundException;
import com.cmdb.entity.Asset;
import com.cmdb.entity.AssetTypeOption;
import com.cmdb.entity.ImportAudit;
import com.cmdb.entity.ImportFailure;
import com.cmdb.entity.RegionOption;
import com.cmdb.repo.AssetRepository;
import com.cmdb.repo.AssetTypeOptionRepository;
import com.cmdb.repo.AssetChangeRepository;
import com.cmdb.repo.ImportAuditRepository;
import com.cmdb.repo.ImportFailureRepository;
import com.cmdb.repo.ProjectRepository;
import com.cmdb.repo.RegionOptionRepository;
import jakarta.persistence.criteria.JoinType;
import jakarta.servlet.http.HttpServletRequest;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.csv.CSVRecord;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.ss.util.CellRangeAddressList;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.*;

@RestController
@RequestMapping("/api/assets")
public class AssetController {
    private static final int MAX_IMPORT_ROWS = 10_000;
    private static final int MAX_EXPORT_ROWS = 10_000;
    private enum DuplicateStrategy { SKIP, MERGE }
    private static final class ImportValidationContext {
        private final Map<String, String> names = new HashMap<>();
        private final Map<String, String> privateIps = new HashMap<>();
        private final Map<String, String> publicIps = new HashMap<>();
        private int nextFileGroup = 1;

        private String nextGroup() {
            return "FILE:" + nextFileGroup++;
        }
    }
    private final AssetRepository assets;
    private final ProjectRepository projects;
    private final AssetTypeOptionRepository assetTypes;
    private final AssetChangeRepository assetChanges;
    private final ImportAuditRepository importAudits;
    private final ImportFailureRepository importFailures;
    private final RegionOptionRepository regionOptions;
    private final AuditService auditService;

    public AssetController(AssetRepository assets, ProjectRepository projects, AssetTypeOptionRepository assetTypes,
                           AssetChangeRepository assetChanges, ImportAuditRepository importAudits,
                           ImportFailureRepository importFailures, RegionOptionRepository regionOptions,
                           AuditService auditService) {
        this.assets = assets;
        this.projects = projects;
        this.assetTypes = assetTypes;
        this.assetChanges = assetChanges;
        this.importAudits = importAudits;
        this.importFailures = importFailures;
        this.regionOptions = regionOptions;
        this.auditService = auditService;
    }

    @GetMapping
    @Transactional(readOnly = true)
    public Map<String, Object> list(@RequestParam(required = false) Long projectId,
                                    @RequestParam(required = false) String keyword,
                                    @RequestParam(defaultValue = "0") int page,
                                    @RequestParam(defaultValue = "10") int size,
                                    @RequestParam(defaultValue = "updatedAt") String sort,
                                    @RequestParam(defaultValue = "desc") String direction) {
        Pageable pageable = PageRequest.of(Math.max(0, page), Math.min(Math.max(1, size), 100), order(sort, direction));
        Page<Asset> assetPage = assets.findAll(spec(projectId, keyword), pageable);
        List<Map<String, Object>> content = new ArrayList<>();
        for (Asset asset : assetPage.getContent()) content.add(view(asset));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("content", content);
        result.put("page", assetPage.getNumber());
        result.put("size", assetPage.getSize());
        result.put("totalElements", assetPage.getTotalElements());
        result.put("totalPages", assetPage.getTotalPages());
        result.put("sort", sort);
        result.put("direction", direction);
        return result;
    }

    @GetMapping("/{id}")
    @Transactional(readOnly = true)
    public Map<String, Object> detail(@PathVariable Long id) {
        Asset asset = assets.findById(id).orElseThrow(() -> new ResourceNotFoundException("资产不存在"));
        Map<String, Object> result = view(asset);
        List<Map<String, Object>> changes = new ArrayList<>();
        for (com.cmdb.entity.AssetChange change : assetChanges.findTop100ByAssetIdOrderByCreatedAtDescIdDesc(id)) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", change.getId());
            item.put("changeType", change.getChangeType());
            item.put("fieldName", change.getFieldName());
            item.put("oldValue", change.getOldValue());
            item.put("newValue", change.getNewValue());
            item.put("operator", change.getOperator());
            item.put("createdAt", change.getCreatedAt());
            changes.add(item);
        }
        result.put("changes", changes);
        return result;
    }

    @PostMapping
    @Transactional
    public Map<String, Object> create(HttpServletRequest request, @RequestBody Map<String, Object> body) {
        Asset asset = from(body, new Asset());
        ensureAssetUnique(asset);
        asset = saveAsset(asset);
        auditService.assetChange(asset.getId(), asset.getName(), "CREATE", null, null, snapshot(asset), auditService.operator(request));
        auditService.operation(request, "CREATE", "ASSET", asset.getId(), asset.getName(), "SUCCESS", "新增资产");
        return view(asset);
    }

    @PutMapping("/{id}")
    @Transactional
    public Map<String, Object> update(HttpServletRequest request, @PathVariable Long id, @RequestBody Map<String, Object> body) {
        Asset asset = assets.findById(id).orElseThrow(() -> new ResourceNotFoundException("资产不存在"));
        Map<String, String> before = snapshotMap(asset);
        from(body, asset);
        ensureAssetUnique(asset);
        Asset saved = saveAsset(asset);
        recordAssetDiff(saved, before, auditService.operator(request));
        auditService.operation(request, "UPDATE", "ASSET", saved.getId(), saved.getName(), "SUCCESS", "编辑资产");
        return view(saved);
    }

    @DeleteMapping("/{id}")
    @Transactional
    public void delete(HttpServletRequest request, @PathVariable Long id) {
        Asset asset = assets.findById(id).orElseThrow(() -> new ResourceNotFoundException("资产不存在"));
        auditService.assetChange(asset.getId(), asset.getName(), "DELETE", null, snapshot(asset), null, auditService.operator(request));
        assets.delete(asset);
        auditService.operation(request, "DELETE", "ASSET", id, asset.getName(), "SUCCESS", "删除资产");
    }

    @DeleteMapping("/batch")
    @Transactional
    public Map<String, Object> deleteBatch(HttpServletRequest request, @RequestBody List<Long> ids) {
        if (ids == null || ids.isEmpty()) throw new BadRequestException("请先选择要删除的资产");
        if (ids.size() > 100) throw new BadRequestException("单次最多删除 100 项资产");
        Set<Long> uniqueIds = new LinkedHashSet<>(ids);
        if (uniqueIds.contains(null)) throw new BadRequestException("资产 ID 不能为空");
        List<Asset> selected = assets.findAllById(uniqueIds);
        if (selected.size() != uniqueIds.size()) throw new ResourceNotFoundException("部分资产不存在或已被删除，请刷新列表后重试");
        String operator = auditService.operator(request);
        for (Asset asset : selected) {
            auditService.assetChange(asset.getId(), asset.getName(), "DELETE", null, snapshot(asset), null, operator);
            auditService.operation(request, "DELETE", "ASSET", asset.getId(), asset.getName(), "SUCCESS", "批量删除资产");
        }
        assets.deleteAll(selected);
        return Map.of("deletedCount", selected.size());
    }

    @GetMapping("/types")
    @Transactional(readOnly = true)
    public List<String> types() {
        return assetTypeCodes();
    }

    @GetMapping("/regions")
    @Transactional(readOnly = true)
    public List<Map<String, Object>> regions() {
        List<Map<String, Object>> result = new ArrayList<>();
        for (RegionOption option : regionOptions.findByEnabledTrueOrderBySortOrderAscCountryAscRegionAsc()) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", option.getId());
            item.put("continent", option.getContinent());
            item.put("country", option.getCountry());
            item.put("region", option.getRegion());
            item.put("label", regionLabel(option));
            result.add(item);
        }
        return result;
    }

    @PostMapping("/import")
    @Transactional
    public Map<String, Object> importCsv(HttpServletRequest request, @RequestParam("file") MultipartFile file,
                                         @RequestParam(defaultValue = "SKIP") String duplicateStrategy) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("请选择 CSV 或 Excel 文件");
        }
        DuplicateStrategy strategy = duplicateStrategy(duplicateStrategy);
        String filename = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().toLowerCase(Locale.ROOT);
        if (!filename.endsWith(".xlsx") && !filename.endsWith(".csv")) {
            throw new BadRequestException("仅支持 .csv 或 .xlsx 文件");
        }
        ImportAudit audit = new ImportAudit();
        audit.setFilename(file.getOriginalFilename() == null ? "unknown" : file.getOriginalFilename());
        audit.setFileType(filename.endsWith(".xlsx") ? "XLSX" : "CSV");
        audit.setOperator(auditService.operator(request));
        audit = importAudits.save(audit);
        Map<String, Object> result;
        if (filename.endsWith(".xlsx")) result = importExcel(request, file, audit, strategy);
        else result = importCsvFile(request, file, audit, strategy);
        String operationResult = "SUCCESS";
        if (((Number) result.get("count")).intValue() == 0 && ((Number) result.get("skipped")).intValue() > 0) {
            operationResult = "FAILED";
        } else if (((Number) result.get("skipped")).intValue() > 0) {
            operationResult = "PARTIAL";
        }
        auditService.operation(request, "IMPORT", "ASSET", audit.getId(), audit.getFilename(), operationResult,
                "导入成功 " + result.get("count") + " 条，失败 " + result.get("skipped") + " 条，重复策略 " + strategy);
        return result;
    }

    @PostMapping("/import/validate")
    @Transactional(readOnly = true)
    public Map<String, Object> validateImport(@RequestParam("file") MultipartFile file,
                                              @RequestParam(defaultValue = "SKIP") String duplicateStrategy) throws IOException {
        validateImportFile(file);
        DuplicateStrategy strategy = duplicateStrategy(duplicateStrategy);
        String filename = file.getOriginalFilename().toLowerCase(Locale.ROOT);
        return filename.endsWith(".xlsx") ? validateExcel(file, strategy) : validateCsv(file, strategy);
    }

    private void validateImportFile(MultipartFile file) {
        if (file == null || file.isEmpty()) throw new BadRequestException("请选择 CSV 或 Excel 文件");
        String filename = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().toLowerCase(Locale.ROOT);
        if (!filename.endsWith(".xlsx") && !filename.endsWith(".csv")) throw new BadRequestException("仅支持 .csv 或 .xlsx 文件");
    }

    private DuplicateStrategy duplicateStrategy(String value) {
        try { return DuplicateStrategy.valueOf(value == null ? "SKIP" : value.trim().toUpperCase(Locale.ROOT)); }
        catch (IllegalArgumentException ex) { throw new BadRequestException("重复资产策略仅支持 SKIP 或 MERGE"); }
    }

    private Map<String, Object> validateCsv(MultipartFile file, DuplicateStrategy strategy) throws IOException {
        List<Map<String, Object>> errors = new ArrayList<>(); int total = 0, valid = 0;
        ImportValidationContext context = new ImportValidationContext();
        CSVFormat format = CSVFormat.DEFAULT.builder().setSkipHeaderRecord(true).setHeader().setTrim(true).build();
        try (Reader reader = new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8); CSVParser parser = new CSVParser(reader, format)) {
            for (CSVRecord record : parser) {
                total++; if (total > MAX_IMPORT_ROWS) throw new BadRequestException("单次最多导入 " + MAX_IMPORT_ROWS + " 行");
                Map<String, Object> body = csvBody(record); String reason = record.size() < 7 ? "字段不足，至少需要 7 列" : validateCandidate(body, strategy, context);
                if (reason == null) valid++; else if (errors.size() < 200) errors.add(Map.of("rowNumber", record.getRecordNumber() + 1, "reason", reason, "rawData", recordData(record)));
            }
        }
        return validationResult(total, valid, errors, strategy);
    }

    private Map<String, Object> validateExcel(MultipartFile file, DuplicateStrategy strategy) throws IOException {
        List<Map<String, Object>> errors = new ArrayList<>(); int total = 0, valid = 0;
        ImportValidationContext context = new ImportValidationContext();
        try (Workbook workbook = WorkbookFactory.create(file.getInputStream())) {
            Sheet sheet = workbook.getSheetAt(0); if (sheet.getLastRowNum() > MAX_IMPORT_ROWS) throw new BadRequestException("单次最多导入 " + MAX_IMPORT_ROWS + " 行");
            DataFormatter formatter = new DataFormatter();
            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i); if (row == null || isBlank(row, formatter)) continue; total++;
                Map<String, Object> body = new HashMap<>();
                body.put("name", cell(row, 0, formatter)); body.put("assetType", cell(row, 1, formatter)); body.put("environment", cell(row, 2, formatter));
                body.put("privateIp", cell(row, 3, formatter)); body.put("publicIp", cell(row, 4, formatter)); body.put("hostname", cell(row, 5, formatter));
                body.put("projectName", cell(row, 6, formatter)); body.put("status", cell(row, 7, formatter)); body.put("region", cell(row, 8, formatter));
                body.put("description", cell(row, 9, formatter));
                String reason = validateCandidate(body, strategy, context);
                if (reason == null) valid++; else if (errors.size() < 200) errors.add(Map.of("rowNumber", i + 1, "reason", reason, "rawData", rowData(row, formatter)));
            }
        }
        return validationResult(total, valid, errors, strategy);
    }

    private String validateCandidate(Map<String, Object> body, DuplicateStrategy strategy,
                                     ImportValidationContext context) {
        try {
            Asset candidate = from(body, new Asset());
            String nameKey = candidate.getProject().getId() + "|" + normalizeKey(candidate.getName());
            String privateKey = normalizeKey(candidate.getPrivateIp()), publicKey = normalizeKey(candidate.getPublicIp());
            Asset byName = assets.findByNameIgnoreCaseAndProjectId(candidate.getName(), candidate.getProject().getId()).orElse(null);
            Asset byPrivateIp = candidate.getPrivateIp() == null ? null : assets.findByPrivateIpIgnoreCase(candidate.getPrivateIp()).orElse(null);
            Asset byPublicIp = candidate.getPublicIp() == null ? null : assets.findByPublicIpIgnoreCase(candidate.getPublicIp()).orElse(null);

            if (strategy == DuplicateStrategy.SKIP) {
                boolean repeated = context.names.containsKey(nameKey)
                        || privateKey != null && context.privateIps.containsKey(privateKey)
                        || publicKey != null && context.publicIps.containsKey(publicKey);
                if (repeated || byName != null || byPrivateIp != null || byPublicIp != null) {
                    return "检测到重复资产；当前策略为跳过";
                }
                String group = context.nextGroup();
                registerValidationKeys(context, nameKey, privateKey, publicKey, group);
                return null;
            }

            Set<String> groups = new LinkedHashSet<>();
            addValidationGroup(groups, byName, context.names.get(nameKey));
            addValidationGroup(groups, byPrivateIp, privateKey == null ? null : context.privateIps.get(privateKey));
            addValidationGroup(groups, byPublicIp, publicKey == null ? null : context.publicIps.get(publicKey));
            if (groups.size() > 1) return "名称和 IP 分别匹配到不同资产，无法安全合并";
            String group = groups.isEmpty() ? context.nextGroup() : groups.iterator().next();
            registerValidationKeys(context, nameKey, privateKey, publicKey, group);
            return null;
        } catch (BadRequestException exception) {
            return exception.getMessage() == null ? "字段校验失败" : exception.getMessage();
        }
    }

    private void addValidationGroup(Set<String> groups, Asset databaseMatch, String fileMatch) {
        if (databaseMatch != null) groups.add("DB:" + databaseMatch.getId());
        if (fileMatch != null) groups.add(fileMatch);
    }

    private void registerValidationKeys(ImportValidationContext context, String nameKey,
                                        String privateKey, String publicKey, String group) {
        context.names.putIfAbsent(nameKey, group);
        if (privateKey != null) context.privateIps.putIfAbsent(privateKey, group);
        if (publicKey != null) context.publicIps.putIfAbsent(publicKey, group);
    }

    private Map<String, Object> csvBody(CSVRecord record) {
        Map<String, Object> body = new HashMap<>();
        body.put("name", record.size() > 0 ? record.get(0) : ""); body.put("assetType", record.size() > 1 ? record.get(1) : "");
        body.put("environment", record.size() > 2 ? record.get(2) : ""); body.put("privateIp", record.size() > 3 ? record.get(3) : "");
        body.put("publicIp", record.size() > 4 ? record.get(4) : ""); body.put("hostname", record.size() > 5 ? record.get(5) : "");
        body.put("projectName", record.size() > 6 ? record.get(6) : ""); body.put("status", record.size() > 7 ? record.get(7) : "");
        body.put("region", record.size() > 8 ? record.get(8) : ""); body.put("description", record.size() > 9 ? record.get(9) : "");
        return body;
    }

    private Map<String, Object> validationResult(int total, int valid, List<Map<String, Object>> errors, DuplicateStrategy strategy) {
        Map<String, Object> result = new LinkedHashMap<>(); result.put("totalRows", total); result.put("validRows", valid);
        result.put("failedRows", total - valid); result.put("duplicateStrategy", strategy.name()); result.put("errors", errors);
        result.put("errorsTruncated", total - valid > errors.size()); return result;
    }

    private String recordData(CSVRecord record) {
        try { StringWriter writer = new StringWriter(); try (CSVPrinter printer = new CSVPrinter(writer, CSVFormat.DEFAULT)) { printer.printRecord(record); } return writer.toString().trim(); }
        catch (IOException ex) { return record.toString(); }
    }

    private Map<String, Object> failureView(ImportFailure failure) {
        Map<String, Object> row = new LinkedHashMap<>(); row.put("id", failure.getId()); row.put("rowNumber", failure.getRowNumber());
        row.put("reason", failure.getReason()); row.put("rawData", failure.getRawData());
        try (CSVParser parser = CSVParser.parse(failure.getRawData(), CSVFormat.DEFAULT)) {
            if (parser.iterator().hasNext()) {
                CSVRecord data = parser.iterator().next();
                Map<String, Object> asset = new LinkedHashMap<>();
                asset.put("name", value(data, 0)); asset.put("assetType", value(data, 1)); asset.put("environment", value(data, 2));
                asset.put("privateIp", value(data, 3)); asset.put("publicIp", value(data, 4)); asset.put("hostname", value(data, 5));
                asset.put("projectName", value(data, 6)); asset.put("status", value(data, 7)); asset.put("region", value(data, 8));
                asset.put("description", value(data, 9)); row.put("asset", asset);
            }
        } catch (IOException ignored) { }
        return row;
    }

    private String value(CSVRecord row, int index) { return row.size() > index ? row.get(index) : ""; }

    @GetMapping("/imports")
    @Transactional(readOnly = true)
    public Map<String, Object> imports(@RequestParam(defaultValue = "0") int page,
                                       @RequestParam(defaultValue = "10") int size) {
        Page<ImportAudit> auditPage = importAudits.findAllByOrderByCreatedAtDescIdDesc(
                PageRequest.of(Math.max(0, page), Math.min(Math.max(1, size), 100)));
        List<Map<String, Object>> content = new ArrayList<>();
        for (ImportAudit audit : auditPage.getContent()) content.add(importView(audit));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("content", content);
        result.put("page", auditPage.getNumber());
        result.put("size", auditPage.getSize());
        result.put("totalElements", auditPage.getTotalElements());
        result.put("totalPages", auditPage.getTotalPages());
        return result;
    }

    @GetMapping("/imports/{id}/failures.csv")
    @Transactional(readOnly = true)
    public ResponseEntity<byte[]> importFailures(@PathVariable Long id) throws IOException {
        ImportAudit audit = importAudits.findById(id).orElseThrow(() -> new ResourceNotFoundException("导入记录不存在"));
        StringWriter writer = new StringWriter();
        CSVFormat format = CSVFormat.DEFAULT.builder().setHeader("行号", "失败原因", "原始数据").build();
        try (CSVPrinter printer = new CSVPrinter(writer, format)) {
            for (ImportFailure failure : importFailures.findByImportAuditOrderByRowNumberAscIdAsc(audit)) {
                printer.printRecord(failure.getRowNumber(), CsvExportSanitizer.sanitize(failure.getReason()),
                        CsvExportSanitizer.sanitize(failure.getRawData()));
            }
        }
        return fileResponse(("\uFEFF" + writer).getBytes(StandardCharsets.UTF_8),
                "cmdb-import-failures-" + id + ".csv", "text/csv;charset=UTF-8");
    }

    @GetMapping("/imports/{id}/failures")
    @Transactional(readOnly = true)
    public List<Map<String, Object>> importFailureRows(@PathVariable Long id) {
        ImportAudit audit = importAudits.findById(id).orElseThrow(() -> new ResourceNotFoundException("导入记录不存在"));
        List<Map<String, Object>> result = new ArrayList<>();
        for (ImportFailure failure : importFailures.findByImportAuditOrderByRowNumberAscIdAsc(audit)) result.add(failureView(failure));
        return result;
    }

    @PutMapping("/imports/{importId}/failures/{failureId}/retry")
    @Transactional
    public Map<String, Object> retryImportFailure(HttpServletRequest request, @PathVariable Long importId,
                                                   @PathVariable Long failureId, @RequestBody Map<String, Object> body,
                                                   @RequestParam(defaultValue = "MERGE") String duplicateStrategy) {
        ImportAudit importAudit = importAudits.findById(importId).orElseThrow(() -> new ResourceNotFoundException("导入记录不存在"));
        ImportFailure failure = importFailures.findById(failureId).orElseThrow(() -> new ResourceNotFoundException("失败行不存在"));
        if (!failure.getImportAudit().getId().equals(importId)) throw new BadRequestException("失败行不属于该导入记录");
        Asset asset = saveImportedAsset(request, body, new HashSet<>(), new HashSet<>(), new HashSet<>(), duplicateStrategy(duplicateStrategy));
        auditService.assetChange(asset.getId(), asset.getName(), "IMPORT_RETRY", null, null, snapshot(asset), auditService.operator(request));
        importFailures.delete(failure);
        importAudit.setSuccessCount(importAudit.getSuccessCount() + 1);
        importAudit.setFailedCount(Math.max(0, importAudit.getFailedCount() - 1));
        importAudit.setStatus(importAudit.getFailedCount() == 0 ? "SUCCESS" : "PARTIAL");
        importAudit.setCompletedAt(LocalDateTime.now());
        importAudits.save(importAudit);
        auditService.operation(request, "IMPORT_RETRY", "ASSET", asset.getId(), asset.getName(), "SUCCESS", "在线修正导入失败行 " + failure.getRowNumber());
        return Map.of("asset", view(asset), "remainingFailures", importAudit.getFailedCount(), "importStatus", importAudit.getStatus());
    }

    private Map<String, Object> importCsvFile(HttpServletRequest request, MultipartFile file, ImportAudit audit, DuplicateStrategy strategy) throws IOException {
        int imported = 0;
        int skipped = 0;
        int totalRows = 0;
        List<String> errors = new ArrayList<>();
        Set<String> seenNames = new HashSet<>();
        Set<String> seenPrivateIps = new HashSet<>();
        Set<String> seenPublicIps = new HashSet<>();
        CSVFormat format = CSVFormat.DEFAULT.builder().setSkipHeaderRecord(true).setHeader()
                .setTrim(true).build();
        try (Reader reader = new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8);
             CSVParser parser = new CSVParser(reader, format)) {
            for (CSVRecord record : parser) {
                totalRows++;
                if (totalRows > MAX_IMPORT_ROWS) {
                    throw new BadRequestException("单次最多导入 " + MAX_IMPORT_ROWS + " 行");
                }
                long line = record.getRecordNumber() + 1;
                if (record.size() < 7) {
                    skipped++;
                    addImportFailure(audit, (int) line, "字段不足，至少需要 7 列", record.toString(), errors);
                    continue;
                }
                try {
                    Map<String, Object> body = new HashMap<>();
                    body.put("name", record.get(0));
                    body.put("assetType", record.get(1));
                    body.put("environment", record.get(2));
                    body.put("privateIp", record.get(3));
                    body.put("publicIp", record.get(4));
                    body.put("hostname", record.get(5));
                    body.put("projectName", record.get(6));
                    body.put("status", record.size() > 7 ? record.get(7) : "ONLINE");
                    body.put("region", record.size() > 8 ? record.get(8) : null);
                    body.put("description", record.size() > 9 ? record.get(9) : null);
                    Asset asset = saveImportedAsset(request, body, seenNames, seenPrivateIps, seenPublicIps, strategy);
                    auditService.assetChange(asset.getId(), asset.getName(), "IMPORT", null, null, snapshot(asset), auditService.operator(request));
                    imported++;
                } catch (BadRequestException ex) {
                    skipped++;
                    addImportFailure(audit, (int) line, ex.getMessage(), recordData(record), errors);
                }
            }
        }
        finishImport(audit, totalRows, imported, skipped);
        return importResult(audit.getId(), imported, skipped, errors);
    }

    @GetMapping("/export")
    @Transactional(readOnly = true)
    public ResponseEntity<byte[]> exportCsv(@RequestParam(required = false) Long projectId,
                                            @RequestParam(required = false) String keyword) throws IOException {
        List<Asset> all = exportAssets(projectId, keyword);
        StringWriter writer = new StringWriter();
        CSVFormat format = CSVFormat.DEFAULT.builder()
                .setHeader("名称", "类型", "环境", "内网IP", "外网IP", "主机名", "项目名称", "状态", "区域", "描述")
                .build();
        try (CSVPrinter printer = new CSVPrinter(writer, format)) {
            for (Asset asset : all) {
                printer.printRecord(CsvExportSanitizer.sanitize(asset.getName()), CsvExportSanitizer.sanitize(asset.getAssetType()),
                        CsvExportSanitizer.sanitize(asset.getEnvironment()), CsvExportSanitizer.sanitize(asset.getPrivateIp()),
                        CsvExportSanitizer.sanitize(asset.getPublicIp()), CsvExportSanitizer.sanitize(asset.getHostname()),
                        CsvExportSanitizer.sanitize(asset.getProject().getName()), CsvExportSanitizer.sanitize(asset.getStatus()),
                        CsvExportSanitizer.sanitize(asset.getRegion()), CsvExportSanitizer.sanitize(asset.getDescription()));
            }
        }
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType("text/csv;charset=UTF-8"));
        headers.setContentDisposition(ContentDisposition.attachment().filename("cmdb-assets.csv", StandardCharsets.UTF_8).build());
        return ResponseEntity.ok().headers(headers).body(("\uFEFF" + writer).getBytes(StandardCharsets.UTF_8));
    }

    @GetMapping("/export.xlsx")
    @Transactional(readOnly = true)
    public ResponseEntity<byte[]> exportExcel(@RequestParam(required = false) Long projectId,
                                              @RequestParam(required = false) String keyword) throws IOException {
        List<Asset> all = exportAssets(projectId, keyword);
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("资产清单");
            String[] headers = {"名称", "类型", "环境", "内网IP", "外网IP", "主机名", "项目名称", "状态", "区域", "描述"};
            writeHeader(workbook, sheet, headers);
            int rowIndex = 1;
            for (Asset asset : all) {
                Row row = sheet.createRow(rowIndex++);
                writeCell(row, 0, asset.getName());
                writeCell(row, 1, asset.getAssetType());
                writeCell(row, 2, asset.getEnvironment());
                writeCell(row, 3, asset.getPrivateIp());
                writeCell(row, 4, asset.getPublicIp());
                writeCell(row, 5, asset.getHostname());
                writeCell(row, 6, asset.getProject().getName());
                writeCell(row, 7, asset.getStatus());
                writeCell(row, 8, asset.getRegion());
                writeCell(row, 9, asset.getDescription());
            }
            sizeColumns(sheet, headers.length);
            workbook.write(output);
            return fileResponse(output.toByteArray(), "cmdb-assets.xlsx",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        }
    }

    private List<Asset> exportAssets(Long projectId, String keyword) {
        Pageable pageable = PageRequest.of(0, MAX_EXPORT_ROWS + 1, Sort.by(Sort.Direction.DESC, "updatedAt"));
        List<Asset> result = assets.findAll(spec(projectId, keyword), pageable).getContent();
        if (result.size() > MAX_EXPORT_ROWS) {
            throw new BadRequestException("单次最多导出 " + MAX_EXPORT_ROWS + " 条资产，请先缩小筛选范围");
        }
        return result;
    }

    @GetMapping("/template.xlsx")
    @Transactional(readOnly = true)
    public ResponseEntity<byte[]> template() throws IOException {
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("资产导入");
            Sheet options = workbook.createSheet("选项");
            String[] headers = {"名称", "类型", "环境", "内网IP", "外网IP", "主机名", "项目名称", "状态", "区域", "描述"};
            writeHeader(workbook, sheet, headers);
            List<RegionOption> regions = regionOptions.findByEnabledTrueOrderBySortOrderAscCountryAscRegionAsc();
            Row sample = sheet.createRow(1);
            String[] values = {"示例资产-请修改", "SERVER", "PRODUCTION", "10.0.0.10", "203.0.113.10",
                    "example-server", projects.count() == 0 ? "" : projects.findAll().get(0).getName(),
                    "ONLINE", regions.isEmpty() ? "亚洲 / 中国 / 北京" : regionLabel(regions.get(0)), "请替换为实际资产信息"};
            for (int i = 0; i < values.length; i++) writeCell(sample, i, values[i]);

            String[] optionHeaders = {"项目名称", "类型", "环境", "状态", "区域"};
            Row optionHeader = options.createRow(0);
            for (int i = 0; i < optionHeaders.length; i++) writeCell(optionHeader, i, optionHeaders[i]);
            List<com.cmdb.entity.Project> projectList = projects.findAll();
            for (int i = 0; i < projectList.size(); i++) writeCell(optionRow(options, i + 1), 0, projectList.get(i).getName());
            List<String> types = assetTypeCodes();
            String[] environments = {"PRODUCTION", "STAGING", "DEVELOPMENT"};
            String[] statuses = {"ONLINE", "OFFLINE", "MAINTENANCE"};
            for (int i = 0; i < types.size(); i++) writeCell(optionRow(options, i + 1), 1, types.get(i));
            for (int i = 0; i < environments.length; i++) writeCell(optionRow(options, i + 1), 2, environments[i]);
            for (int i = 0; i < statuses.length; i++) writeCell(optionRow(options, i + 1), 3, statuses[i]);
            for (int i = 0; i < regions.size(); i++) {
                RegionOption region = regions.get(i);
                writeCell(optionRow(options, i + 1), 4, regionLabel(region));
            }

            defineName(workbook, "ProjectOptions", "'选项'!$A$2:$A$" + Math.max(2, projectList.size() + 1));
            defineName(workbook, "TypeOptions", "'选项'!$B$2:$B$" + (types.size() + 1));
            defineName(workbook, "EnvironmentOptions", "'选项'!$C$2:$C$" + (environments.length + 1));
            defineName(workbook, "StatusOptions", "'选项'!$D$2:$D$" + (statuses.length + 1));
            defineName(workbook, "RegionOptions", "'选项'!$E$2:$E$" + Math.max(2, regions.size() + 1));
            addDropdown(sheet, "ProjectOptions", 1, 1000, 6);
            addDropdown(sheet, "TypeOptions", 1, 1000, 1);
            addDropdown(sheet, "EnvironmentOptions", 1, 1000, 2);
            addDropdown(sheet, "StatusOptions", 1, 1000, 7);
            addDropdown(sheet, "RegionOptions", 1, 1000, 8);
            sizeColumns(sheet, headers.length);
            sizeColumns(options, optionHeaders.length);
            options.createFreezePane(0, 1);
            // Keep the source values visible so Excel/WPS can resolve the validation lists reliably.
            workbook.write(output);
            return fileResponse(output.toByteArray(), "cmdb-asset-import-template.xlsx",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        }
    }

    private Asset from(Map<String, Object> body, Asset asset) {
        if (body == null) throw new BadRequestException("请求数据不能为空");
        String name = text(body, "name");
        if (name == null) throw new BadRequestException("资产名称不能为空");
        if (name.length() > 100) throw new BadRequestException("资产名称不能超过 100 个字符");
        asset.setName(name);
        String type = text(body, "assetType");
        if (type != null && !assetTypeCodes().contains(type.toUpperCase(Locale.ROOT))) throw new BadRequestException("资产类型无效：" + type);
        asset.setAssetType(type == null ? null : type.toUpperCase(Locale.ROOT));
        asset.setEnvironment(enumValue(body, "environment", Set.of("PRODUCTION", "STAGING", "DEVELOPMENT"), "PRODUCTION"));
        asset.setPrivateIp(ipValue(body, "privateIp"));
        asset.setPublicIp(ipValue(body, "publicIp"));
        asset.setHostname(limitText(text(body, "hostname"), 100, "主机名"));
        asset.setStatus(enumValue(body, "status", Set.of("ONLINE", "OFFLINE", "MAINTENANCE"), "ONLINE"));
        if (asset.getId() == null && text(body, "lifecycleState") != null) {
            asset.setLifecycleState(enumValue(body, "lifecycleState", Set.of("PLANNED", "IN_SERVICE", "RETIRING", "RETIRED"), "IN_SERVICE"));
        }
        asset.setRegion(limitText(text(body, "region"), 100, "区域"));
        asset.setDescription(limitText(text(body, "description"), 500, "描述"));
        String projectName = text(body, "projectName");
        Object projectId = body.get("projectId");
        if (projectName != null) {
            List<com.cmdb.entity.Project> matches = projects.findByNameIgnoreCase(projectName);
            if (matches.isEmpty()) throw new BadRequestException("项目不存在：" + projectName);
            if (matches.size() > 1) throw new BadRequestException("项目名称重复，请先清理重复项目：" + projectName);
            asset.setProject(matches.get(0));
        } else if (projectId != null && !String.valueOf(projectId).trim().isEmpty()) {
            try {
                asset.setProject(projects.findById(Long.valueOf(String.valueOf(projectId).trim()))
                        .orElseThrow(() -> new BadRequestException("项目不存在")));
            } catch (NumberFormatException ex) {
                throw new BadRequestException("项目 ID 无效");
            }
        } else if (asset.getProject() == null) {
            throw new BadRequestException("项目不能为空");
        }
        return asset;
    }

    private String enumValue(Map<String, Object> body, String key, Set<String> allowed, String fallback) {
        String value = text(body, key);
        if (value == null) return fallback;
        value = value.toUpperCase(Locale.ROOT);
        if (!allowed.contains(value)) throw new BadRequestException(key + " 值无效：" + value);
        return value;
    }

    private String ipValue(Map<String, Object> body, String key) {
        String value = text(body, key);
        if (value == null) return null;
        if (!IpAddressValidator.isValid(value)) {
            throw new BadRequestException(key + " 不是有效的 IP 地址");
        }
        return IpAddressValidator.canonicalize(value);
    }

    private String limitText(String value, int max, String label) {
        if (value != null && value.length() > max) throw new BadRequestException(label + "不能超过 " + max + " 个字符");
        return value;
    }

    private Map<String, Object> importExcel(HttpServletRequest request, MultipartFile file, ImportAudit audit, DuplicateStrategy strategy) throws IOException {
        int imported = 0;
        int skipped = 0;
        int totalRows = 0;
        List<String> errors = new ArrayList<>();
        Set<String> seenNames = new HashSet<>();
        Set<String> seenPrivateIps = new HashSet<>();
        Set<String> seenPublicIps = new HashSet<>();
        try (Workbook workbook = WorkbookFactory.create(file.getInputStream())) {
            Sheet sheet = workbook.getSheetAt(0);
            if (sheet.getLastRowNum() > MAX_IMPORT_ROWS) {
                throw new BadRequestException("单次最多导入 " + MAX_IMPORT_ROWS + " 行");
            }
            DataFormatter formatter = new DataFormatter();
            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null || isBlank(row, formatter)) continue;
                totalRows++;
                try {
                    Map<String, Object> body = new HashMap<>();
                    body.put("name", cell(row, 0, formatter));
                    body.put("assetType", cell(row, 1, formatter));
                    body.put("environment", cell(row, 2, formatter));
                    body.put("privateIp", cell(row, 3, formatter));
                    body.put("publicIp", cell(row, 4, formatter));
                    body.put("hostname", cell(row, 5, formatter));
                    body.put("projectName", cell(row, 6, formatter));
                    body.put("status", cell(row, 7, formatter));
                    body.put("region", cell(row, 8, formatter));
                    body.put("description", cell(row, 9, formatter));
                    Asset asset = saveImportedAsset(request, body, seenNames, seenPrivateIps, seenPublicIps, strategy);
                    auditService.assetChange(asset.getId(), asset.getName(), "IMPORT", null, null, snapshot(asset), auditService.operator(request));
                    imported++;
                } catch (BadRequestException ex) {
                    skipped++;
                    addImportFailure(audit, i + 1, ex.getMessage(), rowData(row, formatter), errors);
                }
            }
        }
        finishImport(audit, totalRows, imported, skipped);
        return importResult(audit.getId(), imported, skipped, errors);
    }

    private Asset saveImportedAsset(HttpServletRequest request, Map<String, Object> body,
                                    Set<String> seenNames, Set<String> seenPrivateIps,
                                    Set<String> seenPublicIps, DuplicateStrategy strategy) {
        Asset asset = from(body, new Asset());
        String nameKey = asset.getProject().getId() + "|" + normalizeKey(asset.getName());
        String privateIpKey = normalizeKey(asset.getPrivateIp());
        String publicIpKey = normalizeKey(asset.getPublicIp());

        boolean duplicateInFile = !seenNames.add(nameKey);
        if (duplicateInFile && strategy == DuplicateStrategy.SKIP) {
            throw new BadRequestException("文件内存在重复资产：同一项目下资产名称“" + asset.getName() + "”重复");
        }
        boolean duplicatePrivateIp = privateIpKey != null && !seenPrivateIps.add(privateIpKey);
        if (duplicatePrivateIp && strategy == DuplicateStrategy.SKIP) {
            throw new BadRequestException("文件内存在重复内网 IP：" + asset.getPrivateIp());
        }
        boolean duplicatePublicIp = publicIpKey != null && !seenPublicIps.add(publicIpKey);
        if (duplicatePublicIp && strategy == DuplicateStrategy.SKIP) {
            throw new BadRequestException("文件内存在重复外网 IP：" + asset.getPublicIp());
        }
        Asset existingByName = assets.findByNameIgnoreCaseAndProjectId(asset.getName(), asset.getProject().getId()).orElse(null);
        Asset existingByPrivateIp = asset.getPrivateIp() == null ? null : assets.findByPrivateIpIgnoreCase(asset.getPrivateIp()).orElse(null);
        Asset existingByPublicIp = asset.getPublicIp() == null ? null : assets.findByPublicIpIgnoreCase(asset.getPublicIp()).orElse(null);
        if (strategy == DuplicateStrategy.MERGE && (existingByName != null || existingByPrivateIp != null || existingByPublicIp != null)) {
            Set<Long> matches = new HashSet<>();
            if (existingByName != null) matches.add(existingByName.getId());
            if (existingByPrivateIp != null) matches.add(existingByPrivateIp.getId());
            if (existingByPublicIp != null) matches.add(existingByPublicIp.getId());
            if (matches.size() > 1) throw new BadRequestException("名称、内网 IP 和外网 IP 分别匹配到不同资产，无法安全合并");
            Asset existing = existingByName != null ? existingByName : existingByPrivateIp != null ? existingByPrivateIp : existingByPublicIp;
            mergeNonEmpty(asset, existing, body);
            ensureAssetUnique(existing);
            return assets.save(existing);
        }
        if (existingByName != null) {
            throw new BadRequestException("数据库中已存在同一项目资产：" + asset.getName());
        }
        if (existingByPrivateIp != null) {
            throw new BadRequestException("数据库中已存在内网 IP：" + asset.getPrivateIp());
        }
        if (existingByPublicIp != null) {
            throw new BadRequestException("数据库中已存在外网 IP：" + asset.getPublicIp());
        }
        try {
            return assets.save(asset);
        } catch (RuntimeException exception) {
            seenNames.remove(nameKey);
            if (privateIpKey != null) seenPrivateIps.remove(privateIpKey);
            if (publicIpKey != null) seenPublicIps.remove(publicIpKey);
            throw exception;
        }
    }

    private void mergeNonEmpty(Asset incoming, Asset existing, Map<String, Object> body) {
        if (text(body, "assetType") != null) existing.setAssetType(incoming.getAssetType());
        if (text(body, "environment") != null) existing.setEnvironment(incoming.getEnvironment());
        if (text(body, "privateIp") != null) existing.setPrivateIp(incoming.getPrivateIp());
        if (text(body, "publicIp") != null) existing.setPublicIp(incoming.getPublicIp());
        if (text(body, "hostname") != null) existing.setHostname(incoming.getHostname());
        if (text(body, "region") != null) existing.setRegion(incoming.getRegion());
        if (text(body, "description") != null) existing.setDescription(incoming.getDescription());
        if (text(body, "status") != null) existing.setStatus(incoming.getStatus());
        if (text(body, "lifecycleState") != null) existing.setLifecycleState(incoming.getLifecycleState());
    }

    private String normalizeKey(String value) {
        if (value == null || value.trim().isEmpty()) return null;
        return value.trim().toLowerCase(Locale.ROOT);
    }

    private void ensureAssetUnique(Asset asset) {
        Long currentId = asset.getId();
        Asset sameName = assets.findByNameIgnoreCaseAndProjectId(asset.getName(), asset.getProject().getId()).orElse(null);
        if (sameName != null && !Objects.equals(sameName.getId(), currentId)) {
            throw new BadRequestException("同一项目下已存在资产名称：" + asset.getName());
        }
        if (asset.getPrivateIp() != null) {
            Asset samePrivateIp = assets.findByPrivateIpIgnoreCase(asset.getPrivateIp()).orElse(null);
            if (samePrivateIp != null && !Objects.equals(samePrivateIp.getId(), currentId)) {
                throw new BadRequestException("内网 IP 已被其他资产使用：" + asset.getPrivateIp());
            }
        }
        if (asset.getPublicIp() != null) {
            Asset samePublicIp = assets.findByPublicIpIgnoreCase(asset.getPublicIp()).orElse(null);
            if (samePublicIp != null && !Objects.equals(samePublicIp.getId(), currentId)) {
                throw new BadRequestException("外网 IP 已被其他资产使用：" + asset.getPublicIp());
            }
        }
    }

    private Asset saveAsset(Asset asset) {
        try {
            return assets.saveAndFlush(asset);
        } catch (DataIntegrityViolationException exception) {
            throw new BadRequestException("资产名称或 IP 已存在，请检查后重试");
        }
    }

    private Map<String, Object> importResult(Long importId, int imported, int skipped, List<String> errors) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("importId", importId);
        result.put("count", imported);
        result.put("skipped", skipped);
        result.put("errors", errors);
        return result;
    }

    private void addImportFailure(ImportAudit audit, int rowNumber, String reason, String rawData, List<String> errors) {
        ImportFailure failure = new ImportFailure();
        failure.setImportAudit(audit);
        failure.setRowNumber(rowNumber);
        String failureReason = reason == null ? "导入失败" : reason;
        failure.setReason(failureReason);
        failure.setRawData(rawData == null || rawData.length() <= 2000 ? rawData : rawData.substring(0, 2000));
        importFailures.save(failure);
        errors.add("第 " + rowNumber + " 行：" + failureReason);
    }

    private void finishImport(ImportAudit audit, int totalRows, int imported, int skipped) {
        audit.setTotalRows(totalRows);
        audit.setSuccessCount(imported);
        audit.setFailedCount(skipped);
        audit.setStatus(skipped == 0 ? "SUCCESS" : imported == 0 ? "FAILED" : "PARTIAL");
        audit.setCompletedAt(LocalDateTime.now());
        importAudits.save(audit);
    }

    private String rowData(Row row, DataFormatter formatter) {
        List<String> values = new ArrayList<>();
        for (int i = 0; i < 10; i++) values.add(cell(row, i, formatter));
        try {
            StringWriter writer = new StringWriter();
            try (CSVPrinter printer = new CSVPrinter(writer, CSVFormat.DEFAULT)) { printer.printRecord(values); }
            return writer.toString().trim();
        } catch (IOException ex) { return String.join(",", values); }
    }

    private boolean isBlank(Row row, DataFormatter formatter) {
        for (int i = 0; i < 10; i++) if (!cell(row, i, formatter).isEmpty()) return false;
        return true;
    }

    private String cell(Row row, int index, DataFormatter formatter) {
        Cell cell = row.getCell(index);
        return cell == null ? "" : formatter.formatCellValue(cell).trim();
    }

    private void writeHeader(Workbook workbook, Sheet sheet, String[] headers) {
        Row row = sheet.createRow(0);
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        font.setColor(IndexedColors.WHITE.getIndex());
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        for (int i = 0; i < headers.length; i++) {
            Cell cell = row.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(style);
        }
        sheet.createFreezePane(0, 1);
        sheet.setAutoFilter(new CellRangeAddress(0, 0, 0, headers.length - 1));
    }

    private void writeCell(Row row, int index, String value) {
        row.createCell(index).setCellValue(value == null ? "" : value);
    }

    private Row optionRow(Sheet sheet, int rowIndex) {
        Row row = sheet.getRow(rowIndex);
        return row == null ? sheet.createRow(rowIndex) : row;
    }

    private List<String> assetTypeCodes() {
        List<String> codes = new ArrayList<>();
        for (AssetTypeOption option : assetTypes.findByEnabledTrueOrderBySortOrderAscCodeAsc()) {
            codes.add(option.getCode());
        }
        if (codes.isEmpty()) {
            codes.addAll(Arrays.asList("SERVER", "DATABASE", "NETWORK", "STORAGE", "APPLICATION", "OTHER"));
        }
        return codes;
    }

    private String regionLabel(RegionOption option) {
        return String.join(" / ", option.getContinent(), option.getCountry(), option.getRegion());
    }

    private void addDropdown(Sheet sheet, String formula, int firstRow, int lastRow, int column) {
        DataValidationHelper helper = sheet.getDataValidationHelper();
        DataValidationConstraint constraint = helper.createFormulaListConstraint(formula);
        CellRangeAddressList range = new CellRangeAddressList(firstRow, lastRow, column, column);
        DataValidation validation = helper.createValidation(constraint, range);
        // POI writes the OOXML showDropDown flag with the opposite meaning.
        // Passing true here produces showDropDown="false", which displays the arrow in Excel/WPS.
        validation.setSuppressDropDownArrow(true);
        validation.setShowErrorBox(true);
        sheet.addValidationData(validation);
    }

    private void defineName(Workbook workbook, String name, String formula) {
        Name namedRange = workbook.createName();
        namedRange.setNameName(name);
        namedRange.setRefersToFormula(formula);
    }

    private void sizeColumns(Sheet sheet, int count) {
        // Fixed widths keep workbook generation independent of AWT/font packages on headless servers.
        int[] widths = {24, 16, 16, 20, 20, 22, 24, 16, 30, 42};
        for (int i = 0; i < count; i++) {
            int width = i < widths.length ? widths[i] : 20;
            sheet.setColumnWidth(i, Math.min(width * 256, 255 * 256));
        }
    }

    private ResponseEntity<byte[]> fileResponse(byte[] bytes, String filename, String contentType) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(contentType));
        headers.setContentDisposition(ContentDisposition.attachment().filename(filename, StandardCharsets.UTF_8).build());
        return ResponseEntity.ok().headers(headers).body(bytes);
    }

    private String searchable(Asset asset) {
        return String.join(" ", safe(asset.getName()), safe(asset.getPrivateIp()), safe(asset.getPublicIp()),
                safe(asset.getHostname()), safe(asset.getAssetType()), safe(asset.getEnvironment()),
                safe(asset.getStatus()), safe(asset.getRegion())).toLowerCase(Locale.ROOT);
    }

    private String text(Map<String, Object> body, String key) {
        Object value = body.get(key);
        if (value == null) return null;
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? null : text;
    }

    private String textOr(Map<String, Object> body, String key, String fallback) {
        String value = text(body, key);
        return value == null ? fallback : value;
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private Sort order(String sort, String direction) {
        Set<String> allowed = new HashSet<>(Arrays.asList("name", "assetType", "environment", "status", "region", "updatedAt", "createdAt"));
        String property = allowed.contains(sort) ? sort : "updatedAt";
        Sort.Direction dir = "asc".equalsIgnoreCase(direction) ? Sort.Direction.ASC : Sort.Direction.DESC;
        return Sort.by(dir, property);
    }

    private Specification<Asset> spec(Long projectId, String keyword) {
        return (root, query, builder) -> {
            if (query.getResultType() != Long.class && query.getResultType() != long.class) {
                root.fetch("project", JoinType.LEFT);
                query.distinct(true);
            }
            List<jakarta.persistence.criteria.Predicate> predicates = new ArrayList<>();
            if (projectId != null) predicates.add(builder.equal(root.get("project").get("id"), projectId));
            String q = keyword == null ? "" : keyword.trim().toLowerCase(Locale.ROOT);
            if (!q.isEmpty()) {
                String like = "%" + q + "%";
                predicates.add(builder.or(
                        builder.like(builder.lower(root.get("privateIp")), like),
                        builder.like(builder.lower(root.get("publicIp")), like)
                ));
            }
            return builder.and(predicates.toArray(new jakarta.persistence.criteria.Predicate[0]));
        };
    }

    private void recordAssetDiff(Asset asset, Map<String, String> before, String operator) {
        Map<String, String> after = snapshotMap(asset);
        for (String field : after.keySet()) {
            auditService.assetChange(asset.getId(), asset.getName(), "UPDATE", field, before.get(field), after.get(field), operator);
        }
    }

    private String snapshot(Asset asset) {
        return snapshotMap(asset).toString();
    }

    private Map<String, String> snapshotMap(Asset asset) {
        Map<String, String> result = new LinkedHashMap<>();
        result.put("name", asset.getName());
        result.put("assetType", asset.getAssetType());
        result.put("environment", asset.getEnvironment());
        result.put("privateIp", asset.getPrivateIp());
        result.put("publicIp", asset.getPublicIp());
        result.put("hostname", asset.getHostname());
        result.put("status", asset.getStatus());
        result.put("lifecycleState", asset.getLifecycleState());
        result.put("region", asset.getRegion());
        result.put("description", asset.getDescription());
        result.put("projectName", asset.getProject() == null ? null : asset.getProject().getName());
        return result;
    }

    private Map<String, Object> importView(ImportAudit audit) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", audit.getId());
        result.put("filename", audit.getFilename());
        result.put("fileType", audit.getFileType());
        result.put("status", audit.getStatus());
        result.put("totalRows", audit.getTotalRows());
        result.put("successCount", audit.getSuccessCount());
        result.put("failedCount", audit.getFailedCount());
        result.put("operator", audit.getOperator());
        result.put("createdAt", audit.getCreatedAt());
        result.put("completedAt", audit.getCompletedAt());
        return result;
    }

    private Map<String, Object> view(Asset asset) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", asset.getId());
        result.put("name", asset.getName());
        result.put("assetType", asset.getAssetType());
        result.put("environment", asset.getEnvironment());
        result.put("privateIp", asset.getPrivateIp());
        result.put("publicIp", asset.getPublicIp());
        result.put("hostname", asset.getHostname());
        result.put("status", asset.getStatus());
        result.put("lifecycleState", asset.getLifecycleState());
        result.put("region", asset.getRegion());
        result.put("description", asset.getDescription());
        result.put("projectId", asset.getProject() == null ? null : asset.getProject().getId());
        result.put("projectName", asset.getProject() == null ? null : asset.getProject().getName());
        result.put("createdAt", asset.getCreatedAt());
        result.put("updatedAt", asset.getUpdatedAt());
        return result;
    }
}
