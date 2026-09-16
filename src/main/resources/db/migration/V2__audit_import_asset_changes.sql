-- Audit, import audit, and asset change history.

CREATE TABLE IF NOT EXISTS `cmdb_asset_change` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `asset_id` BIGINT NOT NULL,
  `asset_name` VARCHAR(100) NOT NULL,
  `change_type` VARCHAR(30) NOT NULL,
  `field_name` VARCHAR(50) NULL,
  `old_value` VARCHAR(1000) NULL,
  `new_value` VARCHAR(1000) NULL,
  `operator` VARCHAR(50) NULL,
  `created_at` DATETIME(6) NULL,
  PRIMARY KEY (`id`),
  KEY `idx_asset_change_asset_time` (`asset_id`, `created_at`),
  KEY `idx_asset_change_type_time` (`change_type`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `cmdb_import_audit` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `filename` VARCHAR(255) NOT NULL,
  `file_type` VARCHAR(20) NULL,
  `status` VARCHAR(30) NOT NULL,
  `total_rows` INT NOT NULL DEFAULT 0,
  `success_count` INT NOT NULL DEFAULT 0,
  `failed_count` INT NOT NULL DEFAULT 0,
  `operator` VARCHAR(50) NULL,
  `created_at` DATETIME(6) NULL,
  `completed_at` DATETIME(6) NULL,
  PRIMARY KEY (`id`),
  KEY `idx_import_audit_time` (`created_at`),
  KEY `idx_import_audit_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `cmdb_import_failure` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `import_id` BIGINT NOT NULL,
  `row_number` INT NOT NULL,
  `reason` VARCHAR(1000) NOT NULL,
  `raw_data` VARCHAR(2000) NULL,
  PRIMARY KEY (`id`),
  KEY `idx_import_failure_import_id` (`import_id`),
  CONSTRAINT `fk_import_failure_audit`
    FOREIGN KEY (`import_id`) REFERENCES `cmdb_import_audit` (`id`)
    ON UPDATE CASCADE
    ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `cmdb_operation_audit` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `operator` VARCHAR(50) NULL,
  `role` VARCHAR(30) NULL,
  `action` VARCHAR(50) NOT NULL,
  `resource_type` VARCHAR(50) NOT NULL,
  `resource_id` VARCHAR(80) NULL,
  `resource_name` VARCHAR(200) NULL,
  `result` VARCHAR(30) NOT NULL,
  `message` VARCHAR(1000) NULL,
  `client_ip` VARCHAR(64) NULL,
  `created_at` DATETIME(6) NULL,
  PRIMARY KEY (`id`),
  KEY `idx_operation_audit_time` (`created_at`),
  KEY `idx_operation_audit_resource` (`resource_type`, `resource_id`),
  KEY `idx_operation_audit_operator` (`operator`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
