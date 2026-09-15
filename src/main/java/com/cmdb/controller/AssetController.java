package com.cmdb.controller;

import com.cmdb.entity.Asset;
import com.cmdb.entity.AssetTypeOption;
import com.cmdb.repo.AssetRepository;
import com.cmdb.repo.AssetTypeOptionRepository;
import com.cmdb.repo.ProjectRepository;
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
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

@RestController
@RequestMapping("/api/assets")
public class AssetController {
    private final AssetRepository assets;
    private final ProjectRepository projects;
    private final AssetTypeOptionRepository assetTypes;

    public AssetController(AssetRepository assets, ProjectRepository projects, AssetTypeOptionRepository assetTypes) {
        this.assets = assets;
        this.projects = projects;
        this.assetTypes = assetTypes;
    }

    @GetMapping
    @Transactional(readOnly = true)
    public List<Map<String, Object>> list(@RequestParam(required = false) Long projectId,
                                          @RequestParam(required = false) String keyword) {
        List<Asset> all = projectId == null ? assets.findAll() : assets.findByProjectId(projectId);
        String query = keyword == null ? "" : keyword.trim().toLowerCase(Locale.ROOT);
        List<Map<String, Object>> result = new ArrayList<>();
        for (Asset asset : all) {
            if (query.isEmpty() || searchable(asset).contains(query)) {
                result.add(view(asset));
            }
        }
        return result;
    }

    @PostMapping
    @Transactional
    public Map<String, Object> create(@RequestBody Map<String, Object> body) {
        return view(assets.save(from(body, new Asset())));
    }

    @PutMapping("/{id}")
    @Transactional
    public Map<String, Object> update(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        Asset asset = assets.findById(id).orElseThrow(() -> new RuntimeException("资产不存在"));
        return view(assets.save(from(body, asset)));
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        Asset asset = assets.findById(id).orElseThrow(() -> new RuntimeException("资产不存在"));
        assets.delete(asset);
    }

    @GetMapping("/types")
    @Transactional(readOnly = true)
    public List<String> types() {
        return assetTypeCodes();
    }

    @PostMapping("/import")
    @Transactional
    public Map<String, Object> importCsv(@RequestParam("file") MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new RuntimeException("请选择 CSV 或 Excel 文件");
        }
        String filename = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().toLowerCase(Locale.ROOT);
        if (filename.endsWith(".xlsx")) return importExcel(file);
        if (!filename.endsWith(".csv")) throw new RuntimeException("仅支持 .csv 或 .xlsx 文件");
        return importCsvFile(file);
    }

    private Map<String, Object> importCsvFile(MultipartFile file) throws IOException {
        int imported = 0;
        int skipped = 0;
        List<String> errors = new ArrayList<>();
        CSVFormat format = CSVFormat.DEFAULT.builder().setSkipHeaderRecord(true).setHeader()
                .setTrim(true).build();
        try (Reader reader = new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8);
             CSVParser parser = new CSVParser(reader, format)) {
            for (CSVRecord record : parser) {
                long line = record.getRecordNumber() + 1;
                if (record.size() < 7) {
                    skipped++;
                    errors.add("第 " + line + " 行字段不足，至少需要 7 列");
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
                    assets.save(from(body, new Asset()));
                    imported++;
                } catch (RuntimeException ex) {
                    skipped++;
                    errors.add("第 " + line + " 行：" + ex.getMessage());
                }
            }
        }
        return importResult(imported, skipped, errors);
    }

    @GetMapping("/export")
    @Transactional(readOnly = true)
    public ResponseEntity<byte[]> exportCsv(@RequestParam(required = false) Long projectId,
                                            @RequestParam(required = false) String keyword) throws IOException {
        List<Asset> all = projectId == null ? assets.findAll() : assets.findByProjectId(projectId);
        String query = keyword == null ? "" : keyword.trim().toLowerCase(Locale.ROOT);
        StringWriter writer = new StringWriter();
        CSVFormat format = CSVFormat.DEFAULT.builder()
                .setHeader("名称", "类型", "环境", "内网IP", "外网IP", "主机名", "项目名称", "状态", "区域", "描述")
                .build();
        try (CSVPrinter printer = new CSVPrinter(writer, format)) {
            for (Asset asset : all) {
                if (!query.isEmpty() && !searchable(asset).contains(query)) continue;
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
        List<Asset> all = projectId == null ? assets.findAll() : assets.findByProjectId(projectId);
        String query = keyword == null ? "" : keyword.trim().toLowerCase(Locale.ROOT);
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("资产清单");
            String[] headers = {"名称", "类型", "环境", "内网IP", "外网IP", "主机名", "项目名称", "状态", "区域", "描述"};
            writeHeader(workbook, sheet, headers);
            int rowIndex = 1;
            for (Asset asset : all) {
                if (!query.isEmpty() && !searchable(asset).contains(query)) continue;
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
            Row sample = sheet.createRow(1);
            String[] values = {"示例资产-请修改", "SERVER", "PRODUCTION", "10.0.0.10", "203.0.113.10",
                    "example-server", projects.count() == 0 ? "" : projects.findAll().get(0).getName(),
                    "ONLINE", "杭州", "请替换为实际资产信息"};
            for (int i = 0; i < values.length; i++) writeCell(sample, i, values[i]);

            String[] optionHeaders = {"项目名称", "类型", "环境", "状态"};
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

            defineName(workbook, "ProjectOptions", "'选项'!$A$2:$A$" + Math.max(2, projectList.size() + 1));
            defineName(workbook, "TypeOptions", "'选项'!$B$2:$B$" + (types.size() + 1));
            defineName(workbook, "EnvironmentOptions", "'选项'!$C$2:$C$" + (environments.length + 1));
            defineName(workbook, "StatusOptions", "'选项'!$D$2:$D$" + (statuses.length + 1));
            addDropdown(sheet, "ProjectOptions", 1, 1000, 6);
            addDropdown(sheet, "TypeOptions", 1, 1000, 1);
            addDropdown(sheet, "EnvironmentOptions", 1, 1000, 2);
            addDropdown(sheet, "StatusOptions", 1, 1000, 7);
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

    private Map<String, Object> importExcel(MultipartFile file) throws IOException {
        int imported = 0;
        int skipped = 0;
        List<String> errors = new ArrayList<>();
        try (Workbook workbook = WorkbookFactory.create(file.getInputStream())) {
            Sheet sheet = workbook.getSheetAt(0);
            DataFormatter formatter = new DataFormatter();
            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null || isBlank(row, formatter)) continue;
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
                    assets.save(from(body, new Asset()));
                    imported++;
                } catch (RuntimeException ex) {
                    skipped++;
                    errors.add("第 " + (i + 1) + " 行：" + ex.getMessage());
                }
            }
        }
        return importResult(imported, skipped, errors);
    }

    private Map<String, Object> importResult(int imported, int skipped, List<String> errors) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("count", imported);
        result.put("skipped", skipped);
        result.put("errors", errors);
        return result;
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
