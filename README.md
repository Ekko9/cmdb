# Orbit CMDB

Orbit CMDB 是一个轻量级的配置管理数据库（Configuration Management Database），用于统一维护项目、基础设施资产和用户权限。

当前版本为 `1.0.0`，后端使用 Spring Boot 3.3.13，运行环境为 JDK 17，前端使用 Vue 3，数据访问使用 Spring Data JPA，数据库使用 MySQL 5.7+。应用内置前端页面，打包后只需要启动一个 Java 服务即可访问。

## 1. 功能概览

### 1.1 登录与权限

- 用户名密码登录。
- 支持公开提交注册申请；新账号固定为停用的 `VIEWER`，需管理员审核启用。
- 使用 HMAC-SHA256 签名令牌访问受保护接口。
- 令牌默认有效期为 12 小时。
- 密码至少包含大小写字母、数字和特殊符号，最长有效期 3 个月；到期前 14 天提醒，到期后仅允许先修改密码。
- 用户可修改自己的密码；不提供自助找回密码，忘记密码由管理员重置。
- 支持三种角色：
  - `ADMIN`：管理员，可管理用户、项目和资产。
  - `OPERATOR`：运维人员，可新增、编辑、删除和导入项目/资产，不能管理用户。
  - `VIEWER`：只读用户，只能查看、导出和下载模板。
- 用户可以启用或停用，停用用户无法登录；管理员可重置用户密码。

后端拦截器会按角色校验 `/api/users`、`/api/projects` 和 `/api/assets` 请求；前端按钮隐藏仅用于改善操作体验，不能替代服务端授权。

### 1.2 项目空间

- 新建、编辑和删除项目。
- 支持项目编码、负责人和描述。
- 自动统计每个项目下的资产数量。
- 项目下仍有资产时禁止删除，必须先删除资产或将资产迁移到其他项目。

### 1.3 资产清单

- 新建、编辑、单项删除和当前页批量删除资产。
- 支持资产类型、环境、状态、区域、内网 IP、外网 IP、主机名和描述；区域维护为洲、国家/地区、城市/区域三级字典下拉。
- 支持分页、排序，以及按 IP、项目等条件筛选资产。
- 运行状态与生命周期分开管理；生命周期支持规划中、在用、退役中和已退役，并保留流转历史。
- 支持项目内资产关系和拓扑查看。
- 支持 CSV 和 Excel（`.xlsx`）批量导入、导入前预校验、重复资产跳过/合并，以及失败行在线修正重试。
- 支持 CSV 和 Excel（`.xlsx`）导出。

### 1.4 审计与追踪

- 记录资产字段变更、生命周期流转和导入结果。
- 操作审计支持操作人、动作、资源类型、结果、关键字和时间范围筛选。
- 操作审计支持 CSV 导出，并可将历史记录迁入独立归档表后继续查询。

### 1.5 总览仪表盘

当前总览页面展示：

- 项目总数。
- 资产总数。
- 在线资产数。
- 生产环境资产数。
- 最近资产列表。

## 2. 技术栈

| 分类 | 选型 |
| --- | --- |
| 后端框架 | Spring Boot 3.3.13、Spring MVC |
| 开发语言 | Java 17 |
| ORM | Spring Data JPA、Hibernate |
| 数据库 | MySQL 5.7+ |
| 密码加密 | BCrypt |
| 接口认证 | HMAC-SHA256 签名令牌 |
| 前端 | Vue 3 CDN、原生 HTML/CSS/JavaScript |
| CSV | Apache Commons CSV 1.10.0 |
| 打包方式 | Spring Boot Fat JAR |
| 容器化 | Docker、Docker Compose、Kubernetes |
| 健康检查 | Spring Boot Actuator liveness/readiness、metrics、Prometheus |

## 3. 项目目录

```text
cmdb/
├─ database/
│  ├─ init.sql                    # 完整数据库初始化单一入口
│  ├─ asset-import-template.csv  # 资产导入 CSV 模板，包含样例
│  └─ asset-import-template.xlsx # 资产导入 Excel 模板，包含下拉选项和样例
├─ src/
│  └─ main/
│     ├─ java/com/cmdb/
│     │  ├─ config/               # 认证、异常处理、初始化和 Web 配置
│     │  ├─ controller/           # 认证、总览、项目、资产、拓扑、生命周期、审计和用户接口
│     │  ├─ entity/               # 用户、项目、资产、关系、生命周期和审计实体
│     │  └─ repo/                 # JPA Repository
│     └─ resources/
│        ├─ application.yaml      # 服务、数据库和认证配置
│        └─ static/                # 前端页面、脚本和样式
├─ Dockerfile
├─ docker-compose.yml             # 本地应用运行配置，不包含 MySQL
├─ k8s/
│  └─ cmdb.yaml                   # Kubernetes 部署、服务和密钥模板
├─ pom.xml
└─ README.md
```

## 4. 运行环境

### 4.1 最低要求

- JDK 17 或更高版本。
- Maven 3.8 或更高版本。
- MySQL 5.7 或更高版本。
- 浏览器：Chrome、Edge 或其他现代浏览器。
- 浏览器需要能够加载 Vue 3 CDN；当前前端依赖 `unpkg.com`。

### 4.2 本机开发环境

当前开发机使用以下目录：

| 组件 | 路径 |
| --- | --- |
| 项目目录 | `D:\workspace\cmdb` |
| JDK 17 | `D:\jude\jdk17` |
| Maven | `D:\jude\maven` |
| Maven 本地仓库 | `D:\jude\maven-repository` |

如果使用其他目录，只需要调整命令中的路径。

## 5. 初始化数据库

### 5.1 执行初始化脚本

使用 SQL 客户端连接 MySQL 后，执行：

```sql
source D:/workspace/cmdb/database/init.sql;
```

也可以直接打开 `database/init.sql`，复制全部内容到 SQL 客户端执行。

`database/init.sql` 现在是完整的单一手工初始化入口，脚本会完成以下操作：

