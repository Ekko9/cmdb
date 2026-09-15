# Orbit CMDB

Orbit CMDB 是一个轻量级的配置管理数据库（Configuration Management Database），用于统一维护项目、基础设施资产和用户权限。

当前版本为 `1.0.0`，后端使用 Spring Boot 2.7.18，前端使用 Vue 3，数据访问使用 Spring Data JPA，数据库使用 MySQL 5.7+。应用内置前端页面，打包后只需要启动一个 Java 服务即可访问。

## 1. 功能概览

### 1.1 登录与权限

- 用户名密码登录。
- 使用 HMAC-SHA256 签名令牌访问受保护接口。
- 令牌默认有效期为 12 小时。
- 支持三种角色：
  - `ADMIN`：管理员，当前页面可进入用户权限模块。
  - `OPERATOR`：运维人员，角色字段已支持。
  - `VIEWER`：只读用户，角色字段已支持。
- 用户可以启用或停用，停用用户无法登录。

当前版本已经在页面层隐藏非管理员的“用户权限”入口，但项目和资产接口的细粒度角色授权仍待完善。上线前不要仅依赖前端按钮隐藏来实现安全隔离。

### 1.2 项目空间

- 新建、编辑和删除项目。
- 支持项目编码、负责人和描述。
- 自动统计每个项目下的资产数量。
- 项目下仍有资产时禁止删除，必须先删除资产或将资产迁移到其他项目。

### 1.3 资产清单

- 新建、编辑和删除资产。
- 支持资产类型、环境、状态、区域、内网 IP、外网 IP、主机名和描述。
- 支持按资产名称、IP、主机名、资产类型、环境、状态和区域搜索。
- 支持 CSV 和 Excel（`.xlsx`）批量导入。
- 支持 CSV 和 Excel（`.xlsx`）导出。

### 1.4 总览仪表盘

当前总览页面展示：

- 项目总数。
- 资产总数。
- 在线资产数。
- 生产环境资产数。
- 最近资产列表。

## 2. 技术栈

| 分类 | 选型 |
| --- | --- |
| 后端框架 | Spring Boot 2.7.18、Spring MVC |
| 开发语言 | Java 8 |
| ORM | Spring Data JPA、Hibernate |
| 数据库 | MySQL 5.7+ |
| 密码加密 | BCrypt |
| 接口认证 | HMAC-SHA256 签名令牌 |
| 前端 | Vue 3 CDN、原生 HTML/CSS/JavaScript |
| CSV | Apache Commons CSV 1.10.0 |
| 打包方式 | Spring Boot Fat JAR |
| 容器化 | Docker、Docker Compose、Kubernetes |
| 健康检查 | Spring Boot Actuator liveness/readiness |

## 3. 项目目录

```text
cmdb/
├─ database/
│  ├─ init.sql                    # 数据库、表和索引初始化脚本
│  ├─ asset-import-template.csv  # 资产导入 CSV 模板，包含样例
│  └─ asset-import-template.xlsx # 资产导入 Excel 模板，包含下拉选项和样例
├─ src/
│  └─ main/
│     ├─ java/com/cmdb/
│     │  ├─ config/               # 认证、异常处理、初始化和 Web 配置
│     │  ├─ controller/           # 登录、总览、项目、资产和用户接口
│     │  ├─ entity/               # User、Project、Asset 和资产类型实体
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

- JDK 8 或更高版本。
- Maven 3.8 或更高版本。
- MySQL 5.7 或更高版本。
- 浏览器：Chrome、Edge 或其他现代浏览器。
- 浏览器需要能够加载 Vue 3 CDN；当前前端依赖 `unpkg.com`。

### 4.2 本机开发环境

当前开发机使用以下目录：

| 组件 | 路径 |
| --- | --- |
| 项目目录 | `D:\workspace\cmdb` |
| JDK 8 | `D:\jude\jdk8` |
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

脚本会完成以下操作：

1. 创建 `cmdb` 数据库。
2. 创建 `sys_user` 用户表。
3. 创建 `cmdb_project` 项目表。
4. 创建 `cmdb_asset` 资产表。
5. 创建 `cmdb_asset_type` 资产类型选项表并写入默认类型。
6. 创建项目与资产之间的外键关系。
7. 创建常用查询索引。

脚本使用 `CREATE DATABASE IF NOT EXISTS` 和 `CREATE TABLE IF NOT EXISTS`，重复执行不会重复创建已有对象。

### 5.2 表关系

```text
sys_user       用户和权限
cmdb_project   项目空间
cmdb_asset     资产，必须关联一个项目
cmdb_asset_type 资产类型下拉选项

