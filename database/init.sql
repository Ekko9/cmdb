-- Orbit CMDB unified database initialization
-- Compatible with MySQL 5.7+
--
-- This is the only manual SQL entry point for a fresh database.
-- It includes:
--   1. Core CMDB tables
--   2. Asset type dictionary
--   3. Continent/country/city region dictionary
--   4. Asset change, import and operation audit tables
--   5. Optional demo project and assets
--
-- Demo data is disabled by default. To enable it in the same SQL session:
-- SET @CMDB_LOAD_SAMPLE_DATA = 1;
-- source D:/workspace/cmdb/database/init.sql;

SET NAMES utf8mb4;
SET @CMDB_LOAD_SAMPLE_DATA = COALESCE(@CMDB_LOAD_SAMPLE_DATA, 0);

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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `cmdb_asset_type` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `code` VARCHAR(50) NOT NULL,
  `sort_order` INT NOT NULL DEFAULT 0,
  `enabled` TINYINT(1) NOT NULL DEFAULT 1,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_cmdb_asset_type_code` (`code`),
  KEY `idx_cmdb_asset_type_enabled_sort` (`enabled`, `sort_order`, `code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT IGNORE INTO `cmdb_asset_type` (`code`, `sort_order`, `enabled`) VALUES
  ('SERVER', 10, 1),
  ('DATABASE', 20, 1),
  ('NETWORK', 30, 1),
  ('STORAGE', 40, 1),
  ('APPLICATION', 50, 1),
  ('OTHER', 60, 1);