1. 创建 `cmdb` 数据库。
2. 创建 `sys_user` 用户表。
3. 创建 `cmdb_project` 项目表。
4. 创建 `cmdb_asset` 资产表。
5. 创建 `cmdb_asset_type` 资产类型选项表并写入默认类型。
6. 创建 `cmdb_region` 区域字典表，并写入洲、国家/地区、城市/区域字典。
7. 创建项目与资产之间的外键关系。
8. 创建资产关系表和生命周期流转历史表。
9. 创建资产变更记录、导入审计、导入失败明细、当前操作审计和长期归档表。
10. 为已有数据库幂等补齐密码修改时间、生命周期、资产唯一约束、审计归档结构和相关索引。
11. 创建常用查询索引。
12. 预置可重复执行的样例数据语句，默认不插入样例数据。

脚本使用 `CREATE DATABASE IF NOT EXISTS`、`CREATE TABLE IF NOT EXISTS` 和 `INFORMATION_SCHEMA` 条件升级语句；正常情况下可重复执行，不会丢失资产或覆盖业务数据。旧版本中已标记归档的审计记录会迁入独立归档表并从当前审计表移除。若旧库存在重复的非空项目编码、同项目资产名称或资产 IP，必须先合并或修正重复数据，唯一索引才能创建成功。

### 5.2 表关系

```text
sys_user       用户和权限
cmdb_project   项目空间
cmdb_asset     资产，必须关联一个项目
cmdb_asset_type 资产类型下拉选项
cmdb_region      洲、国家/地区、城市/区域三级字典
cmdb_asset_relation 项目内资产关系
cmdb_asset_lifecycle_change 生命周期流转历史
cmdb_asset_change 资产变更记录
cmdb_import_audit 导入审计主表
cmdb_import_failure 导入失败行明细
cmdb_operation_audit 操作审计日志
cmdb_operation_audit_archive 长期归档的操作审计日志

cmdb_project 1 ─── N cmdb_asset
cmdb_asset_type 1 ─── N cmdb_asset（通过类型编码使用）
cmdb_asset 1 ─── N cmdb_asset_relation（分别作为关系源和目标）
cmdb_asset 1 ─── N cmdb_asset_lifecycle_change（通过 asset_id 保留历史快照）
cmdb_import_audit 1 ─── N cmdb_import_failure
```

### 5.3 启动时的数据库校验

当前版本不在应用启动时执行数据库迁移。数据库结构统一由 `database/init.sql` 管理，应用启动时只校验实体与数据库表结构是否匹配。

当前 `application.yaml` 使用：

```yaml
spring:
  jpa:
    hibernate:
      ddl-auto: validate
```

这表示应用启动时只校验实体与数据库表结构是否匹配，不会自动创建或修改表。首次启动前必须先执行 `database/init.sql`。

如果启动时报表不存在、字段不存在或类型不匹配，请优先检查：

- 当前连接的数据库是否为 `cmdb`。
- 初始化脚本是否完整执行。
- 数据库账号是否有访问和读取表结构的权限。
- 数据库表名、字段名是否被手工修改。

如果希望在初始化时同时插入演示项目和 3 条资产样例，可以在同一个 SQL 会话中执行：

```sql
SET @CMDB_LOAD_SAMPLE_DATA = 1;
source D:/workspace/cmdb/database/init.sql;
```

已有数据库也可以重复执行 `init.sql` 补齐缺失的表、字段、索引和字典数据；脚本不会丢失资产和业务数据。升级会为已有账号补充 `password_changed_at`，但不会修改密码哈希；生命周期、资产关系、资产唯一约束和独立审计归档表也由同一脚本补齐。当前项目只保留这一份手工数据库脚本，后续表结构调整也必须同步更新 `init.sql`。

区域字典按洲、国家/地区、城市/区域保存。项目内置常用国家、首都、主要城市和云服务区域；城市数量没有统一的“全部”边界，业务自定义城市可以继续向 `cmdb_region` 追加，`enabled=1` 后会自动出现在资产表单下拉中。

## 6. 应用配置

项目内置配置文件为：

```text
src/main/resources/application.yaml
```

该文件只保留可提交到仓库的默认值和占位值。实际部署时优先使用环境变量或外置配置文件覆盖数据库地址、数据库账号、数据库密码、令牌密钥和初始管理员密码。

主要配置项如下：

| 配置项 | 作用 | 当前说明 |
| --- | --- | --- |
| `server.port` | Web 服务端口 | 默认 `8080` |
| `spring.datasource.url` | MySQL JDBC 地址 | 指向 `cmdb` 数据库 |
| `spring.datasource.username` | 数据库用户 | 建议使用最小权限账号 |
| `spring.datasource.password` | 数据库密码 | 生产环境不要写入代码仓库 |
| `spring.jpa.hibernate.ddl-auto` | JPA 表结构策略 | 当前为 `validate` |
| `spring.servlet.multipart.max-file-size` | 单个上传文件大小 | `10MB` |
| `spring.servlet.multipart.max-request-size` | 请求最大大小 | `10MB` |
| `cmdb.jwt-secret` | 令牌签名密钥 | 启动校验要求至少 32 个字符的随机值 |
| `cmdb.jwt-expire-hours` | 令牌有效时长 | 默认 `12` 小时 |
| `cmdb.initial-admin-password` | 首次创建管理员时使用的密码 | 通过 `ADMIN_PASSWORD` 提供 |
| `management.endpoints.web.exposure.include` | 暴露健康和监控接口 | `health,info,metrics,prometheus` |
| `logging.file.name` | 应用日志文件 | 默认 `logs/cmdb.log` |
| `logging.logback.rollingpolicy.max-file-size` | 单个日志文件大小 | 默认 `20MB` |
| `logging.logback.rollingpolicy.max-history` | 日志保留文件数 | 默认 `14` |
| `logging.logback.rollingpolicy.total-size-cap` | 滚动日志总容量 | 默认 `512MB` |

仓库中的 `application.yaml` 只保留安全占位值。启动时请通过环境变量、密钥管理服务或部署平台的加密配置提供真实数据库连接信息和令牌密钥，避免把真实密码提交到代码仓库。

### 6.1 环境变量覆盖