cmdb_project 1 ─── N cmdb_asset
cmdb_asset_type 1 ─── N cmdb_asset（通过类型编码使用）
```

### 5.3 启动时的数据库校验

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

## 6. 应用配置

配置文件为：

```text
src/main/resources/application.yaml
```

主要配置项如下：

| 配置项 | 作用 | 当前说明 |
| --- | --- | --- |
| `server.port` | Web 服务端口 | 默认 `8080` |
| `spring.datasource.url` | MySQL JDBC 地址 | 指向 `cmdb` 数据库 |
| `spring.datasource.username` | 数据库用户 | 当前为本机开发配置 |
| `spring.datasource.password` | 数据库密码 | 仅保存在本机配置，不写入本文档 |
| `spring.jpa.hibernate.ddl-auto` | JPA 表结构策略 | 当前为 `validate` |
| `spring.servlet.multipart.max-file-size` | 单个上传文件大小 | `10MB` |
| `spring.servlet.multipart.max-request-size` | 请求最大大小 | `10MB` |
| `cmdb.jwt-secret` | 令牌签名密钥 | 本机开发配置，生产必须更换 |
| `cmdb.jwt-expire-hours` | 令牌有效时长 | 默认 `12` 小时 |
| `management.endpoints.web.exposure.include` | 暴露健康检查接口 | `health,info` |

仓库中的 `application.yaml` 只保留安全占位值。启动时请通过环境变量、密钥管理服务或部署平台的加密配置提供真实数据库连接信息和令牌密钥，避免把真实密码提交到代码仓库。

PowerShell 示例：

```powershell
$env:DB_URL = 'jdbc:mysql://your-mysql-host:3306/cmdb?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true'
$env:DB_USERNAME = 'cmdb_app'
$env:DB_PASSWORD = 'replace-with-your-password'
$env:JWT_SECRET = 'replace-with-a-long-random-secret'
& 'D:\jude\jdk8\bin\java.exe' -jar 'target\cmdb-1.0.0.jar'
```

## 7. 启动方式

### 7.1 使用已打包 JAR 启动

项目当前可直接启动的产物为：

```text
D:\workspace\cmdb\target\cmdb-1.0.0.jar
```

PowerShell 启动命令：

```powershell
& 'D:\jude\jdk8\bin\java.exe' `
  -jar 'D:\workspace\cmdb\target\cmdb-1.0.0.jar'
```

启动成功后访问：

```text
http://localhost:8080/
```

### 7.2 使用 Maven 重新打包

```powershell
$env:JAVA_HOME = 'D:\jude\jdk8'
& 'D:\jude\maven\bin\mvn.cmd' clean package -DskipTests
```

打包成功后会生成：

```text
target/cmdb-1.0.0.jar
```

项目当前还没有完整的自动化测试用例，`-DskipTests` 只表示跳过测试执行，不代表代码不需要回归验证。

### 7.3 检查服务是否启动

检查 8080 端口：

```powershell
Get-NetTCPConnection -LocalPort 8080 -State Listen
```

如果端口已监听，打开浏览器访问 `http://localhost:8080/`。

## 8. 首次登录

首次启动时，`DataInitializer` 会自动创建以下数据：

- 管理员账号：`admin`
- 初始密码：`admin123`
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

### 9.3 资产清单

新建资产时：

1. 填写资产名称。
2. 选择所属项目。
3. 按需填写资产类型、环境、状态、区域、IP、主机名和描述。
4. 点击“保存”。

资产名称和项目为必填字段。资产编辑时可以修改已有字段，但资产仍必须关联有效项目。

搜索框支持以下内容：

- 资产名称。
- 内网 IP。
- 外网 IP。
- 主机名。
- 资产类型。
- 环境。
- 状态。
- 区域。

### 9.4 用户权限

管理员登录后可以进入“用户权限”页面：

1. 新建用户时需要填写用户名和密码。
2. 用户名不能重复。
3. 可以设置显示名称和角色。
4. 编辑用户时可以修改显示名称、角色、启用状态和密码。
5. 停用用户无法继续登录。

当前页面只向前端返回用户基本信息，不返回密码哈希。

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
| 9 | `区域` | 否 | `杭州` | 地域或机房区域 |
| 10 | `描述` | 否 | `业务 Web 服务器` | 资产补充说明 |

示例数据：

```csv
名称,类型,环境,内网IP,外网IP,主机名,项目名称,状态,区域,描述
web-01,SERVER,PRODUCTION,10.0.0.10,203.0.113.10,web-01,默认项目,ONLINE,杭州,业务 Web 服务器
```

### 10.3 导入规则

- 第一行为表头，字段按位置读取。
- 文件建议使用 UTF-8 编码。
- `名称` 和 `项目名称` 必须有效。
- 项目名称不存在时，该行会被跳过并返回错误信息。
- 少于 7 列的行会被跳过。
- 未填写状态时默认使用 `ONLINE`。
- 导入结果会显示成功数量、跳过数量和错误行信息。
- 当前实现不会根据名称自动更新已有资产，重复导入会创建新记录。

### 10.4 页面操作

在“资产清单”页面：

- 点击“下载 Excel 模板”获取带下拉选项和样例的模板。
- 点击“导入文件”选择 `.csv` 或 `.xlsx` 文件。
- 点击“导出 CSV”下载 CSV 格式资产清单。
- 点击“导出 Excel”下载 Excel 格式资产清单。
- 搜索框有内容时，导出结果只包含匹配的资产。
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

