# 视频素材查询服务 · 技术设计文档

> 版本：1.0 | 日期：2026-04-23 | 作者：Java 架构师

---

## 1. 系统概述

本系统基于三份字段结构不一致的视频素材数据集，设计并实现了一套统一的存储 Schema 和只读查询 API，并附带一个轻量可视化管理前端。

**核心挑战**：三份数据集在字段命名语言、时间格式、大小单位、枚举值、标签分隔符等方面均不一致，不是简单的"字段多少"问题，而是真正的异构数据集成（Heterogeneous Data Integration）问题。

---

## 2. 数据库选型决策

**选型**：PostgreSQL 15

### 决策依据（访问模式驱动）

| 访问模式 | 特征 | 对 DB 的真正要求 |
|---------|------|----------------|
| 写入 | 一次性 ETL，要求幂等 | UNIQUE 约束 + ON CONFLICT DO UPDATE |
| 读取核心 | 多字段结构化过滤 + 排序分页 | B-tree + 组合索引 |
| 读取聚合 | 三条 GROUP BY / UNNEST / FILTER 查询 | 关系代数，SQL 原生 |
| 动态字段 | 三份数据集的稀疏扩展字段 | JSONB + GIN |
| 标签查询 | 数组包含（tags @> ARRAY[...]） | text[] + GIN |
| 一致性 | ETL 后立刻需要查询验证 | 强一致，不接受 refresh 延迟 |

**ElasticSearch 为什么不选**：数据量小（~75条→百万级），无全文检索需求，ETL 后立查需要强一致，PG 的 JSONB 已覆盖动态字段需求，ES 3节点集群运维成本不合算。

**未来演进路径**：若出现全文检索需求，通过 Debezium CDC → Kafka → ES 建立读侧 projection，PG 仍作为 Source of Truth。

---

## 3. Schema 设计

### 核心设计决策

| 决策 | 选择 | 理由 |
|------|------|------|
| 主键类型 | UUID（gen_random_uuid()） | 避免三套数据集 ID 命名空间冲突 |
| 文件大小单位 | BIGINT bytes | 统一单位，避免精度损失 |
| 时间字段 | TIMESTAMPTZ（UTC） | 避免 Excel 本地时间歧义 |
| 标签字段 | text[] + GIN | 避免分隔符陷阱，支持 @> 查询 |
| 状态枚举 | TEXT + CHECK（非 PG ENUM） | ENUM 加值要 ALTER TYPE，CHECK 约束更灵活 |
| 稀疏字段 | extra JSONB + GIN | 避免 EAV 模式，保持单表 |
| 血缘字段 | raw_record JSONB | 支持审计和重跑 ETL |
| 幂等约束 | UNIQUE(source_dataset, source_id) | ETL ON CONFLICT DO UPDATE |

### 索引策略

每个索引都对应具体查询场景（不为幻想建索引）：

```sql
idx_assets_status_uploader  -- Q1: WHERE status='approved' GROUP BY uploader
idx_assets_tags_gin         -- tags @> ARRAY[?]（UNNEST 聚合通常仍为 Seq Scan）
idx_assets_platform         -- Q3: GROUP BY platform
idx_assets_uploaded_at      -- 列表排序
idx_assets_file_size_bytes  -- 范围过滤 + 排序
idx_assets_extra_gin        -- JSONB 路径查询
```

---

## 4. ETL 架构

### Adapter + Normalizer 模式

```
xls → ExcelReader → DatasetXAdapter → CanonicalAsset → Upsert → PostgreSQL
                           ↓
                    5 个 Normalizer（纯函数、可单元测试）
```

**为什么在应用层做归一化而不在 DB 层**：
1. 脏数据清洗是业务语义判断（"通过 = approved"），放 DB 层失去可测性
2. 每个 Normalizer 可独立单元测试，覆盖边界值
3. 新增数据集只加一个 Adapter，不改 schema 和 API

### 三份数据集的差异处理

| 维度 | 数据集1 | 数据集2 | 数据集3 | 归一化方案 |
|------|---------|---------|---------|-----------|
| 时间格式 | Excel 序列号整数 | Excel 序列号含小数 | Unix 秒时间戳 | 阈值 1e9 区分 Unix/Excel |
| 文件大小 | 字符串 "63.76MB" | 字节整数 | 数值+单位双列 | 三路 SizeNormalizer |
| 审核状态 | 中文 | 英文 | 混合 | 映射表 StatusNormalizer |
| 标签格式 | 分号 | 逗号 | Python list 字符串 | 正则提取（禁止 eval） |
| 平台命名 | 无 | 千川 | qianchuan/千川混用 | PlatformNormalizer |

---

## 5. API 设计

### 查询 DSL

题面要求 `file_size[lte]=524288000` 是 bracket-style，与 RSQL/FIQL 不同，自研解析器（~150行）：