Spring Boot 会自动读取环境变量。推荐在裸机、Docker 和 Kubernetes 中使用以下变量：

| 环境变量 | 对应配置 | 示例 |
| --- | --- | --- |
| `SERVER_PORT` | `server.port` | `8080` |
| `DB_URL` | `spring.datasource.url` | `jdbc:mysql://mysql-host:3306/cmdb?...` |
| `DB_USERNAME` | `spring.datasource.username` | `cmdb_app` |
| `DB_PASSWORD` | `spring.datasource.password` | 数据库密码 |
| `JWT_SECRET` | `cmdb.jwt-secret` | 至少 32 个字符的随机字符串 |
| `JWT_EXPIRE_HOURS` | `cmdb.jwt-expire-hours` | `12` |
| `ADMIN_PASSWORD` | `cmdb.initial-admin-password` | 首次安装管理员密码 |
| `LOG_FILE` | `logging.file.name` | `logs/cmdb.log` |
| `LOG_MAX_FILE_SIZE` | `logging.logback.rollingpolicy.max-file-size` | `20MB` |
| `LOG_MAX_HISTORY` | `logging.logback.rollingpolicy.max-history` | `14` |
| `LOG_TOTAL_SIZE_CAP` | `logging.logback.rollingpolicy.total-size-cap` | `512MB` |

Windows PowerShell 示例：

```powershell
$env:DB_URL = 'jdbc:mysql://your-mysql-host:3306/cmdb?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true'
$env:DB_USERNAME = 'cmdb_app'
$env:DB_PASSWORD = 'replace-with-your-password'
$env:JWT_SECRET = 'replace-with-a-long-random-secret'
$env:ADMIN_PASSWORD = 'replace-with-a-strong-initial-admin-password'
```

Linux 示例：

```bash
export DB_URL='jdbc:mysql://your-mysql-host:3306/cmdb?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true'
export DB_USERNAME='cmdb_app'
export DB_PASSWORD='replace-with-your-password'
export JWT_SECRET='replace-with-a-long-random-secret'
export ADMIN_PASSWORD='replace-with-a-strong-initial-admin-password'
```

### 6.2 外置配置文件覆盖

也可以在 JAR 同级目录放置外置配置文件，例如：

```text
D:\deploy\cmdb\application-prod.yaml
```

推荐内容如下：

```yaml
server:
  port: 8080
spring:
  datasource:
    url: jdbc:mysql://your-mysql-host:3306/cmdb?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true
    username: cmdb_app
    password: replace-with-your-password
cmdb:
  jwt-secret: replace-with-a-long-random-secret
  jwt-expire-hours: 12
  initial-admin-password: replace-with-a-strong-initial-admin-password
logging:
  file:
    name: logs/cmdb.log
```

启动时通过 `--spring.config.additional-location` 指定这个外置文件：

```powershell
& 'D:\jude\jdk17\bin\java.exe' `
  -jar 'D:\deploy\cmdb\cmdb-1.0.0.jar' `
  --spring.config.additional-location='file:D:/deploy/cmdb/application-prod.yaml'
```

注意：外置配置文件中可能包含数据库密码，不要提交到 Git，也不要放在公开共享目录。

## 7. 裸机部署和启动

裸机部署指不使用 Docker，直接在服务器上安装 JDK 17 并运行 Spring Boot JAR。适合 Windows Server、Linux 虚拟机或物理机。

### 7.1 部署目录建议

建议按下面的结构放置文件：

```text
D:\deploy\cmdb\
├─ cmdb-1.0.0.jar
├─ application-prod.yaml
└─ logs\
```

Linux 可使用：

```text
/opt/cmdb/
├─ cmdb-1.0.0.jar
├─ application-prod.yaml
└─ logs/
```

### 7.2 裸机部署步骤

1. 安装 JDK 17。
2. 安装并准备 MySQL 5.7+。
3. 执行 `database/init.sql` 初始化数据库。
4. 将 `target/cmdb-1.0.0.jar` 复制到部署目录。
5. 在部署目录创建 `application-prod.yaml`，填写真实数据库连接、令牌密钥和初始管理员密码。
6. 启动应用并检查健康状态。

### 7.3 使用已打包 JAR 前台启动

项目当前可直接启动的产物为：

```text
D:\workspace\cmdb\target\cmdb-1.0.0.jar
```

Windows PowerShell 启动命令：

```powershell
& 'D:\jude\jdk17\bin\java.exe' `
  -jar 'D:\workspace\cmdb\target\cmdb-1.0.0.jar' `
  --spring.config.additional-location='file:D:/deploy/cmdb/application-prod.yaml'
```

如果只使用环境变量，不使用外置配置文件，可以省略 `--spring.config.additional-location`。

Linux 启动命令：

```bash
java -jar /opt/cmdb/cmdb-1.0.0.jar \
  --spring.config.additional-location=file:/opt/cmdb/application-prod.yaml
```

启动成功后访问：

```text
http://localhost:8080/
```

### 7.4 Windows 后台启动

PowerShell 示例：

```powershell
$app = 'D:\deploy\cmdb'
$java = 'D:\jude\jdk17\bin\java.exe'
$jar = Join-Path $app 'cmdb-1.0.0.jar'
$stdout = Join-Path $app 'logs\cmdb.stdout.log'
$stderr = Join-Path $app 'logs\cmdb.stderr.log'

New-Item -ItemType Directory -Force -Path (Join-Path $app 'logs') | Out-Null
Start-Process -FilePath $java `
  -ArgumentList @('-jar', $jar, '--spring.config.additional-location=file:D:/deploy/cmdb/application-prod.yaml') `
  -WorkingDirectory $app `
  -RedirectStandardOutput $stdout `
  -RedirectStandardError $stderr `
  -WindowStyle Hidden
```

查看进程：

```powershell
Get-CimInstance Win32_Process |
  Where-Object { $_.CommandLine -like '*cmdb-1.0.0.jar*' } |
  Select-Object ProcessId,CommandLine
