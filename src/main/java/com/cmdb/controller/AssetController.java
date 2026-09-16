package com.cmdb.controller;

import com.cmdb.config.AuditService;
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
        Asset asset = assets.findById(id).orElseThrow(() -> new RuntimeException("资产不存在"));
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
        Asset asset = assets.save(from(body, new Asset()));
        auditService.assetChange(asset.getId(), asset.getName(), "CREATE", null, null, snapshot(asset), auditService.operator(request));
        auditService.operation(request, "CREATE", "ASSET", asset.getId(), asset.getName(), "SUCCESS", "新增资产");
        return view(asset);
    }

    @PutMapping("/{id}")
    @Transactional
    public Map<String, Object> update(HttpServletRequest request, @PathVariable Long id, @RequestBody Map<String, Object> body) {
        Asset asset = assets.findById(id).orElseThrow(() -> new RuntimeException("资产不存在"));
        Map<String, String> before = snapshotMap(asset);
        Asset saved = assets.save(from(body, asset));
        recordAssetDiff(saved, before, auditService.operator(request));
        auditService.operation(request, "UPDATE", "ASSET", saved.getId(), saved.getName(), "SUCCESS", "编辑资产");
        return view(saved);
    }

    @DeleteMapping("/{id}")
    @Transactional
    public void delete(HttpServletRequest request, @PathVariable Long id) {
        Asset asset = assets.findById(id).orElseThrow(() -> new RuntimeException("资产不存在"));
        auditService.assetChange(asset.getId(), asset.getName(), "DELETE", null, snapshot(asset), null, auditService.operator(request));
        assets.delete(asset);
        auditService.operation(request, "DELETE", "ASSET", id, asset.getName(), "SUCCESS", "删除资产");
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
    public Map<String, Object> importCsv(HttpServletRequest request, @RequestParam("file") MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new RuntimeException("请选择 CSV 或 Excel 文件");
        }
        String filename = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().toLowerCase(Locale.ROOT);
        ImportAudit audit = new ImportAudit();
        audit.setFilename(file.getOriginalFilename() == null ? "unknown" : file.getOriginalFilename());
        audit.setFileType(filename.endsWith(".xlsx") ? "XLSX" : filename.endsWith(".csv") ? "CSV" : "UNKNOWN");
        audit.setOperator(auditService.operator(request));
        audit = importAudits.save(audit);
        Map<String, Object> result;
        if (filename.endsWith(".xlsx")) result = importExcel(request, file, audit);
        else {
            if (!filename.endsWith(".csv")) throw new RuntimeException("仅支持 .csv 或 .xlsx 文件");
            result = importCsvFile(request, file, audit);
        }
        auditService.operation(request, "IMPORT", "ASSET", audit.getId(), audit.getFilename(), "SUCCESS",
                "导入成功 " + result.get("count") + " 条，失败 " + result.get("skipped") + " 条");
        return result;
    }

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
        ImportAudit audit = importAudits.findById(id).orElseThrow(() -> new RuntimeException("导入记录不存在"));
        StringWriter writer = new StringWriter();
        CSVFormat format = CSVFormat.DEFAULT.builder().setHeader("行号", "失败原因", "原始数据").build();
        try (CSVPrinter printer = new CSVPrinter(writer, format)) {
            for (ImportFailure failure : importFailures.findByImportAuditOrderByRowNumberAscIdAsc(audit)) {
                printer.printRecord(failure.getRowNumber(), failure.getReason(), failure.getRawData());
            }
        }
        return fileResponse(("\uFEFF" + writer).getBytes(StandardCharsets.UTF_8),
                "cmdb-import-failures-" + id + ".csv", "text/csv;charset=UTF-8");
    }

    private Map<String, Object> importCsvFile(HttpServletRequest request, MultipartFile file, ImportAudit audit) throws IOException {
        int imported = 0;
        int skipped = 0;
        int totalRows = 0;
        List<String> errors = new ArrayList<>();
        CSVFormat format = CSVFormat.DEFAULT.builder().setSkipHeaderRecord(true).setHeader()
                .setTrim(true).build();
        try (Reader reader = new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8);
             CSVParser parser = new CSVParser(reader, format)) {
            for (CSVRecord record : parser) {
                totalRows++;
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
                    Asset asset = assets.save(from(body, new Asset()));
                    auditService.assetChange(asset.getId(), asset.getName(), "IMPORT", null, null, snapshot(asset), auditService.operator(request));
                    imported++;
                } catch (RuntimeException ex) {
                    skipped++;
                    addImportFailure(audit, (int) line, ex.getMessage(), record.toString(), errors);
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
        List<Asset> all = assets.findAll(spec(projectId, keyword), Sort.by(Sort.Direction.DESC, "updatedAt"));
        StringWriter writer = new StringWriter();
        CSVFormat format = CSVFormat.DEFAULT.builder()
                .setHeader("名称", "类型", "环境", "内网IP", "外网IP", "主机名", "项目名称", "状态", "区域", "描述")
                .build();
        try (CSVPrinter printer = new CSVPrinter(writer, format)) {
            for (Asset asset : all) {
                printer.printRecord(asset.getName(), asset.getAssetType(), asset.getEnvironment(),
                        asset.getPrivateIp(), asset.getPublicIp(), asset.getHostname(),
                        asset.getProject().getName(), asset.getStatus(), asset.getRegion(), asset.getDescription());
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
        List<Asset> all = assets.findAll(spec(projectId, keyword), Sort.by(Sort.Direction.DESC, "updatedAt"));
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
            // 普通隐藏比 VERY_HIDDEN 对 Excel/WPS 的数据验证兼容性更好。
            workbook.setSheetVisibility(workbook.getSheetIndex(options), SheetVisibility.HIDDEN);
            workbook.write(output);
            return fileResponse(output.toByteArray(), "cmdb-asset-import-template.xlsx",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        }
    }

    private Asset from(Map<String, Object> body, Asset asset) {
        String name = text(body, "name");
        if (name == null) throw new RuntimeException("资产名称不能为空");
        asset.setName(name);
        asset.setAssetType(text(body, "assetType"));
        asset.setEnvironment(text(body, "environment"));
        asset.setPrivateIp(text(body, "privateIp"));
        asset.setPublicIp(text(body, "publicIp"));
        asset.setHostname(text(body, "hostname"));
        asset.setStatus(textOr(body, "status", "ONLINE"));
        asset.setRegion(text(body, "region"));
        asset.setDescription(text(body, "description"));
        String projectName = text(body, "projectName");
        Object projectId = body.get("projectId");
        if (projectName != null) {
            List<com.cmdb.entity.Project> matches = projects.findByNameIgnoreCase(projectName);
            if (matches.isEmpty()) throw new RuntimeException("项目不存在：" + projectName);
            if (matches.size() > 1) throw new RuntimeException("项目名称重复，请先清理重复项目：" + projectName);
            asset.setProject(matches.get(0));
        } else if (projectId != null && !String.valueOf(projectId).trim().isEmpty()) {
            try {
                asset.setProject(projects.findById(Long.valueOf(String.valueOf(projectId).trim()))
                        .orElseThrow(() -> new RuntimeException("项目不存在")));
            } catch (NumberFormatException ex) {
                throw new RuntimeException("项目 ID 无效");
            }
        } else if (asset.getProject() == null) {
            throw new RuntimeException("项目不能为空");
        }
        return asset;
    }

    private Map<String, Object> importExcel(HttpServletRequest request, MultipartFile file, ImportAudit audit) throws IOException {
        int imported = 0;
        int skipped = 0;
        int totalRows = 0;
        List<String> errors = new ArrayList<>();
        try (Workbook workbook = WorkbookFactory.create(file.getInputStream())) {
            Sheet sheet = workbook.getSheetAt(0);
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
                    Asset asset = assets.save(from(body, new Asset()));
                    auditService.assetChange(asset.getId(), asset.getName(), "IMPORT", null, null, snapshot(asset), auditService.operator(request));
                    imported++;
                } catch (RuntimeException ex) {
                    skipped++;
                    addImportFailure(audit, i + 1, ex.getMessage(), rowData(row, formatter), errors);
                }
            }
        }
        finishImport(audit, totalRows, imported, skipped);
        return importResult(audit.getId(), imported, skipped, errors);
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
        failure.setReason(reason == null ? "导入失败" : reason);
        failure.setRawData(rawData == null || rawData.length() <= 2000 ? rawData : rawData.substring(0, 2000));
        importFailures.save(failure);
        errors.add("第 " + rowNumber + " 行：" + failure.getReason());
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
        return String.join(",", values);
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
        // OOXML 的 showDropDown 语义是反向的：false 才表示显示下拉箭头。
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
        for (int i = 0; i < count; i++) sheet.autoSizeColumn(i);
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
        result.put("region", asset.getRegion());
        result.put("description", asset.getDescription());
        result.put("projectId", asset.getProject() == null ? null : asset.getProject().getId());
        result.put("projectName", asset.getProject() == null ? null : asset.getProject().getName());
        result.put("createdAt", asset.getCreatedAt());
        result.put("updatedAt", asset.getUpdatedAt());
        return result;
    }
}
