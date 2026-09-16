-- Orbit CMDB database initialization
-- Compatible with MySQL 5.7+

CREATE DATABASE IF NOT EXISTS `cmdb`
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;

USE `cmdb`;

CREATE TABLE IF NOT EXISTS `sys_user` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `username` VARCHAR(50) NOT NULL,
  `password` VARCHAR(100) NOT NULL,
  `display_name` VARCHAR(100) NOT NULL,
  `role` VARCHAR(30) NOT NULL DEFAULT 'OPERATOR',
  `enabled` TINYINT(1) NOT NULL DEFAULT 1,
  `created_at` DATETIME(6) NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sys_user_username` (`username`),
  KEY `idx_sys_user_role_enabled` (`role`, `enabled`)
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `cmdb_project` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `name` VARCHAR(100) NOT NULL,
  `code` VARCHAR(30) NULL,
  `description` VARCHAR(500) NULL,
  `owner` VARCHAR(50) NULL,
  `created_at` DATETIME(6) NULL,
  PRIMARY KEY (`id`),
  KEY `idx_cmdb_project_name` (`name`),
  KEY `idx_cmdb_project_code` (`code`)
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `cmdb_asset` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `name` VARCHAR(100) NOT NULL,
  `asset_type` VARCHAR(50) NULL,
  `environment` VARCHAR(50) NULL,
  `private_ip` VARCHAR(45) NULL,
  `public_ip` VARCHAR(45) NULL,
  `hostname` VARCHAR(100) NULL,
  `status` VARCHAR(30) NOT NULL DEFAULT 'ONLINE',
  `region` VARCHAR(100) NULL,
  `description` VARCHAR(500) NULL,
  `project_id` BIGINT NOT NULL,
  `created_at` DATETIME(6) NULL,
  `updated_at` DATETIME(6) NULL,
  PRIMARY KEY (`id`),
  KEY `idx_cmdb_asset_project_id` (`project_id`),
  KEY `idx_cmdb_asset_asset_type` (`asset_type`),
  KEY `idx_cmdb_asset_status` (`status`),
  KEY `idx_cmdb_asset_environment` (`environment`),
  KEY `idx_cmdb_asset_private_ip` (`private_ip`),
  KEY `idx_cmdb_asset_public_ip` (`public_ip`),
  KEY `idx_cmdb_asset_hostname` (`hostname`),
  CONSTRAINT `fk_cmdb_asset_project`
    FOREIGN KEY (`project_id`) REFERENCES `cmdb_project` (`id`)
    ON UPDATE CASCADE
    ON DELETE RESTRICT
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `cmdb_asset_type` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `code` VARCHAR(50) NOT NULL,
  `sort_order` INT NOT NULL DEFAULT 0,
  `enabled` TINYINT(1) NOT NULL DEFAULT 1,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_cmdb_asset_type_code` (`code`),
  KEY `idx_cmdb_asset_type_enabled_sort` (`enabled`, `sort_order`, `code`)
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci;

INSERT IGNORE INTO `cmdb_asset_type` (`code`, `sort_order`, `enabled`) VALUES
  ('SERVER', 10, 1),
  ('DATABASE', 20, 1),
  ('NETWORK', 30, 1),
  ('STORAGE', 40, 1),
  ('APPLICATION', 50, 1),
  ('OTHER', 60, 1);

-- The application creates the admin account on first startup.
-- Set ADMIN_PASSWORD to a strong password before first startup.
-- Optional starter project:
-- INSERT INTO `cmdb_project` (`name`, `code`, `owner`, `description`)
-- VALUES ('默认项目', 'DEFAULT', '系统管理员', 'CMDB 默认项目空间');