```

停止进程：

```powershell
Stop-Process -Id <ProcessId>
```

### 7.5 Linux 后台启动

临时后台启动：

```bash
cd /opt/cmdb
nohup java -jar cmdb-1.0.0.jar \
  --spring.config.additional-location=file:/opt/cmdb/application-prod.yaml \
  > logs/cmdb.stdout.log 2> logs/cmdb.stderr.log &
```

查看进程：

```bash
ps -ef | grep cmdb-1.0.0.jar
```

停止进程：

```bash
kill <pid>
```

生产环境建议使用 `systemd`、Windows 服务包装器或进程守护工具托管应用，保证开机自启、失败拉起和统一日志采集。

### 7.6 使用 Maven 重新打包

```powershell
$env:JAVA_HOME = 'D:\jude\jdk17'
& 'D:\jude\maven\bin\mvn.cmd' clean package
```

打包成功后会生成：

```text
target/cmdb-1.0.0.jar
```

构建会编译并执行自动化测试。Dockerfile 的正式打包阶段同样不会跳过测试。

### 7.7 2026-11-06 已验证的 Linux 部署

`192.168.11.218` 的裸机实例部署目录为 `/opt/fusx/cmdb`，服务端口为 `9090`。服务器系统默认 Java 仍为 Java 8，启动脚本使用项目目录中的 JDK 17，不要改用系统 Java。

目录内运行配置保存在受限权限的 `cmdb.env`，不要覆盖或提交该文件。仓库中的 `deploy/cmdb-start.sh`、`deploy/cmdb-stop.sh` 和 `deploy/cmdb-status.sh` 用于启停检查。脚本会核对 PID 对应的命令确实是当前 `cmdb.jar`，避免误停其他进程；启动前还会拒绝占用中的端口，启动后等待 readiness 返回成功并再次确认新进程仍存活。停止脚本会等待进程退出，必要时才强制终止。可通过 `CMDB_STARTUP_TIMEOUT` 调整启动等待秒数。替换 JAR 时先备份旧包，健康检查通过后再清理备份。

当前线上进程 PID 为 `6104`，JAR SHA-256 为 `ED42FCF752DE76285A4EC4C6A0995917297D3A826EBFB14A1F14FB6B6B741B37`。部署前数据库备份为 `/opt/fusx/cmdb/backups/cmdb-20261106-155130.sql.gz`，SHA-256 为 `47FDF51A65B5C25F660A15F18C284CC4B446110333F45141DEC3B101D9180F5A`。PID 和哈希会随后续发布变化，应以部署当次输出为准。

### 7.8 检查服务是否启动

检查 8080 端口：

```powershell
Get-NetTCPConnection -LocalPort 8080 -State Listen
```

检查首页：

```powershell
Invoke-WebRequest -UseBasicParsing -Uri 'http://localhost:8080/'
```

检查健康状态：

```powershell
Invoke-RestMethod -Uri 'http://localhost:8080/actuator/health/readiness'
```

如果端口已监听且健康检查返回 `UP`，打开浏览器访问 `http://localhost:8080/`。

## 8. 首次登录

首次启动时，`DataInitializer` 会自动创建以下数据：

- 管理员账号：`admin`
- 仅当数据库中还没有 `admin` 账号时，才会创建初始管理员；首次启动前必须通过 `ADMIN_PASSWORD` 设置密码。未设置或不符合密码策略时，应用会停止启动，不会创建默认弱密码账号。
- 项目不提供通用默认密码。`ADMIN_PASSWORD` 只用于首次创建管理员；管理员已存在时，重新部署或重启不会修改其密码。遗忘密码需由另一位管理员在用户权限页面重置。
- 默认角色：`ADMIN`
- 默认项目：`默认项目`
- 默认项目编码：`DEFAULT`

首次登录后建议立即在“用户权限”页面编辑管理员，设置新的密码。

如果数据库中已经存在 `admin` 用户，应用不会覆盖已有密码；如果数据库中已有项目，也不会重复创建默认项目。

## 9. 页面使用说明

### 9.1 总览

登录后默认进入“资产总览”。页面会加载项目、资产和统计数据。点击“查看全部”可以进入资产清单。

### 9.2 项目空间

1. 点击“项目空间”。
2. 点击“新建项目”。
3. 填写项目名称，项目编码、负责人和描述为可选项。
4. 点击“保存”。
5. 使用每行的“编辑”修改项目。
6. 只有项目资产数为 `0` 时才允许删除项目。

项目编码用于区分项目，重复编码会被拒绝。

项目编码由数据库唯一索引保证唯一；升级已有数据库时，`init.sql` 会补建该索引。若已有重复编码，需先整理重复项目后再执行升级脚本。

### 9.3 资产清单

新建资产时：

1. 填写资产名称。
2. 选择所属项目。
3. 按需填写资产类型、环境、状态、区域、IP、主机名和描述。
4. 点击“保存”。

资产名称和项目为必填字段。资产编辑时可以修改已有字段，但资产仍必须关联有效项目。同一项目内资产名称不能重复，内网 IP 和外网 IP 在整个 CMDB 中分别保持唯一；页面新增、编辑、导入和数据库唯一索引使用相同规则。

资产搜索区只保留两个维度：

- IP：匹配内网 IP 和外网 IP。
- 项目：按所属项目筛选。

资产名称、类型、环境、状态和区域不参与列表搜索；区域直接在资产列表中展示。新建和编辑资产时，区域从洲、国家/地区、城市/区域三级下拉中选择；历史自由文本区域会作为兼容选项保留。

资产清单支持服务端分页、排序和多条件筛选。点击资产名称可以打开资产详情页，详情页展示资产基础信息和最近 100 条变更记录。

管理员和运维人员可以勾选资产清单当前页的多项资产并批量删除；每次最多 100 项。翻页或重新筛选会清空所选项，删除前会要求二次确认，删除结果逐项写入资产变更记录和操作审计。

资产运行状态（在线、离线、维护中）与生命周期状态独立。生命周期依次支持规划中、在用、退役中、已退役；只允许规划中→在用/已退役、在用→退役中、退役中→在用/已退役。每次流转需由有写入权限的用户执行，可填写原因，并永久保留操作人和时间。资产详情可查看生命周期记录。

