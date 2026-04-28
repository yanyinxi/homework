# 安全与架构质量审计报告

**审计日期**: 2026-04-27
**审计范围**: 项目全局（后端 + 前端 + 基础设施配置）
**审查文件**: 47 个 Java 源文件、1 个 MyBatis XML Mapper、2 个 Flyway 迁移脚本、17 个前端 TS/Vue 源文件、5 个配置文件
**审查标准**: 五轴审查框架（正确性、可读性、架构、安全、性能）

---

## 审查概览

| 维度 | 结果 |
|------|------|
| SQL 注入防护 | **通过** -- 所有 SQL 使用 `#{}` 参数化，字段名通过三重白名单 |
| 认证授权链 | **基本通过** -- 2 个重要问题 |
| 敏感信息泄露 | **不通过** -- 1 个严重问题，2 个重要问题 |
| 分层架构合规 | **通过** -- Controller/Service/Mapper 分层清晰 |
| 依赖安全 | **需要关注** -- 11 个过期依赖 |
| .gitignore 覆盖 | **基本通过** -- 1 个改善建议 |

**发现问题总计**: 15 个
- 严重 (Critical): 2
- 重要 (Important): 6
- 建议 (Suggestion): 7

---

## 问题列表

### 严重问题 (Critical)

| # | 文件 | 行号 | 问题 | 影响 | 修复建议 |
|---|------|------|------|------|----------|
| 1 | `main/backend/src/main/resources/application.yml` | 103-109 | **API 密钥硬编码并已纳入 Git 版本控制**。`dev-api-key-001`、`admin-api-key-001` 以明文写入配置文件，任何拥有仓库访问权限的人均可获取这些密钥 | 未授权访问：攻击者获取代码后可直接使用这些密钥调用 API，包括删除素材等管理员操作 | (a) 轮换所有密钥；(b) 从 `application.yml` 中移除明文密钥，改为环境变量占位符 `app.security.api-keys[0].key: ${API_KEY_USER}`；(c) 使用 `git filter-branch` 清理历史记录 |
| 2 | `main/frontend/src/services/assetService.ts` | 37 | **前端兜底使用硬编码 API 密钥**。`const API_KEY = import.meta.env.VITE_API_KEY \|\| 'dev-api-key-001'`，若未设置环境变量，密钥直接打包进 JS bundle 中 | 密钥泄露：任何访问网站的用户可通过浏览器 DevTools 查看密钥 | 移除硬编码兜底值，改为构建时强制检查：`const API_KEY = import.meta.env.VITE_API_KEY; if (!API_KEY) throw new Error('VITE_API_KEY is required')` |

### 重要问题 (Important)