**安全四原则**：
1. 字段名 → FilterableField 枚举白名单，未知字段 400
2. 操作符 → FilterOperator 枚举白名单，未知操作符 400
3. 所有值 → MyBatis `#{}` 参数化，零字符串拼接
4. 排序字段 → SortableField 枚举映射到 DB 列名，XML 内用 `<foreach>/<choose>` 硬编码列名，完全消除 `${}` 拼接

**类型解析策略**：
- `uploaded_at[gt/gte/lt/lte]` 支持 ISO-8601 和 epoch 秒/毫秒，统一转 `TIMESTAMPTZ` 参数比较。
- 其余数值范围过滤按数值解析，非法类型直接 400。

**稳定分页策略**：
- 即使未传 `sort`，默认也按 `uploaded_at DESC, id DESC` 排序，避免 offset 分页结果漂移。
- 用户自定义排序时自动追加 `id DESC` 作为 tie-breaker。

### 端点清单

```
GET /api/v1/assets                      列表（过滤+排序+分页+稀疏字段）
GET /api/v1/assets/{id}                 详情（支持 ?fields=）
GET /api/v1/stats/uploader-avg-size     Q1 聚合
GET /api/v1/stats/top-tags              Q2 聚合
GET /api/v1/stats/platform-approval     Q3 聚合
GET /swagger-ui.html                    API 文档
GET /actuator/health                    健康检查
```

---

## 6. 前端设计

轻量 Vue 3 管理后台，3 页面：

| 页面 | 功能 |
|------|------|
| Dashboard（/） | ECharts 可视化三条聚合查询结果 |
| AssetList（/assets） | 多字段过滤 + 排序 + 稀疏字段 + 分页 |
| AssetDetail（/assets/:id） | 详情 + ?fields 开关 |

`utils/queryBuilder.ts` 是前后端契约的唯一来源，把 UI 状态序列化为 DSL 语法。

---

## 7. 扩展性设计

> 面试题仅有 3 份数据集，当前 MVP 方案采用硬编码 Adapter。以下是面向 **100/1000 份异构数据集** 的演进路径，确保架构具备可扩展性。

### 7.1 数据源扩展

**现状**：每份新数据集需要新增一个 `DatasetXAdapter.java` 类，硬编码字段映射。

**演进方案：元数据驱动映射**

```
dataset_metadata 表（字段血缘配置）
┌──────────┬────────────┬──────────┬────────────────┬────────────────┐
│ dataset  │ field_name │ data_type│ normalize_rule │ canonical_name │
├──────────┼────────────┼──────────┼────────────────┼────────────────┤
│ 1        │ 素材编号    │ TEXT     │ identity       │ source_id      │
│ 1        │ 上传日期    │ EXCEL_DATE│ excel_to_instant│ uploaded_at   │
│ 1        │ 文件大小(MB)│ MB_STRING│ mb_to_bytes    │ file_size_bytes│
│ 2        │ asset_id   │ TEXT     │ identity       │ source_id      │
│ 2        │ upload_time│ UNIX_EPOCH│ unix_to_instant│ uploaded_at    │
│ 999      │ ...        │ ...      │ ...            │ ...            │
└──────────┴────────────┴──────────┴────────────────┴────────────────┘
```

新增数据集 = **填配置行** + **上传 Excel 文件**，零代码改动。

### 7.2 清洗规则扩展

**现状**：`Normalizer` 硬编码 if-else 分支，新增格式需改代码。

**演进方案：规则优先级引擎**

```
normalizer_rules 表
┌────────┬─────────────────────────────────┬──────────────────┬─────────┐
│ field   │ pattern（正则）                 │ transform_fn     │ priority │
├────────┼─────────────────────────────────┼──────────────────┼─────────┤
│ status  │ ^(待审核|pending)$              │ → pending        │ 10      │
│ status  │ ^(通过|approved)$              │ → approved       │ 10      │
│ status  │ ^.*$                            │ → rejected       │ 1       │
│ size    │ ^(\d+\.?\d*)\s*MB$              │ parse * 1048576  │ 10      │
│ size    │ ^(\d+)$                         │ identity         │ 10      │
│ date    │ \d{10,}                         │ unix_sec_or_ms   │ 10      │
│ date    │ \d{4}-\d{2}-\d{2}.*            │ iso8601_parse    │ 10      │
└────────┴─────────────────────────────���───┴──────────────────┴─────────┘
```

优先级匹配（高优先级优先），匹配失败则回退到默认值或拒绝导入。

### 7.3 为什么当前版本用硬编码

| 考量 | 决策 |
|------|------|
| 数据集数量 | 3 份（明确边界），硬编码更直观、可读 |
| 可测试性 | 每个 Adapter/Normalizer 独立单元测试，无需 mock 配置 |
| 交付节奏 | 5 天时间，MVP 优先，把问题解决到位比留扩展性更重要 |
| 面试价值 | 清晰的三层 ETL 架构比配置驱动框架更容易展示设计思路 |

**架构师原则**：用最简单的方式解决当前问题，在文档中说清楚演进路径——这比过度设计更能体现架构判断力。

---

## 8. 查询性能基准（EXPLAIN ANALYZE）