“资产拓扑”按项目显示节点及其关系。关系类型包括依赖、运行于、连接、包含和备份自；目前仅允许同一项目内关联，不允许资产关联自身或添加相同关系。删除资产时关系随资产一并删除，拓扑查询不会跨项目扩展。

### 9.4 用户权限

管理员登录后可以进入“用户权限”页面：

1. 新建用户时需要填写用户名和密码。
2. 用户名为 3 至 50 位，只允许字母、数字、点、下划线和连字符，且不能重复。
3. 可以设置显示名称和角色。
4. 编辑用户时可以修改显示名称、角色、启用状态和密码。
5. 系统至少保留一位启用的管理员；不能停用、降权或删除最后一位启用管理员。
6. 登录页支持提交注册申请；注册账号固定为停用的 `VIEWER` 只读账号，必须由管理员审核启用。
7. 停用用户无法继续登录。
8. `admin` 是系统保留账号，不允许删除。
9. 密码有效期最长 3 个月，登录时提前 14 天提醒；到期后只能进入修改密码流程，修改成功后需要使用新密码重新登录。
10. 每个已登录用户都可以通过“修改密码”更新自己的密码；忘记密码只能联系管理员重置，非管理员不能访问用户权限管理接口。

公开注册仅创建停用的只读账号，不开放自选角色，也不会自动登录。管理员在“用户权限”页面审核账号，必要时重置初始密码并启用。密码需为 8 至 72 位，不含空白字符，并包含大小写字母、数字和特殊符号。密码有效期按日历月计算；升级已有数据库时，初始化 SQL 会新增 `sys_user.password_changed_at`，将现有账号的有效期起点设为迁移时间，不会修改密码哈希。之后重复执行该 SQL 不会重置已经记录的密码时间。用户自助修改密码后界面会退出到登录页；用户自助修改或管理员重置密码后，之前签发的令牌立即失效，需要使用新密码重新登录。

当前页面只向前端返回用户基本信息，不返回密码哈希。

权限矩阵：

| 功能 | ADMIN | OPERATOR | VIEWER |
| --- | --- | --- | --- |
| 查看总览、项目、资产 | 允许 | 允许 | 允许 |
| 查看资产详情和变更记录 | 允许 | 允许 | 允许 |
| 导出资产、下载模板 | 允许 | 允许 | 允许 |
| 新增、编辑、删除项目和资产 | 允许 | 允许 | 禁止 |
| 导入资产 | 允许 | 允许 | 禁止 |
| 预校验/合并导入/失败行修正 | 允许 | 允许 | 禁止 |
| 查看资产关系拓扑 | 允许 | 允许 | 允许 |
| 创建资产关系和生命周期流转 | 允许 | 允许 | 禁止 |
| 查看导入审计和下载失败行 | 允许 | 允许 | 禁止 |
| 查看操作审计日志 | 允许 | 禁止 | 禁止 |
| 导出、归档操作审计 | 允许 | 禁止 | 禁止 |
| 查看、新增、编辑、删除用户 | 允许 | 禁止 | 禁止 |

## 10. 资产导入导出

### 10.1 模板位置

CSV 模板位于：

```text
database/asset-import-template.csv
```

Excel 模板位于：

```text
database/asset-import-template.xlsx
```

页面中的“下载 Excel 模板”会按照当前数据库中的项目和资产类型生成项目名称、类型、环境和状态下拉选项。Excel 模板包含“资产导入”页和隐藏的“选项”页，并预置一行完整样例。数据库没有项目时，模板仍会生成其他字段的下拉选项，并提示先创建项目。

CSV 模板也包含一行可复制修改的样例。两个模板使用相同的表头：

```csv
名称,类型,环境,内网IP,外网IP,主机名,项目名称,状态,区域,描述
```

### 10.2 字段说明

| 顺序 | 字段 | 是否必填 | 示例 | 说明 |
| ---: | --- | --- | --- | --- |
| 1 | `名称` | 是 | `web-01` | 资产名称 |
| 2 | `类型` | 否 | `SERVER` | 资产类型，如服务器、数据库 |
| 3 | `环境` | 否 | `PRODUCTION` | 建议使用 `PRODUCTION`、`STAGING`、`DEVELOPMENT` |
| 4 | `内网IP` | 否 | `10.0.0.10` | 内网地址 |
| 5 | `外网IP` | 否 | `203.0.113.10` | 外网地址 |
| 6 | `主机名` | 否 | `web-01` | 主机名 |
| 7 | `项目名称` | 是 | `默认项目` | 必须与系统中的项目名称一致 |
| 8 | `状态` | 否 | `ONLINE` | 建议使用 `ONLINE`、`OFFLINE`、`MAINTENANCE` |
| 9 | `区域` | 否 | `亚洲 / 中国 / 杭州` | 从洲、国家/地区、城市/区域三级字典选择 |
| 10 | `描述` | 否 | `业务 Web 服务器` | 资产补充说明 |

示例数据：

```csv
名称,类型,环境,内网IP,外网IP,主机名,项目名称,状态,区域,描述
web-01,SERVER,PRODUCTION,10.0.0.10,203.0.113.10,web-01,默认项目,ONLINE,亚洲 / 中国 / 杭州,业务 Web 服务器
```

### 10.3 导入规则

- 第一行为表头，字段按位置读取。
- 文件建议使用 UTF-8 编码。
- `名称` 和 `项目名称` 必须有效。
- 项目名称不存在时，该行会被跳过并返回错误信息。
- 少于 7 列的行会被跳过。
- 未填写状态时默认使用 `ONLINE`。
- 导入结果会显示成功数量、跳过数量和错误行信息。
- 每次导入都会写入导入审计记录。
- 导入失败行会保存行号、失败原因和原始数据，可在“导入审计”页面下载 CSV。
- 默认“跳过”策略不会更新已有资产；同一项目下资产名称重复、内网 IP 重复、外网 IP 重复，以及同一文件内的重复行都会被拒绝，并记录到失败行明细。
- 导入文件可先执行预校验；预校验仅读取文件和数据库，不创建导入审计或变更资产。显式选择“合并”时按同项目资产名称、内网 IP 或外网 IP 定位；预校验会模拟文件前序行产生的合并结果，名称和 IP 指向不同资产时会提前拒绝。合并只覆盖文件中非空的字段，未提供字段保持原值。
- 导入审计中的失败行可在线修正并重试。成功后失败行移除、成功/失败计数与导入状态同步更新；重试采用合并策略，避免修正重复行时误建重复资产。
- CSV 和 XLSX 单次最多导入 10,000 行；超过限制时整个请求会回滚。上传文件大小限制仍为 10MB。
- 导出最多返回 10,000 条资产；需要更多数据时请先筛选或分批导出。
- CSV 导出和失败行 CSV 会对公式起始字符进行转义，避免表格软件打开时将单元格内容当作公式执行。