登录请求示例：

```json
{
  "username": "admin",
  "password": "admin123"
}
```

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
| `GET` | `/api/assets` | 查询资产，可选 `projectId`、`keyword` |
| `POST` | `/api/assets` | 新建资产 |
| `PUT` | `/api/assets/{id}` | 编辑资产 |
| `DELETE` | `/api/assets/{id}` | 删除资产 |
| `POST` | `/api/assets/import` | 上传 CSV 或 Excel 批量导入，字段名为 `file` |
| `GET` | `/api/assets/export` | 导出 CSV，可选 `projectId`、`keyword` |
| `GET` | `/api/assets/export.xlsx` | 导出 Excel，可选 `projectId`、`keyword` |
| `GET` | `/api/assets/template.xlsx` | 下载带下拉选项和样例的 Excel 模板 |
| `GET` | `/api/assets/types` | 查询启用的资产类型下拉选项 |

### 11.5 用户

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| `GET` | `/api/users` | 查询用户列表 |
| `POST` | `/api/users` | 新建用户 |
| `PUT` | `/api/users/{id}` | 编辑用户 |
| `DELETE` | `/api/users/{id}` | 删除用户 |

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
  orbit-cmdb:1.0.0
```

### 12.4 容器构建特性

- Dockerfile 使用 Maven 多阶段构建，最终运行镜像只包含 JRE 和应用 JAR。
- Maven 依赖单独缓存，修改 Java 源码时可以复用依赖层。
- 运行容器使用非 root 用户。
- 容器文件系统设置为只读，`/tmp` 使用临时文件系统。
- Compose 不负责初始化数据库，也不会创建或管理 MySQL 容器。
- 数据库表结构仍需提前通过 `database/init.sql` 初始化。

## 13. Kubernetes 部署

项目提供 `k8s/cmdb.yaml`，包含 Secret 占位模板、双副本 Deployment 和 ClusterIP Service。清单不创建 MySQL，数据库必须提前准备并允许集群访问。

### 13.1 部署前修改

编辑 `k8s/cmdb.yaml` 中 `Secret` 的以下值：

- `DB_URL`
- `DB_USERNAME`
- `DB_PASSWORD`
- `JWT_SECRET`

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

本地没有执行 Docker 镜像构建；需要部署到 Kubernetes 时，使用 CI/CD 或具备 Docker/BuildKit 的构建环境根据 `Dockerfile` 构建并推送镜像。

### 13.3 健康检查接口

```text
GET /actuator/health/liveness
GET /actuator/health/readiness
```

接口仅用于容器和 Kubernetes 探针，不返回数据库连接细节。

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

然后确认 `application.yaml` 连接的数据库名称为 `cmdb`。

### 14.3 登录提示用户名或密码错误

- 确认用户名大小写和密码输入无误。
- 确认用户在 `sys_user` 中存在。
- 确认 `enabled` 为 `1`。
- 如果已经修改过管理员密码，初始密码 `admin123` 不再适用。

### 14.4 接口返回“登录已失效”

令牌默认有效期为 12 小时。重新登录即可获取新令牌。

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

## 15. 当前验证状态

已完成以下验证：

- Maven 打包成功。
- Java 8 JAR 启动成功。
- 8080 端口正常监听。
- 管理员登录成功。
- 总览、资产清单、项目空间和用户权限页面可访问。
- 资产、项目和用户新建表单可打开。
- 资产、项目和用户新增、编辑流程已完成回归。
- 资产编辑过程中发现的懒加载事务问题已修复。

当前仍建议继续补充：

- 资产、项目和用户删除流程的自动化测试。
- CSV 导入成功、失败和重复数据场景测试。
- 角色权限隔离测试。
- 数据库迁移和生产部署测试。

## 16. 生产部署建议

上线前至少完成以下调整：

1. 不要把数据库密码和令牌密钥直接提交到代码仓库。
2. 修改初始管理员密码。
3. 使用独立的最小权限数据库账号，不要长期使用 root。
4. 将自定义令牌替换为成熟的 JWT、OIDC 或企业统一认证。
5. 关闭全开放 CORS，限制允许的来源。
6. 在网关或 Nginx 层启用 HTTPS、限流和访问日志。
7. 使用 Flyway 或 Liquibase 管理数据库结构变更。
8. 增加数据库备份、恢复和数据保留策略。
9. 增加后端接口测试、前端流程测试和导入数据校验。
10. 将 Vue CDN 依赖改成本地构建资源，减少外部网络依赖。

## 17. 后续迭代方向

- 资产详情页和资产变更记录。
- 分页、排序和多条件筛选。
- 批量导入失败行下载和导入审计。
- 更细的角色与资源权限矩阵。
- 数据库迁移版本管理。
- 操作审计日志。
- 健康检查、日志滚动和监控指标。