> 环境：PostgreSQL 15，69 条记录，Docker 容器本地执行。数据量小，Seq Scan 是预期行为（索引在大数据集下才触发），Planning Time 已包含。

### Q1：各上传人平均文件大小

```text
Sort  (cost=6.25..6.27 rows=8)  (actual time=0.127..0.128 rows=8)
  Sort Key: avg_size_bytes DESC
  Sort Method: quicksort  Memory: 25kB
  -> HashAggregate  (cost=6.01..6.13 rows=8)  (actual time=0.052..0.056 rows=8)
       Group Key: uploader
       -> Seq Scan on assets  (rows=20 approved of 69)  (actual time=0.016..0.033)
            Filter: (status = 'approved')

Planning Time: 4.185 ms  |  Execution Time: 1.275 ms
```

**分析**：69 行全表扫 + HashAggregate，<2ms。生产环境百万行时，`idx_assets_status_uploader` 组合索引将把 Seq Scan 替换为 Index Scan，且可考虑添加部分索引 `WHERE status='approved'` 进一步降低扫描范围。

---

### Q2：标签 Top 5（UNNEST 展开聚合）

```text
Limit  (cost=23.87..23.88 rows=5)  (actual time=0.228..0.230 rows=5)
  -> Sort  (actual time=0.227..0.228 rows=5)
       Sort Key: count DESC  |  Sort Method: quicksort  Memory: 25kB
       -> HashAggregate  (actual time=0.186..0.187 rows=8 distinct tags)
            -> Nested Loop  (rows=133 tag expansions from 69 rows)
                 -> Seq Scan on assets (69 rows)
                 -> Function Scan on unnest tag (~2 tags/row avg)

Planning Time: 3.182 ms  |  Execution Time: 0.432 ms
```

**分析**：UNNEST 展开产出 133 行（69 行 × 平均约 2 标签/素材），HashAggregate 去重为 8 个不同标签，Limit 5 截断。`idx_assets_tags_gin` 在此聚合场景不起作用（GIN 适合 `@>` 点查询），Seq Scan 是最优路径。

---

### Q3：各平台审核通过率（CTE + FILTER）

```text
Sort  (actual time=0.130..0.131 rows=2)
  Sort Key: approval_rate_pct DESC NULLS LAST
  -> HashAggregate (Group Key: platform, rows=2 platforms)
       -> HashAggregate (Group Key: platform+status, rows=5 combinations)
            -> Seq Scan on assets  (rows=41 with platform IS NOT NULL)
                 Filter: platform IS NOT NULL  |  Rows Removed: 28

Planning Time: 2.811 ms  |  Execution Time: 0.285 ms
```

**分析**：41 条有平台数据，5 种 (platform, status) 组合，外层聚合为 2 个平台结果。双层 HashAggregate 结构与 CTE 设计一致。`idx_assets_platform` 在此 Seq Scan + Filter 场景未被使用（行数太少），大数据集下将转为 Index Scan。

---

## 9. 局限性与未来改进

| # | 局限性 | 生产改进方案 |
|---|-------|------------|
| 1 | 分页用 offset，大 offset 性能劣化 | 切换 keyset/cursor 分页 |
| 2 | 中文全文搜只有 ILIKE，无分词 | pg_jieba + tsvector；或 ES 读侧 projection |
| 3 | ETL 是一次性脚本 | Debezium CDC + 增量 upsert |
| 4 | 只读接口无鉴权 | API Key + 速率限制（Bucket4j） |
| 5 | 无缓存层 | Caffeine 本地缓存；热点数据加 Redis |
| 6 | 只有结构化日志 | Prometheus Micrometer + OpenTelemetry |
| 7 | ETL 失败行无单独 DLQ | 专门 reject 表 + 人工复核流程 |
| 8 | 未预留 tenant_id | 多租户必加 |
| 9 | extra JSONB 无上限管理 | 超阈值字段升级为正式列 |
| 10 | ES 演进路径未提前铺垫 | Debezium CDC pipeline：PG → Kafka → ES，PG 仍为 SOT |

---

## 10. 技术栈一览

| 层 | 技术 | 版本 | 用途 |
|----|------|------|------|
| 语言 | Java | 17 | LTS，record/switch expression |
| 框架 | Spring Boot | 3.3.x | MVC 同步模型 |
| ORM | MyBatis-Plus | 3.5.x | BaseMapper + 动态 XML |
| 迁移 | Flyway | 9.x | 版本化 Schema |
| 数据库 | PostgreSQL | 15 | 主存储 |
| 集成测试 | Testcontainers | 1.19+ | 真实 PG 容器 |
| API 文档 | springdoc-openapi | 2.x | Swagger UI |
| Excel 读取 | Apache POI | 5.x | .xls HSSF |
| 前端 | Vue 3 + Vite 5 + TypeScript | — | 管理后台 |
| UI 库 | Element Plus | 2.x | 表格/表单/分页 |
| 图表 | ECharts | 5.x | 三条聚合可视化 |
| 容器化 | Docker + docker-compose | — | 一键启动 |