### 10.4 页面操作

在“资产清单”页面：

- 点击“下载 Excel 模板”获取带下拉选项和样例的模板。
- 点击“导入文件”选择 `.csv` 或 `.xlsx` 文件。
- 点击“导出 CSV”下载 CSV 格式资产清单。
- 点击“导出 Excel”下载 Excel 格式资产清单。
- 导出会沿用资产列表当前的 IP 搜索和项目筛选条件，只导出当前筛选结果；未设置筛选条件时导出全部资产。
- `.xlsx` 模板支持项目、资产类型、环境和状态下拉选择。
- `.csv` 文件支持导入和导出，但 CSV 格式本身不支持下拉列表；需要下拉选择时请使用 Excel 模板。

## 11. 接口清单

除登录接口外，其他 `/api/**` 接口都需要携带令牌：

```http
Authorization: Bearer <token>
```

### 11.1 认证

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| `POST` | `/api/auth/login` | 用户登录，返回令牌和用户信息 |
| `POST` | `/api/auth/register` | 提交注册申请，新账号默认停用的只读账号 |
| `PUT` | `/api/account/password` | 当前登录用户修改自己的密码 |

登录响应中的 `user.passwordChangeRecommended` 和 `user.passwordDaysRemaining` 用于临期提醒。密码到期后，受保护 API 返回 HTTP `428`（`PASSWORD_EXPIRED`），仅允许调用修改密码接口；`/api/auth/register` 不需要登录，但新账号始终停用并为 `VIEWER`。

登录请求示例：

```json
{
  "username": "admin",
  "password": "StrongPassword#2026"
}
```

密码需为 8 至 72 位，不含空白字符，并且必须同时包含大写字母、小写字母、数字和特殊符号。管理员创建用户、编辑用户时重置密码，以及用户自助修改密码都使用同一规则。密码修改后最长有效 3 个月，距到期 14 天内登录会提示；过期后必须先修改密码并使用新密码重新登录。忘记密码不提供自助找回，只能联系管理员重置。

### 11.2 总览

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| `GET` | `/api/dashboard` | 获取项目、资产、在线、生产环境和用户统计 |

### 11.3 项目

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| `GET` | `/api/projects` | 查询项目列表 |
| `POST` | `/api/projects` | 新建项目 |
| `PUT` | `/api/projects/{id}` | 编辑项目 |
| `DELETE` | `/api/projects/{id}` | 删除项目，无资产关联时才允许 |

### 11.4 资产

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| `GET` | `/api/assets` | 查询资产，可选 `projectId`、`keyword`；`keyword` 仅匹配内网 IP 和外网 IP |
| `GET` | `/api/assets/{id}` | 查询资产详情和变更记录 |
| `POST` | `/api/assets` | 新建资产 |
| `PUT` | `/api/assets/{id}` | 编辑资产 |
| `DELETE` | `/api/assets/{id}` | 删除资产 |
| `DELETE` | `/api/assets/batch` | 批量删除资产，JSON 数组传入 1 至 100 个资产 ID |
| `POST` | `/api/assets/import` | 上传 CSV 或 Excel 批量导入，字段名为 `file`；可选 `duplicateStrategy=SKIP/MERGE` |
| `POST` | `/api/assets/import/validate` | 预校验 CSV/Excel，不写入资产；可选 `duplicateStrategy=SKIP/MERGE` |
| `GET` | `/api/assets/export` | 导出 CSV，可选 `projectId`、`keyword` |
| `GET` | `/api/assets/export.xlsx` | 导出 Excel，可选 `projectId`、`keyword` |
| `GET` | `/api/assets/template.xlsx` | 下载带下拉选项和样例的 Excel 模板 |
| `GET` | `/api/assets/types` | 查询启用的资产类型下拉选项 |
| `GET` | `/api/assets/regions` | 查询启用的洲、国家/地区、城市/区域字典 |
| `GET` | `/api/assets/imports` | 查询导入审计记录 |
| `GET` | `/api/assets/imports/{id}/failures.csv` | 下载某次导入的失败行 CSV |
| `GET` | `/api/assets/imports/{id}/failures` | 查询可在线修正的失败行 |
| `PUT` | `/api/assets/imports/{importId}/failures/{failureId}/retry` | 修正并重试失败行 |
| `GET` | `/api/assets/topology?projectId={id}` | 查询项目资产节点和关系边 |
| `POST` | `/api/assets/{id}/relations` | 创建同项目资产关系 |
| `DELETE` | `/api/assets/{id}/relations/{relationId}` | 删除源资产关系 |
| `PUT` | `/api/assets/{id}/lifecycle` | 执行受限生命周期流转，参数为 `state` 和可选 `reason` |
| `GET` | `/api/assets/{id}/lifecycle/history` | 查询生命周期流转记录 |

### 11.5 用户

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| `GET` | `/api/users` | 查询用户列表 |
| `POST` | `/api/users` | 新建用户 |
| `PUT` | `/api/users/{id}` | 编辑用户 |
| `DELETE` | `/api/users/{id}` | 删除用户；用户名为 `admin` 的账号不允许删除 |

### 11.6 审计

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| `GET` | `/api/audits` | 查询操作审计日志；支持 `operator`、`action`、`resourceType`、`result`、`keyword`、`from`、`to`、`archived` 筛选，仅管理员可访问 |
| `GET` | `/api/audits/export` | 导出当前相同筛选条件的 CSV，最多 10,000 条 |
| `POST` | `/api/audits/archive?before={ISO日期时间}` | 将截止时间以前的当前日志按每批 500 条迁入独立归档表，可安全重复调用 |

