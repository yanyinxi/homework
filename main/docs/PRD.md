# 产品需求文档：视频素材查询服务

> 版本：1.0 | 日期：2026-04-23 | 状态：已实现（对照交付）

---

## 目录

1. [项目背景与目标](#1-项目背景与目标)
2. [用户角色与使用场景](#2-用户角色与使用场景)
3. [功能需求](#3-功能需求)
   - 3.1 数据导入 / ETL
   - 3.2 资产查询与过滤
   - 3.3 聚合统计
   - 3.4 前端界面
4. [非功能需求](#4-非功能需求)
5. [数据模型概要](#5-数据模型概要)
6. [API 接口规范](#6-api-接口规范)
7. [验收标准](#7-验收标准)
8. [已实现清单 vs 规划中](#8-已实现清单-vs-规划中)
9. [技术决策记录](#9-技术决策记录)

---

## 1. 项目背景与目标

### 背景

三份来自不同系统的视频素材数据集（XLS 格式），字段命名语言、时间格式、文件大小单位、审核状态枚举值、标签分隔符、平台名称编码均不一致。这是异构数据集成（Heterogeneous Data Integration）问题，而非简单 CRUD。

### 目标

| 目标 | 具体指标 |
|------|---------|
| 数据统一建模 | 三份数据集归一到单张 `assets` 表，无精度损失 |
| 只读查询 API | 支持多字段过滤 + 排序 + 分页 + 稀疏字段投影 |
| 聚合统计 | 三条业务查询（上传人平均大小、Top 标签、平台通过率）有结果 |
| 工程完整性 | 防 SQL 注入、幂等导入、可测试、可 Docker 一键启动 |
| 可解释性 | 每个技术决策能讲清楚"为什么这样、为什么不那样、未来如何演进" |

### 范围限定

- **只读服务**：不包含素材上传、编辑、删除功能
- **无鉴权**：当前版本不实现 API Key 或 OAuth（已知局限性，已在改进清单中）
- **单租户**：不预留 `tenant_id`（生产版本需补充）

---

## 2. 用户角色与使用场景

### 角色定义

| 角色 | 描述 | 主要诉求 |
|------|------|---------|
| 数据运营 | 负责管理和分析视频素材库 | 快速检索素材、查看审核状态、了解各平台质量 |
| 投放策略分析师 | 制定广告投放决策 | 各平台审核通过率、素材标签分布、文件规模分析 |
| 技术评审 / 面试官 | 评审架构设计质量 | 查看代码结构、ETL 处理逻辑、API 设计是否工程成熟 |

### 核心使用场景

**场景 A：素材检索**
数据运营在管理后台输入过滤条件（状态=已通过、标签包含"节日"、文件大小不超过 500MB），查看符合条件的素材列表，并按上传时间倒序排列，每页展示 20 条。

**场景 B：统计决策**
投放策略分析师打开 Dashboard，查看各平台审核通过率柱状图，判断哪个平台素材质量更高，指导后续采买预算分配。

**场景 C：数据导入**
运维人员将三份 XLS 文件放入 `samples/` 目录，执行 `--ingest=all`，系统幂等写入所有素材，支持重跑不产生重复记录。

---

## 3. 功能需求

### 3.1 数据导入 / ETL

#### 3.1.1 数据集差异处理

系统必须正确处理以下三份数据集的异构问题：

| 维度 | 数据集 1 | 数据集 2 | 数据集 3 | 归一化目标 |
|------|---------|---------|---------|-----------|
| 时间格式 | Excel 序列号整数（如 45540） | Excel 序列号含小数（如 45413.79） | Unix 秒时间戳（如 1715336373） | `TIMESTAMPTZ` UTC |
| 文件大小 | 字符串 `"63.76MB"` | 字节整数（如 106268720） | 数值 + 单位双列（`541.2` + `MB`） | `BIGINT` bytes |
| 审核状态 | 待审核 / 已通过 / 已拒绝 | pending / approved / rejected | 两套混用（pending / 通过 / 已通过） | `pending/approved/rejected` |
| 标签格式 | 分号分隔（`节日;促销`） | 逗号分隔（`生活,搞笑`） | Python list 字符串（`"['品牌', '测评']"`） | `text[]` |
| 平台命名 | 无 | `千川` | `qianchuan` / `千川`（混用） | canonical code |

#### 3.1.2 五个 Normalizer（原子处理单元）

每个 Normalizer 是纯函数，独立可单元测试：

| Normalizer | 输入类型 | 输出类型 | 关键边界 |
|-----------|---------|---------|---------|
| `DateNormalizer` | Excel 序列号 / Unix 秒 | `Instant` | Excel 1900 年闰年 Bug（基准 1899-12-30）；`>1e9` 判为 Unix 秒 |
| `SizeNormalizer` | 字符串含单位 / Long bytes / 数值+单位 | `Long bytes` | BigDecimal 避免浮点误差；KB/MB/GB/B 因子映射 |
| `StatusNormalizer` | 中英文混合状态值 | `String` canonical | 双向映射表；未知值抛 `EtlNormalizeException` |
| `PlatformNormalizer` | 千川 / qianchuan / N/A | `String` / `null` | 大小写归一；`N/A` → `null` |
| `TagNormalizer` | `;`分隔 / `,`分隔 / Python list 字符串 | `String[]` | Jackson 解析（禁止 eval）；trim 空白 |

#### 3.1.3 导入运行时需求

- `IngestRunner` 支持参数：`--ingest=1|2|3|all`、`--dry-run`、`--limit=N`
- 每批次单事务，失败整批回滚
- 幂等：基于 `UNIQUE(source_dataset, source_id)` + `ON CONFLICT DO UPDATE`
- 支持重跑，不产生重复记录

### 3.2 资产查询与过滤

#### 3.2.1 列表查询（核心功能）

`GET /api/v1/assets` 支持以下能力：

**多字段过滤（bracket-style DSL）**

| 操作符 | 语法 | 示例 |
|-------|------|------|
| 等于 | `field=v` / `field[eq]=v` | `status=approved` |
| 不等于 | `field[ne]=v` | `status[ne]=rejected` |
| 大于 / 大于等于 | `field[gt]=v` / `field[gte]=v` | `file_size_bytes[gte]=1048576` |
| 小于 / 小于等于 | `field[lt]=v` / `field[lte]=v` | `file_size_bytes[lte]=524288000` |
| 枚举包含 | `field[in]=a,b,c` | `status[in]=approved,pending` |
| 模糊匹配 | `field[like]=x` | `uploader[like]=张` |
| 标签包含 | `tags[has]=x` | `tags[has]=节日` |

**可过滤字段白名单**：`status`、`uploader`、`city`、`platform`、`title`、`tags`、`file_size_bytes`、`uploaded_at`、`source_dataset`
`uploaded_at[gt/gte/lt/lte]` 支持 ISO-8601 或 epoch 秒/毫秒输入，统一按 `TIMESTAMPTZ` 比较。

**排序**：`sort=uploaded_at:desc,file_size_bytes:asc`（`:desc` / `:asc`，可多字段）

**分页**：`page=1&page_size=20`（默认 20，上限 200），响应含 `total`

**稀疏字段投影**：`fields=title,status,uploader`（DB 层 SELECT 列投影 + DTO 剥字段双保险）

#### 3.2.2 详情查询

`GET /api/v1/assets/{id}` 返回单条素材完整信息，支持 `?fields=a,b,c` 精简输出。

#### 3.2.3 安全护栏（必须实现）

1. 字段白名单：`FilterableFields` / `SortableFields` / `ReturnableFields` 枚举验证，未知字段返回 400
2. 操作符白名单：未知操作符返回 400
3. 参数化查询：所有值走 MyBatis `#{}` 占位符，零字符串拼接
4. 分页上限：`page_size ≤ 200`
5. 查询超时：`statement_timeout = 3s`
6. CORS：仅开放前端 origin

### 3.3 聚合统计

三条固定聚合查询，无分页，结果直接返回数组：

**Q1：上传人平均文件大小**（`GET /api/v1/stats/uploader-avg-size`）

对 `status='approved'` 的素材，按 `uploader` 分组，计算平均文件大小，按均值降序返回。

**Q2：标签 Top 5**（`GET /api/v1/stats/top-tags`）

展开 `tags` 数组，按标签出现频次降序，返回 Top 5。

**Q3：各平台审核通过率**（`GET /api/v1/stats/platform-approval`）

对有 `platform` 的素材，计算各平台总量、通过量、通过率百分比，按通过率降序返回。

### 3.4 前端界面

三个路由页面，使用 Vue 3 + Element Plus + ECharts：

| 路由 | 页面 | 核心功能 |
|------|------|---------|
| `/` | Dashboard | ECharts 可视化 Q1/Q2/Q3（柱状图 + 饼图 + 分组柱状图） |
| `/assets` | AssetList | 表格 + FilterBar + SortControl + FieldSelector + 分页 |
| `/assets/:id` | AssetDetail | 详情卡片 + `?fields` 开关（完整/精简切换） |

**状态管理**：Pinia `assetStore` 管理查询条件，与路由 query 双向绑定（刷新不丢状态）。

**前后端契约**：`utils/queryBuilder.ts` 是唯一序列化来源，把 UI 状态转成 DSL 字符串。

---

## 4. 非功能需求

### 4.1 性能

| 指标 | 要求 | 实现手段 |
|------|------|---------|
| 列表查询响应 | P99 < 200ms（数据量 ~75 行） | B-tree + 组合索引 |
| 聚合查询响应 | P99 < 500ms | 预置索引（status_uploader / tags_gin / platform） |
| 分页防护 | `page_size ≤ 200` | API 层参数校验 |
| 查询超时 | 单次 SQL `statement_timeout = 3s` | 数据库会话级设置 |
| ETL 吞吐 | 全量 75 行 < 5s | 单次批量 upsert |

### 4.2 安全

| 要求 | 实现方式 |
|------|---------|
| 防 SQL 注入 | 字段名三重白名单枚举 + MyBatis `#{}` 参数化 |
| 排序安全 | `SortableField` 枚举映射到 DB 列名，不拼接原始字符串 |
| 操作符安全 | `FilterOperator` 枚举白名单，未知操作符 400 |
| CORS | 仅允许配置的前端 origin |
| 输入长度 | `page_size ≤ 200`，字符串过滤值长度不超过 500 |

### 4.3 可维护性

| 要求 | 实现方式 |
|------|---------|
| 数据集扩展 | 新增数据集只需实现 `DatasetAdapter` 接口，不改 schema 和 API |
| Schema 版本化 | Flyway 管理迁移脚本，版本可追溯 |
| 代码格式 | Spotless 强制统一（`mvn spotless:apply`） |
| 可测试 | 5 个 Normalizer + QueryDslParser 单元测试；Testcontainers 集成测试 |
| 一键启动 | docker-compose 一命令启动全套环境 |

### 4.4 可观测性（当前范围）

- Spring Boot Actuator `/actuator/health` 健康检查
- 结构化日志（SLF4J + Logback）
- Swagger UI（`/swagger-ui.html`）自动生成 API 文档

---

## 5. 数据模型概要

### 主表：`assets`

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| `id` | UUID | PK, DEFAULT gen_random_uuid() | 全局唯一主键 |
| `source_dataset` | SMALLINT | NOT NULL | 数据集来源（1/2/3） |
| `source_id` | TEXT | NOT NULL | 原始 ID（A0001 / asset_001 / vid0001） |
| `ingested_at` | TIMESTAMPTZ | NOT NULL, DEFAULT now() | 导入时间戳 |
| `title` | TEXT | NOT NULL | 归一化后标题 |
| `uploader` | TEXT | NOT NULL | 上传人 |
| `uploaded_at` | TIMESTAMPTZ | NOT NULL | 上传时间（UTC） |
| `file_size_bytes` | BIGINT | NOT NULL, CHECK >= 0 | 文件大小（字节） |
| `status` | TEXT | NOT NULL, CHECK IN ('pending','approved','rejected') | 审核状态 |
| `tags` | TEXT[] | NOT NULL, DEFAULT '{}' | 标签数组 |
| `city` | TEXT | nullable | 所在城市 |
| `platform` | TEXT | nullable | 投放平台 canonical code |
| `reviewer` | TEXT | nullable | 审核人（仅数据集 1） |
| `remark` | TEXT | nullable | 备注（仅数据集 1） |
| `resolution` | TEXT | nullable | 分辨率（仅数据集 2） |
| `duration_sec` | INT | nullable | 时长秒（仅数据集 3） |
| `extra` | JSONB | NOT NULL, DEFAULT '{}' | 稀疏扩展字段 |
| `raw_record` | JSONB | NOT NULL | 原始记录（审计/重跑 ETL） |

**唯一约束**：`UNIQUE(source_dataset, source_id)`，支持幂等 upsert。

### 索引一览

| 索引名 | 类型 | 字段 | 对应查询场景 |
|-------|------|------|------------|
| `idx_assets_status_uploader` | B-tree 复合 | (status, uploader) | Q1 聚合 |
| `idx_assets_tags_gin` | GIN | tags | `tags @>` 数组包含过滤（非 UNNEST 聚合加速） |
| `idx_assets_platform` | B-tree | platform | Q3 聚合 |
| `idx_assets_uploaded_at` | B-tree DESC | uploaded_at | 列表默认排序 |
| `idx_assets_file_size_bytes` | B-tree | file_size_bytes | 范围过滤 + 排序 |
| `idx_assets_city` | B-tree | city | 城市过滤 |
| `idx_assets_extra_gin` | GIN (jsonb_path_ops) | extra | JSONB 路径查询 |

---

## 6. API 接口规范

### 统一响应信封

```json
// 成功
{ "code": 0, "message": "ok", "data": { ... } }

// 错误
{ "code": 400, "message": "Invalid filter field: 'unknownField'", "errors": [...] }
```

### 6.1 GET /api/v1/assets — 列表查询

**请求参数**

| 参数 | 类型 | 默认值 | 说明 |
|------|------|-------|------|
| `{field}[op]=value` | string | — | 过滤条件（bracket-style DSL） |
| `sort` | string | `uploaded_at:desc,id:desc` | 排序，格式 `field:dir,field2:dir`；后端会追加 `id DESC` 保证稳定分页 |
| `fields` | string | — | 稀疏投影，逗号分隔字段名 |
| `page` | int | 1 | 页码（从 1 开始） |
| `page_size` | int | 20 | 每页条数（上限 200） |

**响应体**（`data` 字段）

```json
{
  "items": [
    {
      "id": "550e8400-...",
      "title": "冬季促销视频",
      "uploader": "张三",
      "uploadedAt": "2024-08-15T10:00:00Z",
      "fileSizeBytes": 66668134,
      "status": "approved",
      "tags": ["节日", "促销"],
      "city": "北京",
      "platform": "qianchuan",
      "sourceDataset": 1,
      "sourceId": "A0001"
    }
  ],
  "total": 75,
  "page": 1,
  "pageSize": 20
}
```

### 6.2 GET /api/v1/assets/{id} — 详情查询

**路径参数**：`id`（UUID）

**查询参数**：`fields`（可选，逗号分隔字段名）

**响应体**（`data` 字段）：同列表单条，包含所有字段（`extra` / `rawRecord` 可选）。

**错误**：ID 不存在返回 `404`。

### 6.3 GET /api/v1/stats/uploader-avg-size — Q1 聚合

**响应体**（`data` 字段）

```json
[
  {
    "uploader": "李四",
    "approvedCount": 5,
    "avgSizeBytes": 89478485,
    "avgSizeHuman": "85 MB"
  }
]
```

### 6.4 GET /api/v1/stats/top-tags — Q2 聚合

**响应体**（`data` 字段）

```json
[
  { "tag": "促销", "count": 12 },
  { "tag": "节日", "count": 9 }
]
```

### 6.5 GET /api/v1/stats/platform-approval — Q3 聚合

**响应体**（`data` 字段）

```json
[
  {
    "platform": "qianchuan",
    "total": 28,
    "approvedCount": 22,
    "approvalRatePct": 78.57
  }
]
```

### 6.6 GET /actuator/health — 健康检查

```json
{ "status": "UP" }
```

---

## 7. 验收标准

### ETL 模块

- [ ] `SELECT COUNT(*) FROM assets` 在全量导入后 ≥ 60（三份数据集总行数）
- [ ] 三份数据集均无重复记录（`UNIQUE` 约束不违反）
- [ ] 重跑 `--ingest=all` 结果幂等（总行数不变）
- [ ] `--dry-run` 模式不写入任何数据
- [ ] Excel 日期序列号（45540）解析结果为正确的 UTC 时间（2024-08-15T00:00:00Z 附近）
- [ ] 字符串 `"63.76MB"` 解析为正确字节数（66,821,734 bytes）
- [ ] Python list 字符串 `"['品牌', '测评']"` 解析为 `["品牌", "测评"]`
- [ ] 混合状态值（"通过"、"已通过"、"approved"）全部归一到 `"approved"`
- [ ] `StatusNormalizerTest`、`DateNormalizerTest`、`SizeNormalizerTest`、`TagNormalizerTest` 全部通过

### 资产查询 API

- [ ] `GET /api/v1/assets?status=approved` 仅返回 status=approved 的记录
- [ ] `GET /api/v1/assets?file_size_bytes[lte]=524288000` 仅返回文件 ≤ 500MB 的记录
- [ ] `GET /api/v1/assets?tags[has]=节日` 返回含"节日"标签的记录
- [ ] `GET /api/v1/assets?title[like]=冬` 返回标题含"冬"的记录
- [ ] `GET /api/v1/assets?sort=file_size_bytes:desc` 结果按文件大小降序
- [ ] `GET /api/v1/assets?fields=title,status` 响应不包含其他字段
- [ ] `GET /api/v1/assets?unknownField=x` 返回 HTTP 400
- [ ] `GET /api/v1/assets?page_size=201` 返回 HTTP 400
- [ ] `QueryDslParserTest` 全部通过

### 统计查询 API

- [ ] Q1 结果仅包含 status=approved 的素材
- [ ] Q2 Top 5 标签结果有序且无空标签
- [ ] Q3 通过率计算正确（approved_count / total * 100，NULLIF 防除零）
- [ ] 三条接口响应格式符合统一信封结构

### 前端界面

- [ ] Dashboard 页面加载后展示三张图表（ECharts 正常渲染）
- [ ] AssetList 页面过滤条件变更后表格内容实时更新
- [ ] AssetList 页面分页、排序功能正常
- [ ] AssetDetail 页面能从列表跳转并展示完整字段
- [ ] 路由状态刷新不丢失（Pinia + 路由 query 双向绑定）

### 整体集成

- [ ] `docker compose up --build` 后三个服务（postgres / backend / frontend）均健康
- [ ] `curl http://localhost:8080/actuator/health` 返回 `{"status":"UP"}`
- [ ] `curl http://localhost/` 返回前端页面（HTTP 200）
- [ ] `AssetControllerIT` Testcontainers 集成测试全部通过
- [ ] `mvn verify` 输出 `BUILD SUCCESS`

---

## 8. 已实现清单 vs 规划中

### 8.1 后端

| 功能项 | 状态 | 说明 |
|-------|------|------|
| Flyway V1 DDL + 索引 | **已实现** | `V1__init_assets.sql` |
| Asset 实体 + AssetStatus 枚举 | **已实现** | `domain/entity/Asset.java` |
| ExcelReader（Apache POI） | **已实现** | `ingest/excel/ExcelReader.java` |
| DateNormalizer | **已实现** | 单元测试通过 |
| SizeNormalizer | **已实现** | 单元测试通过 |
| StatusNormalizer | **已实现** | 单元测试通过 |
| PlatformNormalizer | **已实现** | 覆盖大小写归一 |
| TagNormalizer | **已实现** | Jackson 解析 Python list |
| Dataset1/2/3Adapter | **已实现** | 三个数据集 Adapter |
| IngestBatchService（批量 upsert） | **已实现** | ON CONFLICT DO UPDATE |
| IngestRunner（CLI 入口） | **已实现** | `--ingest / --dry-run / --limit` |
| AssetMapper + AssetMapper.xml | **已实现** | 动态过滤 SQL |
| QueryDslParser（bracket-style） | **已实现** | 单元测试覆盖 |
| FilterableField / SortableField 白名单枚举 | **已实现** | 防注入 |
| AssetController（列表 + 详情） | **已实现** | `GET /api/v1/assets` |
| StatsController（Q1/Q2/Q3） | **已实现** | 三条聚合接口 |
| AssetQueryService / AssetStatsService | **已实现** | 服务层分离 |
| GlobalExceptionHandler + ApiEnvelope | **已实现** | 统一错误响应 |
| CORS 配置 | **已实现** | `CorsConfig.java` |
| Swagger UI | **已实现** | `/swagger-ui.html` |
| Testcontainers 集成测试 | **已实现** | `AssetControllerIT.java` |
| Spotless 代码格式化 | **已实现** | `mvn spotless:apply` |
| Dockerfile（后端） | **已实现** | 多阶段构建 |

### 8.2 前端

| 功能项 | 状态 | 说明 |
|-------|------|------|
| Vue 3 + Vite 5 项目骨架 | **已实现** | `main/frontend/` |
| Vue Router 三路由 | **已实现** | Dashboard / AssetList / AssetDetail |
| Pinia assetStore | **已实现** | 查询条件状态管理 |
| queryBuilder.ts（DSL 序列化） | **已实现** | 前后端契约单一来源 |
| assetService.ts（HTTP 层） | **已实现** | Axios 封装 |
| FilterBar 组件 | **已实现** | 多字段过滤 |
| SortControl 组件 | **已实现** | 多字段排序 |
| FieldSelector 组件 | **已实现** | 稀疏字段选择 |
| Dashboard 页面（ECharts） | **已实现** | 三图表可视化 |
| AssetList 页面 | **已实现** | 表格 + 分页 |
| AssetDetail 页面 | **已实现** | 详情 + fields 开关 |
| Nginx 反代 | **已实现** | `nginx.conf` |
| Dockerfile（前端） | **已实现** | Nginx 静态服务 |

### 8.3 基础设施

| 功能项 | 状态 | 说明 |
|-------|------|------|
| docker-compose.yml（三服务） | **已实现** | postgres + backend + frontend |
| Spring Actuator 健康检查 | **已实现** | `/actuator/health` |
| 结构化日志 | **已实现** | SLF4J + Logback |

### 8.4 规划中（已知局限性）

| 功能项 | 状态 | 改进方向 |
|-------|------|---------|
| Keyset / Cursor 分页 | **规划中** | 替换 offset 分页，解决大 offset 性能问题 |
| 中文全文检索 | **规划中** | pg_jieba + tsvector 或 ES 读侧 projection |
| API 鉴权 | **规划中** | API Key + Bucket4j 速率限制 |
| 缓存层 | **规划中** | Caffeine 本地缓存；热点数据 Redis |
| Metrics / Tracing | **规划中** | Prometheus Micrometer + OpenTelemetry |
| ETL 失败行 DLQ | **规划中** | 独立 reject 表 + 人工复核流程 |
| 多租户支持 | **规划中** | 预留 `tenant_id` 字段 |
| `${orderBy}` 排序安全升级 | **规划中** | 替换为 jOOQ 或 XML `<choose>` 分支 |
| extra JSONB 字段升级策略 | **规划中** | 超阈值稀疏字段自动升级为正式列 |

---

## 9. 技术决策记录

### ADR-001：选择 PostgreSQL 而非 ElasticSearch

**决策**：使用 PostgreSQL 15 作为唯一数据存储。

**上下文**：题面要求二选一并说明理由。

**理由**：
1. 访问模式匹配：强一致 + 结构化过滤 + 小规模聚合 = PG 主战场；全文检索 + 大规模 facet 才是 ES 优势场景
2. 工程成本：PG 一个 Docker 容器；ES 至少 3 节点 + JVM 堆调优
3. JSONB 已足够：`extra JSONB + GIN` 覆盖稀疏字段，PG 本质是带事务的文档数据库
4. 演进路径不对称：PG → ES（Debezium CDC）是标准路径；ES → PG 引入事务语义代价高
5. 一致性需求：ETL 后立查，不接受 ES 默认 1s refresh 延迟

**演进路径**：出现中文全文检索需求时，通过 Debezium → Kafka → ES 建读侧 projection，PG 仍为 Source of Truth。

---

### ADR-002：应用层归一化而非数据库层

**决策**：ETL 归一化逻辑在 Java 应用层的 Normalizer 中实现，不在 DB 触发器或函数中。

**理由**：
1. 可测试性：每个 Normalizer 是纯函数，可独立单元测试边界值
2. 语义清晰：脏数据清洗是业务判断（"通过 = approved"），不属于数据库职责
3. 可扩展性：新增数据集只加一个 Adapter 实现，不改 schema 和 API

---

### ADR-003：自研 QueryDslParser 而非 RSQL 库

**决策**：自研约 150 行的 bracket-style 查询 DSL 解析器。

**理由**：
- 题面语法 `file_size[lte]=524288000` 是 bracket-style，与 RSQL（`file_size=le=524288000`）不同
- 自研可完全掌控字段白名单、操作符白名单、防注入策略
- 代码量小（~150行），面试答辩时可完整讲解实现细节

---

### ADR-004：TEXT + CHECK 约束而非 PostgreSQL ENUM

**决策**：`status` 字段使用 `TEXT NOT NULL CHECK (status IN ('pending','approved','rejected'))`。

**理由**：
- PG ENUM 增加枚举值需要 `ALTER TYPE`，在生产环境有锁风险
- TEXT + CHECK 约束修改更灵活（`ALTER TABLE ... ADD CONSTRAINT`）
- 行为等价，查询性能无差异

---

### ADR-005：UUID 主键而非自增整数

**决策**：`id UUID PRIMARY KEY DEFAULT gen_random_uuid()`。

**理由**：
- 三份数据集各自有独立 ID 命名空间，使用自增主键不影响业务 ID 冲突问题
- 源血缘通过 `source_dataset + source_id` 维护，不依赖数据库自增 ID
- UUID 分布式友好，未来横向扩展无需修改主键策略

---

### ADR-006：TEXT[] 标签字段而非 JSON 或关联表

**决策**：`tags TEXT[] NOT NULL DEFAULT '{}'` + GIN 索引。

**理由**：
- 避免分隔符陷阱（三份数据集用三种分隔符）
- `tags @> ARRAY['节日']` 查询与 GIN 索引天然配合
- 无需维护 tag 维表，降低 schema 复杂度
- PG `UNNEST(tags)` 在聚合查询（Q2）中性能良好

---

**文档终止线** | 版本 1.0 | 2026-04-23
