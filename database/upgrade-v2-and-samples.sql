-- Orbit CMDB V2 手工升级和样例数据脚本
-- 请使用有权限的账号连接 MySQL 后执行。
-- 脚本可重复执行，不会重复创建表或重复插入同名样例资产。

CREATE DATABASE IF NOT EXISTS `cmdb`
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;

USE `cmdb`;

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
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci;

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
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci;

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
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci;

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
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci;

-- 确保示例使用的项目存在。
INSERT INTO `cmdb_project` (`name`, `code`, `owner`, `description`)
SELECT '演示项目', 'DEMO', '系统管理员', 'CMDB 功能演示项目'
WHERE NOT EXISTS (
  SELECT 1 FROM `cmdb_project` WHERE `code` = 'DEMO'
);

-- 插入 3 条资产样例。重复执行时按名称和项目跳过。
INSERT INTO `cmdb_asset`
  (`name`, `asset_type`, `environment`, `private_ip`, `public_ip`,
   `hostname`, `status`, `region`, `description`, `project_id`, `created_at`, `updated_at`)
SELECT
  'demo-web-01', 'SERVER', 'PRODUCTION', '10.20.0.11', '203.0.113.11',
  'demo-web-01', 'ONLINE', '杭州', '演示 Web 服务器',
  p.`id`, NOW(6), NOW(6)
FROM `cmdb_project` p
WHERE p.`code` = 'DEMO'
  AND NOT EXISTS (
    SELECT 1 FROM `cmdb_asset` a
    WHERE a.`name` = 'demo-web-01' AND a.`project_id` = p.`id`
  );

INSERT INTO `cmdb_asset`
  (`name`, `asset_type`, `environment`, `private_ip`, `public_ip`,
   `hostname`, `status`, `region`, `description`, `project_id`, `created_at`, `updated_at`)
SELECT
  'demo-db-01', 'DATABASE', 'PRODUCTION', '10.20.0.21', NULL,
  'demo-db-01', 'ONLINE', '杭州', '演示数据库服务器',
  p.`id`, NOW(6), NOW(6)
FROM `cmdb_project` p
WHERE p.`code` = 'DEMO'
  AND NOT EXISTS (
    SELECT 1 FROM `cmdb_asset` a
    WHERE a.`name` = 'demo-db-01' AND a.`project_id` = p.`id`
  );

INSERT INTO `cmdb_asset`
  (`name`, `asset_type`, `environment`, `private_ip`, `public_ip`,
   `hostname`, `status`, `region`, `description`, `project_id`, `created_at`, `updated_at`)
SELECT
  'demo-network-01', 'NETWORK', 'STAGING', '10.20.0.31', NULL,
  'demo-network-01', 'MAINTENANCE', '上海', '演示网络设备',
  p.`id`, NOW(6), NOW(6)
FROM `cmdb_project` p
WHERE p.`code` = 'DEMO'
  AND NOT EXISTS (
    SELECT 1 FROM `cmdb_asset` a
    WHERE a.`name` = 'demo-network-01' AND a.`project_id` = p.`id`
  );

SELECT
  (SELECT COUNT(*) FROM `cmdb_asset_change`) AS asset_change_count,
  (SELECT COUNT(*) FROM `cmdb_import_audit`) AS import_audit_count,
  (SELECT COUNT(*) FROM `cmdb_import_failure`) AS import_failure_count,
  (SELECT COUNT(*) FROM `cmdb_operation_audit`) AS operation_audit_count,
  (SELECT COUNT(*) FROM `cmdb_asset` WHERE `name` LIKE 'demo-%') AS demo_asset_count;