## 12. Docker 运行

`docker-compose.yml` 只负责构建和运行 CMDB 应用，不包含 MySQL，也不保存数据库密码。数据库必须提前准备好，应用通过环境变量连接外部 MySQL。

### 12.1 使用 Docker Compose 构建并启动

先在项目根目录创建 `.env`，填入外部数据库和令牌配置：

```dotenv
SERVER_PORT=8080
DB_URL=jdbc:mysql://your-mysql-host:3306/cmdb?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true
DB_USERNAME=cmdb_app
DB_PASSWORD=replace-with-your-password
JWT_SECRET=replace-with-a-long-random-secret
JWT_EXPIRE_HOURS=12
ADMIN_PASSWORD=replace-with-a-strong-initial-admin-password
```

然后执行：

```bash
docker compose up -d --build
```

查看应用日志：

```bash
docker compose logs -f cmdb
```

停止并删除应用容器：

```bash
docker compose down
```

启动成功后访问：

```text
http://localhost:8080/
```

### 12.2 单独构建应用镜像

```bash
docker build -t orbit-cmdb:1.0.0 .
```

### 12.3 单独启动应用容器

```bash
docker run -d --name orbit-cmdb -p 8080:8080 \
  -e SERVER_PORT=8080 \
  -e DB_URL='jdbc:mysql://your-mysql-host:3306/cmdb?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true' \
  -e DB_USERNAME='cmdb_app' \
  -e DB_PASSWORD='replace-with-your-password' \
  -e JWT_SECRET='replace-with-a-long-random-secret' \
  -e ADMIN_PASSWORD='replace-with-a-strong-initial-admin-password' \
  orbit-cmdb:1.0.0
```

### 12.4 容器构建特性

- Dockerfile 使用 Maven 多阶段构建，最终运行镜像只包含 JRE 和应用 JAR。
- Dockerfile 启用 BuildKit Maven 本地仓库缓存，并在单次 `clean package` 中完成依赖解析、测试和打包，避免额外的离线依赖预取阶段长时间阻塞。
- Maven 依赖单独缓存，修改 Java 源码时可以复用依赖层。
- 运行容器使用非 root 用户。
- 容器文件系统设置为只读，`/tmp` 使用临时文件系统。
- 日志默认写入 `/tmp/logs/cmdb.log` 并按大小滚动，容器平台也可以直接采集标准输出。
- Compose 不负责初始化数据库，也不会创建或管理 MySQL 容器。
- 数据库表结构仍需提前通过 `database/init.sql` 初始化。
- Kubernetes Deployment 配置包含 startup、readiness 和 liveness 探针；Compose 运行时请通过 `/actuator/health/readiness` 手工或由外部编排器检查健康状态。

## 13. Kubernetes 部署

项目提供 `k8s/cmdb.yaml`，包含 Secret 占位模板、双副本 Deployment 和 ClusterIP Service。清单不创建 MySQL，数据库必须提前准备并允许集群访问。首次管理员和默认项目初始化具备多副本竞争容错，但仍建议先执行 `init.sql` 并确认数据库连接稳定。

### 13.1 部署前修改

编辑 `k8s/cmdb.yaml` 中 `Secret` 的以下值：

- `DB_URL`
- `DB_USERNAME`
- `DB_PASSWORD`
- `JWT_SECRET`
- `ADMIN_PASSWORD`

将 Deployment 中的 `image` 改为镜像仓库地址，例如：

```yaml
image: registry.example.com/ops/orbit-cmdb:1.0.0
```

### 13.2 部署命令

```bash
kubectl apply -f k8s/cmdb.yaml
kubectl rollout status deployment/cmdb
kubectl get pods -l app.kubernetes.io/name=cmdb
kubectl get service cmdb
```

清单已配置：

- 滚动更新，保证更新期间至少保留可用副本。
- 启动、存活和就绪探针。
- 非 root 用户运行。
- 只读根文件系统和内存临时目录。
- CPU、内存请求与限制。
- 禁止自动挂载 ServiceAccount Token。
- 提供 Prometheus 抓取路径和端口注解；因监控接口要求管理员令牌，默认将 `prometheus.io/scrape` 设为 `false`。

当前 Deployment 为双副本。首次启动时管理员和默认项目初始化会在数据库唯一约束下处理多副本竞争：一个副本完成创建后，其他副本会重新查询并继续启动。仍应确保所有副本使用同一组 `ADMIN_PASSWORD` 和数据库配置，并在扩容前先执行最新 `database/init.sql`。

Dockerfile 的 Maven 正式打包步骤会执行测试；K8S 清单日志滚动配置为 8MB 单文件、7 个历史文件、56MB 总上限，`/tmp` 内存卷限制为 96Mi。

2026-11-02 的发布为裸机 JAR 部署，没有更新 Docker 镜像或应用 K8S 清单；K8S 清单仍需在目标集群完成 dry-run 和环境配置校验。

### 13.3 健康检查接口

```text
GET /actuator/health/liveness
GET /actuator/health/readiness
GET /actuator/metrics
GET /actuator/prometheus
```

`liveness` 和 `readiness` 健康探针允许匿名访问，便于裸机、Docker 和 Kubernetes 探活；`metrics` 和 `prometheus` 需要携带管理员令牌访问。生产环境建议仍在网关层限制监控接口来源，并为 Prometheus 抓取配置专用的管理员令牌或通过内网网关注入认证头。

## 14. 常见问题

### 14.1 浏览器打不开

检查：

```powershell
Get-NetTCPConnection -LocalPort 8080 -State Listen
```

如果没有监听：

- 检查 Java 是否安装正确。
- 检查 8080 是否被其他程序占用。
- 检查启动窗口中的数据库连接错误。
- 检查 JAR 是否存在。

### 14.2 启动时报数据库表不存在

先执行：

```text
database/init.sql
```

然后确认实际生效的配置连接的是 `cmdb` 数据库。裸机部署时优先检查：