| # | 文件 | 行号 | 问题 | 影响 | 修复建议 |
|---|------|------|------|------|----------|
| 3 | `docker-compose.yml` | 10, 31 | **数据库密码硬编码并已纳入 Git**。`POSTGRES_PASSWORD: asset123` 是弱密码且明文写入 docker-compose.yml；`SPRING_DATASOURCE_PASSWORD: asset123` 同步暴露 | 数据库未授权访问：获取仓库访问权限的攻击者可直连数据库 | 使用 `${DB_PASSWORD}` 环境变量替换所有硬编码密码，通过 `.env` 文件（已在 .gitignore）注入实际值 |
| 4 | `docker-compose.yml` / `Dockerfile` | docker-compose:22-32 / Dockerfile:1-7 | **Docker 容器以 root 用户运行**。Dockerfile 无 `USER` 指令，后端容器默认以 root 启动，前端 nginx 基础镜像也可能以 root 运行 | 容器逃逸风险：若应用存在 RCE 漏洞，攻击者获得 root 特权可尝试容器逃逸 | 在 Dockerfile 末尾添加：`RUN addgroup -S app && adduser -S app -G app`，`USER app` |
| 5 | `main/backend/src/main/resources/application.yml` | 88 | **默认 profile 日志级别为 DEBUG**。`com.homework.asset: DEBUG` 若未激活 docker/prod profile 则生效，会打印包含用户数据的完整 SQL 语句 | 日志敏感数据泄露：DEBUG 级别输出完整的 SQL 参数值 | 默认日志级别改为 `INFO`，仅保留 `dev` profile 的 `DEBUG` |
| 6 | `main/backend/src/main/java/com/homework/asset/api/StatsController.java` | 39-45, 50-56, 61-66 | **统计端点缺少显式权限注解**。`/api/v1/stats/**` 仅依赖 SecurityConfig 全局 `.requestMatchers("/api/**").authenticated()`，与 `AssetCommandController` 的显式 `@PreAuthorize` 风格不一致 | 防护深度不足：若全局配置被误改，统计端点可能意外暴露 | 在所有统计端点添加 `@PreAuthorize("hasRole('USER')")`，保持与写端点的显式授权一致性 |
| 7 | `main/backend/src/main/java/com/homework/asset/ingest/IngestBatchService.java` | 70-90 | **`batchCheckExists` IN 子句无参数数量上限**。虽然使用 `?` 占位符保证安全，但当导入批次过大（>5000 条）时，生成的超长 SQL 可能导致数据库报错 | 大批量导入性能/可用性问题 | 分批查询（每批 500 条），或改用 PostgreSQL 临时表 JOIN 方案 |
| 8 | `main/backend/pom.xml` | 各处 | **11 个依赖版本严重过期**。jackson-databind 2.17.2（CVE 高发组件）、PostgreSQL driver 42.7.4、Spring Boot 3.3.4 等均有多版安全修复未应用 | CVE 暴露面扩大 | 优先升级 jackson-databind 和 PostgreSQL driver；Spring Boot 可先升 3.4.x 稳定版 |

### 建议 (Suggestion)

| # | 文件 | 行号 | 建议 | 原因 |
|---|------|------|------|------|
| 9 | `main/backend/src/main/java/com/homework/asset/ingest/excel/ExcelReader.java` | 30-37 | **缺少 Excel Zip Bomb 防护**。XSSFWorkbook 对恶意压缩的超大 `.xlsx` 无大小限制，可能导致 OOM | 添加读取前文件大小校验，或限制最大解析行数 |
| 10 | `main/backend/src/main/java/com/homework/asset/config/RateLimitFilter.java` | 150-159 | **`getClientIp()` 信任 X-Forwarded-For 头**。攻击者可伪造该 Header 绕过 IP 级别限流 | 低影响（仅限流）。生产应通过反向代理剥离该头或仅信任上游代理 IP |
| 11 | `main/backend/src/main/resources/application.yml` | 100 | **CORS `allowed-origins` 无端口限制**。`http://localhost` 可匹配任意端口，开发期便利但生产不够精确 | 生产环境指定具体域名（如 `https://admin.example.com`） |
| 12 | Dockerfile | - | **缺少 HEALTHCHECK 指令**。docker-compose 的 `depends_on` 无法判断容器内应用是否真正就绪 | 添加：`HEALTHCHECK --interval=10s CMD wget -qO- http://localhost:8080/actuator/health \|\| exit 1` |
| 13 | `main/frontend/src/router/index.ts` | 全局 | **前端路由无权限守卫**。删除/上传 UI 元素依赖后端拦截，但页面本身对所有用户可见 | 添加 `meta.requiresAuth` 和 `router.beforeEach` 导航守卫 |
| 14 | `main/backend/src/main/java/com/homework/asset/service/AssetCommandService.java` | 176-179 | **`deleteByQuery` 的 `uploader` 参数缺少字符串长度校验** | 添加 `@Size(max=200)` 或手动检查，防止超长字符串影响查询性能 |
| 15 | 根目录缺少 | - | **缺少 `.env.example` 模板文件**（.gitignore 中 `!.env.example` 对该文件的除外规则已被定义但文件不存在） | 创建 `.env.example` 包含所有环境变量模板和默认值说明 |

---

## 各维度详细审计

### 1. SQL 注入扫描 -- 通过