CREATE TABLE IF NOT EXISTS `cmdb_region` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `continent` VARCHAR(50) NOT NULL,
  `country` VARCHAR(100) NOT NULL,
  `region` VARCHAR(100) NOT NULL,
  `code` VARCHAR(30) NULL,
  `enabled` TINYINT(1) NOT NULL DEFAULT 1,
  `sort_order` INT NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_cmdb_region` (`country`, `region`),
  KEY `idx_cmdb_region_lookup` (`enabled`, `continent`, `country`, `sort_order`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Region dictionary. Add custom locations to this table with enabled = 1.
INSERT IGNORE INTO `cmdb_region`
  (`continent`, `country`, `region`, `code`, `sort_order`) VALUES
('亚洲','中国','北京','CN-BJ',10),('亚洲','中国','上海','CN-SH',11),('亚洲','中国','广州','CN-GZ',12),('亚洲','中国','深圳','CN-SZ',13),('亚洲','中国','杭州','CN-HZ',14),('亚洲','中国','南京','CN-NJ',15),('亚洲','中国','苏州','CN-SZ2',16),('亚洲','中国','成都','CN-CD',17),('亚洲','中国','武汉','CN-WH',18),('亚洲','中国','西安','CN-XA',19),('亚洲','中国','香港','CN-HK',20),('亚洲','中国','台北','CN-TPE',21),('亚洲','中国','广东','CN-GD',22),('亚洲','中国','浙江','CN-ZJ',23),('亚洲','中国','江苏','CN-JS',24),('亚洲','中国','四川','CN-SC',25),
('亚洲','日本','东京','JP-TYO',30),('亚洲','日本','大阪','JP-OSA',31),('亚洲','日本','名古屋','JP-NGO',32),('亚洲','日本','福冈','JP-FUK',33),('亚洲','日本','关东','JP-KN',34),('亚洲','日本','关西','JP-KS',35),
('亚洲','韩国','首尔','KR-SEL',40),('亚洲','韩国','釜山','KR-PUS',41),('亚洲','印度','新德里','IN-DEL',50),('亚洲','印度','德里','IN-DL',51),('亚洲','印度','孟买','IN-BOM',52),('亚洲','印度','班加罗尔','IN-BLR',53),('亚洲','印度','海得拉巴','IN-HYD',54),
('亚洲','新加坡','新加坡','SG-SIN',60),('亚洲','印度尼西亚','雅加达','ID-JKT',61),('亚洲','泰国','曼谷','TH-BKK',62),('亚洲','越南','河内','VN-HAN',63),('亚洲','越南','胡志明市','VN-SGN',64),('亚洲','马来西亚','吉隆坡','MY-KUL',65),('亚洲','菲律宾','马尼拉','PH-MNL',66),
('亚洲','阿联酋','迪拜','AE-DXB',70),('亚洲','阿联酋','阿布扎比','AE-AUH',71),('亚洲','沙特阿拉伯','利雅得','SA-RUH',72),('亚洲','以色列','特拉维夫','IL-TLV',73),
('欧洲','英国','伦敦','GB-LON',100),('欧洲','英国','曼彻斯特','GB-MAN',101),('欧洲','德国','法兰克福','DE-FRA',110),('欧洲','德国','柏林','DE-BER',111),('欧洲','德国','慕尼黑','DE-MUC',112),('欧洲','法国','巴黎','FR-PAR',120),('欧洲','法国','马赛','FR-MRS',121),('欧洲','荷兰','阿姆斯特丹','NL-AMS',130),('欧洲','爱尔兰','都柏林','IE-DUB',132),('欧洲','西班牙','马德里','ES-MAD',140),('欧洲','西班牙','巴塞罗那','ES-BCN',141),('欧洲','意大利','罗马','IT-ROM',150),('欧洲','意大利','米兰','IT-MIL',151),('欧洲','瑞士','苏黎世','CH-ZRH',160),('欧洲','瑞典','斯德哥尔摩','SE-STO',170),('欧洲','挪威','奥斯陆','NO-OSL',171),('欧洲','芬兰','赫尔辛基','FI-HEL',172),('欧洲','波兰','华沙','PL-WAW',173),('欧洲','葡萄牙','里斯本','PT-LIS',174),('欧洲','俄罗斯','莫斯科','RU-MOW',180),
('北美洲','美国','弗吉尼亚','US-VA',200),('北美洲','美国','俄勒冈','US-OR',201),('北美洲','美国','硅谷','US-CA',202),('北美洲','美国','纽约','US-NY',203),('北美洲','美国','华盛顿','US-DC',204),('北美洲','美国','德克萨斯','US-TX',205),('北美洲','美国','亚特兰大','US-GA',206),('北美洲','美国','芝加哥','US-IL',207),('北美洲','加拿大','多伦多','CA-ON',210),('北美洲','加拿大','温哥华','CA-BC',211),('北美洲','加拿大','蒙特利尔','CA-QC',212),('北美洲','墨西哥','墨西哥城','MX-CMX',220),('北美洲','古巴','哈瓦那','CU-HAV',221),
('南美洲','巴西','圣保罗','BR-SP',300),('南美洲','巴西','里约热内卢','BR-RJ',301),('南美洲','阿根廷','布宜诺斯艾利斯','AR-BUE',310),('南美洲','智利','圣地亚哥','CL-SCL',320),('南美洲','哥伦比亚','波哥大','CO-BOG',330),('南美洲','秘鲁','利马','PE-LIM',340),
('大洋洲','澳大利亚','悉尼','AU-NSW',400),('大洋洲','澳大利亚','墨尔本','AU-VIC',401),('大洋洲','澳大利亚','布里斯班','AU-QLD',402),('大洋洲','澳大利亚','珀斯','AU-WA',403),('大洋洲','新西兰','奥克兰','NZ-AUK',410),('大洋洲','新西兰','惠灵顿','NZ-WLG',411),
('非洲','南非','约翰内斯堡','ZA-GP',500),('非洲','南非','开普敦','ZA-WC',501),('非洲','埃及','开罗','EG-CAI',510),('非洲','尼日利亚','拉各斯','NG-LAG',520),('非洲','肯尼亚','内罗毕','KE-NBO',530),('非洲','摩洛哥','卡萨布兰卡','MA-CAS',540);

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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

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

-- Optional sample project and assets. Disabled unless explicitly enabled.
INSERT INTO `cmdb_project` (`name`, `code`, `owner`, `description`)
SELECT '演示项目', 'DEMO', '系统管理员', 'CMDB 功能演示项目'
WHERE @CMDB_LOAD_SAMPLE_DATA = 1
  AND NOT EXISTS (SELECT 1 FROM `cmdb_project` WHERE `code` = 'DEMO');

INSERT INTO `cmdb_asset`
  (`name`, `asset_type`, `environment`, `private_ip`, `public_ip`, `hostname`,
   `status`, `region`, `description`, `project_id`, `created_at`, `updated_at`)
SELECT 'demo-web-01', 'SERVER', 'PRODUCTION', '10.20.0.11', '203.0.113.11',
       'demo-web-01', 'ONLINE', '亚洲 / 中国 / 杭州', '演示 Web 服务器',
       p.`id`, NOW(6), NOW(6)
FROM `cmdb_project` p
WHERE @CMDB_LOAD_SAMPLE_DATA = 1 AND p.`code` = 'DEMO'
  AND NOT EXISTS (SELECT 1 FROM `cmdb_asset` a WHERE a.`name` = 'demo-web-01' AND a.`project_id` = p.`id`);

INSERT INTO `cmdb_asset`
  (`name`, `asset_type`, `environment`, `private_ip`, `hostname`, `status`,
   `region`, `description`, `project_id`, `created_at`, `updated_at`)
SELECT 'demo-db-01', 'DATABASE', 'PRODUCTION', '10.20.0.21', 'demo-db-01',
       'ONLINE', '亚洲 / 中国 / 杭州', '演示数据库服务器',
       p.`id`, NOW(6), NOW(6)
FROM `cmdb_project` p
WHERE @CMDB_LOAD_SAMPLE_DATA = 1 AND p.`code` = 'DEMO'
  AND NOT EXISTS (SELECT 1 FROM `cmdb_asset` a WHERE a.`name` = 'demo-db-01' AND a.`project_id` = p.`id`);

INSERT INTO `cmdb_asset`
  (`name`, `asset_type`, `environment`, `private_ip`, `hostname`, `status`,
   `region`, `description`, `project_id`, `created_at`, `updated_at`)
SELECT 'demo-network-01', 'NETWORK', 'STAGING', '10.20.0.31', 'demo-network-01',
       'MAINTENANCE', '亚洲 / 中国 / 上海', '演示网络设备',
       p.`id`, NOW(6), NOW(6)
FROM `cmdb_project` p
WHERE @CMDB_LOAD_SAMPLE_DATA = 1 AND p.`code` = 'DEMO'
  AND NOT EXISTS (SELECT 1 FROM `cmdb_asset` a WHERE a.`name` = 'demo-network-01' AND a.`project_id` = p.`id`);

-- Verification summary.
SELECT
  (SELECT COUNT(*) FROM `sys_user`) AS user_count,
  (SELECT COUNT(*) FROM `cmdb_project`) AS project_count,
  (SELECT COUNT(*) FROM `cmdb_asset`) AS asset_count,
  (SELECT COUNT(*) FROM `cmdb_asset_type` WHERE `enabled` = 1) AS asset_type_count,
  (SELECT COUNT(*) FROM `cmdb_region` WHERE `enabled` = 1) AS region_count,
  (SELECT COUNT(*) FROM `cmdb_asset_change`) AS asset_change_count,
  (SELECT COUNT(*) FROM `cmdb_import_audit`) AS import_audit_count,
  (SELECT COUNT(*) FROM `cmdb_import_failure`) AS import_failure_count,
  (SELECT COUNT(*) FROM `cmdb_operation_audit`) AS operation_audit_count;