- 启动命令是否带了正确的 `--spring.config.additional-location`，并且路径指向实际存在的外置配置文件。
- 外置 `application-prod.yaml` 中的 `spring.datasource.url` 是否指向 `cmdb`。
- 环境变量 `DB_URL` 是否覆盖了配置文件。
- MySQL 账号是否有当前数据库的表结构读取权限。

### 14.3 登录提示用户名或密码错误

- 确认用户名大小写和密码输入无误。
- 确认用户在 `sys_user` 中存在。
- 确认 `enabled` 为 `1`。
- 如果已经修改过管理员密码，请使用修改后的密码登录。
- 新注册账号需要管理员在“用户权限”页面审核并启用。
- 如果提示密码已过期，请使用“修改密码”完成更新；忘记密码请联系管理员重置。
- 首次安装前请通过 `ADMIN_PASSWORD` 设置符合密码策略的管理员密码。

### 14.4 接口返回“登录已失效”

令牌默认有效期为 12 小时。重新登录即可获取新令牌；修改或重置密码后，修改前签发的令牌会立即失效。用户自助修改密码成功后，页面会清理本地会话并返回登录页。

### 14.5 CSV 或 Excel 导入失败

检查：

- 文件是否为 CSV 或 `.xlsx`。
- 是否保留第一行表头。
- 是否至少包含 7 列。
- 资产名称是否为空。
- 项目名称是否存在且与项目空间中的名称一致。
- 文件是否超过 10MB。
- CSV 文件是否使用 UTF-8 编码。

### 14.6 页面样式或 Vue 未加载

当前页面通过 CDN 加载 Vue 3。如果浏览器所在网络无法访问 `unpkg.com`，页面脚本可能无法执行。生产环境建议将 Vue 前端依赖改为本地构建或本地静态资源。

### 14.7 裸机部署如何确认配置已生效

可以从以下位置确认：

- 启动日志中会显示监听端口和 JPA 表结构校验结果。
- `http://localhost:8080/actuator/health/readiness` 返回 `UP` 表示应用已就绪。
- 如果数据库配置错误，启动日志会出现连接失败、认证失败或表结构校验失败。
- 修改外置配置文件后需要重启应用，Spring Boot JAR 不会自动热加载生产配置。

## 15. 当前验证状态

最近一次已确认的线上发布为 2026-11-06：裸机 JAR 已部署到 `192.168.11.218:/opt/fusx/cmdb`，服务端口为 `9090`，PID 为 `6104`；readiness 为 `UP`、首页 HTTP 200，未认证资产接口返回 HTTP 401。线上 JAR SHA-256 为 `ED42FCF752DE76285A4EC4C6A0995917297D3A826EBFB14A1F14FB6B6B741B37`，与本地最终构建一致。生产 MySQL 5.7 已建立项目编码和三项资产唯一索引、密码有效期字段及独立审计归档表；部署前数据库备份位于 `/opt/fusx/cmdb/backups/cmdb-20261106-155130.sql.gz`。

当前源码已包含资产关系拓扑、生命周期流转、导入预校验与重复合并、失败行在线修正、资产批量删除、审计高级筛选/导出/长期归档，以及统一导航页签样式。2026-11-06 发布前已执行 `node --check src/main/resources/static/app.js`、`git diff --check` 和 JDK 17 下的 `mvn clean verify`，35 项测试全部通过并成功生成 `target/cmdb-1.0.0.jar`。

同日已构建镜像 `orbit-cmdb:1.0.0-20261106-final`，镜像 ID 为 `sha256:69d7959be141ee7f90c6e0f45d1de4a6cca659e80dea86ed495919b8b96663c0`。容器冒烟验证确认 readiness 为 `UP`、首页 HTTP 200、运行用户为 `10001:10001` 且根文件系统只读。离线镜像包位于 `/opt/fusx/cmdb_images/20261106-final/orbit-cmdb-1.0.0-20261106-final.tar.gz`，SHA-256 为 `1FD31C8EA184533AED7CAB440705C349AA9606D083FF27FB9CD1841D3F96DABE`；Compose 配置已通过解析校验。Kubernetes 清单包含 `Secret`、`Deployment`、`Service` 三份资源，目标机未安装 `kubectl`，因此尚未向实际集群应用或执行服务端 dry-run。

此前已完成以下功能验证：

- Maven 打包成功。
- Java 17 JAR 启动成功。
- 8080 端口正常监听。
- README 已补充裸机部署、外置配置文件、前台/后台启动、停止和健康检查说明。
- 管理员登录成功。
- 总览、资产清单、项目空间和用户权限页面可访问。
- 资产、项目和用户新建表单可打开。
- 资产、项目和用户新增、编辑流程已完成回归。
- 资产编辑过程中发现的懒加载事务问题已修复。

当前仍建议继续补充：

- 资产单项删除、项目和用户删除流程的自动化测试。
- CSV/Excel 导入成功、失败和重复数据场景的自动化测试。
- 角色权限隔离的自动化测试。
- `database/init.sql` 在全新 MySQL 与历史库升级两种路径下的自动化测试。
- Kubernetes 清单在具备 `kubectl` 和目标集群访问权限的环境中执行服务端 dry-run 与实际部署验证。

## 16. 生产部署建议

上线前至少完成以下调整：

1. 不要把数据库密码和令牌密钥直接提交到代码仓库。
2. 修改初始管理员密码。
3. 使用独立的最小权限数据库账号，不要长期使用 root。
4. 将自定义令牌替换为成熟的 JWT、OIDC 或企业统一认证。
5. 关闭全开放 CORS，限制允许的来源。
6. 在网关或 Nginx 层启用 HTTPS、限流和访问日志。
7. 数据库结构变更必须先更新 `database/init.sql`，再在测试库验证后发布。
8. 增加数据库备份、恢复和数据保留策略。
9. 增加后端接口测试、前端流程测试和导入数据校验。
10. 将 Vue CDN 依赖改成本地构建资源，减少外部网络依赖。

## 17. 后续迭代方向

- 指标告警规则、日志采集面板和容量趋势分析。