**审查范围**: 1 个 MyBatis XML Mapper + 1 个 JdbcTemplate 查询

**纵深防护链**:

```
HTTP Request
  -> QueryDslParser.parseSort()          [SortableField 枚举白名单, 7个排序字段]
  -> QueryDslParser.parseFields()        [FilterableField 枚举白名单, 15个字段]
  -> QueryDslParser.parseFilterParam()   [FilterableField + FilterOperator 双重白名单]
  -> AssetMapper.xml <choose>/<when>     [XML 硬编码列名, 无用户字符串拼接]
  -> MyBatis #{} / JDBC ? 占位符         [PreparedStatement 参数绑定]
```

| 检查项 | 结果 |
|--------|------|
| MyBatis `${}` 占位符扫描 | 通过 -- 0 处使用 |
| ORDER BY 字段注入 | 通过 -- `<choose><when test="clause.columnName == 'uploaded_at'">` 硬编码列名 |
| 过滤字段白名单 | 通过 -- `FilterableField.fromParamName()` 枚举查找，未声明返回 400 |
| 排序字段白名单 | 通过 -- `SortableField.fromParamName()` 枚举映射到 DB 列名 |
| 操作符白名单 | 通过 -- `FilterOperator.fromBracket()` 枚举查找 |
| IN 子句值限制 | 通过 -- `MAX_IN_VALUES = 100` |
| JdbcTemplate 参数化 | 通过 -- `?` 占位符 + `params.toArray()` |

**结论**: SQL 注入防护设计为项目最突出的安全优势。在调用链每一层都有独立防护，即使单层被绕过仍有后续兜底。

---

### 2. 认证授权链完整性 -- 基本通过

**过滤器链**: `RateLimitFilter -> ApiKeyAuthFilter -> Spring Security -> @PreAuthorize -> Controller`

**权限矩阵**:

| 端点 | HTTP 方法 | 权限要求 | 显式注解 |
|------|-----------|----------|----------|
| /api/v1/assets | GET | ROLE_USER | 否（全局 /api/** 规则） |
| /api/v1/assets/cursor | GET | ROLE_USER | 否 |
| /api/v1/assets/{id} | GET | ROLE_USER | 否 |
| /api/v1/assets/upload | POST | ROLE_USER | @PreAuthorize("hasRole('USER')") |
| /api/v1/assets/{id} | DELETE | ROLE_ADMIN | @PreAuthorize("hasRole('ADMIN')") |
| /api/v1/assets/batch | DELETE | ROLE_ADMIN | @PreAuthorize("hasRole('ADMIN')") |
| /api/v1/assets/by-query | DELETE | ROLE_ADMIN | @PreAuthorize("hasRole('ADMIN')") |
| /api/v1/stats/* | GET | ROLE_USER | 否（全局 /api/** 规则） |

**说明**: 查询和统计端点缺少显式 `@PreAuthorize`，虽然当前由 SecurityConfig 全局 `/api/**` 规则兜底保护，但不符合防御深度原则。应将 `@PreAuthorize` 作为标准注解风格应用到所有端点。

---

### 3. 敏感信息泄露扫描 -- 需整改

| 文件 | 敏感内容 | Git 跟踪 | 风险等级 |
|------|----------|----------|----------|
| `main/backend/src/main/resources/application.yml` | API keys: `dev-api-key-001`, `admin-api-key-001` | **是** | **严重** |
| `docker-compose.yml` | DB password: `asset123` | **是** | 重要 |
| `main/frontend/src/services/assetService.ts` | 兜底 API key: `dev-api-key-001` | **是** | **严重** |
| `main/frontend/.env` | VITE_API_KEY | 否（正确排除） | 通过 |

---

### 4. 分层架构合规性 -- 通过

```
api/    -> Controller (3个) + DTO + Exception
service/ -> Service (3个) + IngestBatchService
mapper/  -> AssetMapper (extends BaseMapper<Asset>)
domain/  -> Asset Entity
```

| 检查项 | 结果 |
|--------|------|
| Controller 不注入 Mapper | 通过 |
| Service 不依赖 HttpServletRequest/Response | 通过 |
| Mapper 不含业务逻辑 | 通过 |
| 单向依赖 Controller->Service->Mapper | 通过 |
| DTO 未泄露到 Service 层 | 通过 |

---

### 5. 依赖安全 -- 需关注

**关键过期依赖（按安全风险排序）**:

| 依赖 | 当前 | 最新 | CVE 风险 |
|------|------|------|----------|
| jackson-databind | 2.17.2 | 2.21.2 | **高** -- 反序列化漏洞 |
| PostgreSQL Driver | 42.7.4 | 42.7.10 | **高** -- 安全修复 |
| Spring Boot | 3.3.4 | 3.4.x (stable) | 中 |
| MyBatis-Plus | 3.5.7 | 3.5.16 | 低 |
| Apache POI | 5.3.0 | 5.5.1 | 低 |
| Flyway | 10.10.0 | 12.4.0 | 低 |

**前端**: npm audit 因仓库镜像不可用未能执行，建议在生产网络环境中运行。

---

### 6. .gitignore 覆盖 -- 基本通过

- `node_modules/`, `target/`, `.env/.env.*`, IDE 文件, `*.log` -- 全部正确排除
- `docker-compose.yml`, `application.yml` -- 含敏感值但 **不能** 简单 gitignore（它们是部署所需的配置），正确做法是移除其中的敏感值
- 定义了 `!.env.example` 规则但文件尚不存在

---

## 正面发现 (Positive Findings)

审查过程中发现的优秀实践：

1. **SQL 注入纵深防御设计** -- 三重白名单 + 参数化查询链式防护，是本项目最主要的安全优势
2. **API Envelope 统一响应格式** -- `ApiEnvelope<T>` 保证一致性，GlobalExceptionHandler 集中处理
3. **幂等 ETL 导入** -- `ON CONFLICT DO UPDATE` + `raw_record` 审计保留
4. **Cursor/Keyset 分页** -- 避免深度 OFFSET 扫描
5. **Schema 查询驱动建索引** -- 每个索引有场景注释，无幻想索引
6. **Testcontainers 真实 PG 测试** -- 避免 H2 兼容性问题
7. **Flyway 版本化 Schema** -- DDL 与代码同步
8. **RateLimitFilter 内存泄漏防护** -- 定期清理 + Daemon 线程
9. **前端无 v-html/innerHTML XSS 风险** -- grep 扫描 0 命中
10. **Controller UUID 格式校验** -- `@Pattern(regexp="^[0-9a-f]{8}-...")`

---

## 整改优先级

### P0 -- 立即修复（阻断合并）
1. 轮换并移除 `application.yml` 中硬编码的 API 密钥（问题 #1）
2. 移除 `assetService.ts` 中硬编码的 API 密钥兜底值（问题 #2）

### P1 -- 本周内修复
3. `docker-compose.yml` 密码改为环境变量引用（问题 #3）
4. Dockerfile 添加非 root 用户（问题 #4）
5. 默认日志级别改为 INFO（问题 #5）
6. StatsController / AssetController 添加显式 `@PreAuthorize`（问题 #6）
7. 创建 `.env.example` 模板文件（问题 #15）

### P2 -- 下次迭代
8. 升级 jackson-databind + PostgreSQL driver（问题 #8）
9. IngestBatchService IN 子句分页优化（问题 #7）
10. Dockerfile 添加 HEALTHCHECK（问题 #12）

### P3 -- 建议采纳
11. ExcelReader Zip Bomb 防护（问题 #9）
12. CORS 生产环境收紧（问题 #11）
13. 前端路由守卫（问题 #13）
14. uploader 参数添加长度校验（问题 #14）

---

*审计工具：人工代码审查 + Maven `versions:display-dependency-updates` + grep 模式扫描 + Git 历史追踪*
*审查框架：五轴审查框架（正确性、可读性、架构、安全、性能）*
