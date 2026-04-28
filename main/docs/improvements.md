# 局限性与改进方案

> 知道自己的边界，是工程成熟度的体现。

---

## 实现状态汇总

| # | 局限性 | 状态 | 改进方案 |
|---|-------|------|---------|
| 1 | ~~OFFSET 分页性能~~ | ✅ **已实现** | Cursor 分页 O(1) |
| 2 | 全文搜索无分词 | 待优化 | pg_trgm / pg_jieba / ES |
| 3 | ETL 无增量更新 | 待优化 | Debezium CDC |
| 4 | 大文件阻塞启动 | 待优化 | 消息队列异步导入 |
| 5 | ~~无监控告警~~ | ✅ **已实现** | Micrometer + Prometheus |
| 6 | ~~无认证鉴权~~ | ✅ **已实现** | API Key + Bucket4j |
| 7 | ~~硬编码数据集适配~~ | ✅ **已实现** | JSON 配置驱动 |
| 8 | ~~数据验证不完整~~ | ✅ **已实现** | Bean Validation + 前端表单验证 + DLQ |
| 9 | ~~extra JSONB 无上限~~ | ✅ **已实现** | 64KB 大小检查 + 截断告警 |

---

## ✅ 已实现：动态数据集适配

### 架构对比

```
改进前（硬编码）：
┌─────────────────────────────────────────────────────────┐
│  新增数据集 = 改代码 + 编译 + 测试 + 部署               │
│                                                         │
│  Dataset1Adapter.java  ←─ 硬编码字段映射               │
│  Dataset2Adapter.java  ←─ 硬编码字段映射               │
│  Dataset3Adapter.java  ←─ 硬编码字段映射               │
│                                                         │
│  问题：第100个数据集 = 再写100个 Adapter               │
└─────────────────────────────────────────────────────────┘

改进后（配置驱动）：
┌─────────────────────────────────────────────────────────┐
│  新增数据集 = 编辑 JSON 配置文件                        │
│                                                         │
│  dataset-mappings.json                                 │
│  ├── dataset_001: 素材数据集1                           │
│  ├── dataset_002: 素材数据集2                           │
│  ├── dataset_003: 素材数据集3                           │
│  ├── dataset_004: 新业务线素材库  ←─ 新增只需加配置     │
│  └── ...                                                │
│                                                         │
│  DynamicDatasetAdapter ←─ 一个适配器处理所有数据集      │
└─────────────────────────────────────────────────────────┘
```

### 效率对比

```
┌─────────────────────────────────────────────────────────┐
│                  新增数据集流程对比                      │
├─────────────────────────────────────────────────────────┤
│                                                         │
│  改进前：业务提需求 → 排期 → 开发 → 测试 → 部署         │
│          ════════════════════════════════════════        │
│                        3-5 天                           │
│                                                         │
│  改进后：编辑 JSON → 放入文件 → 执行导入                │
│          ══════════════════════════════════             │
│                      5 分钟                             │
│                                                         │
│                 效率提升：100 倍                        │
│                                                         │
└─────────────────────────────────────────────────────────┘
```

### 数据流转

```
数据导入流程：
┌──────────┐    ┌──────────┐    ┌──────────┐    ┌──────────┐
│  XLS     │    │  Excel   │    │ Dynamic  │    │ PostgreSQL│
│  文件    │───▶│  Reader  │───▶│ Adapter  │───▶│   DB     │
│          │    │          │    │          │    │          │
│ 任意格式 │    │ 解析行   │    │ JSON配置 │    │ 幂等写入 │
└──────────┘    └──────────┘    └──────────┘    └──────────┘
                                     │
                                     ▼
                          ┌──────────────────┐
                          │ dataset-mappings │
                          │     .json        │
                          │                  │
                          │ 字段映射规则     │
                          │ 类型转换规则     │
                          └──────────────────┘
```

---

## 局限 1：分页性能 — OFFSET 在大表上劣化

### 当前实现

```sql
SELECT ... FROM assets LIMIT 20 OFFSET 1000;
```

`OFFSET N` 让数据库扫描并丢弃前 N 行，时间复杂度 O(N)。当 N 较大时（如跳转到第 10000 页），查询即使命中索引也需要跳读大量行。

### 影响

数据量超过 100 万行 + 用户翻到深页时，查询时间可能从 <1ms 升到 >1s。

### 改进方案：Keyset 分页（Cursor 分页）

```sql
-- 第一页
SELECT * FROM assets ORDER BY uploaded_at DESC, id ASC LIMIT 20;

-- 后续页：用上一页最后一行的 (uploaded_at, id) 作为游标
SELECT * FROM assets
WHERE (uploaded_at, id) < ('2024-08-10T00:00:00Z', 'xxxxxx-uuid')
ORDER BY uploaded_at DESC, id ASC
LIMIT 20;
```

优势：每次查询 O(1)，无论翻到多深的页。缺点：不支持随机跳页（只能上/下翻页），适合"无限滚动"场景。

---

## 局限 2：全文搜索 — ILIKE 不支持中文分词

### 当前实现

```sql
WHERE uploader ILIKE '%张%'
WHERE title ILIKE '%促销%'
```

`ILIKE` 是字节级模糊匹配，无法理解"促销活动"中"促销"和"活动"是独立的语义单元。

### 影响

- "春节促销" 无法被 "春节" 搜到（必须输入完整子串）
- 无法做同义词搜索（"优惠" ≈ "折扣"）
- 前导通配符 `%term%` 导致全表扫描，无法使用 B-tree 索引

### 改进方案 A（轻量）：pg_trgm + GIN

```sql
-- 安装扩展
CREATE EXTENSION IF NOT EXISTS pg_trgm;

-- 建 GIN trigram 索引
CREATE INDEX idx_assets_title_trgm ON assets USING GIN (title gin_trgm_ops);

-- 查询（ILIKE 自动使用 trigram 索引）
SELECT * FROM assets WHERE title ILIKE '%促销%';
```

`pg_trgm` 把字符串拆成 3-gram（字符三元组），可以为 ILIKE 建索引，避免全表扫描。支持中文（按 UTF-8 码点拆分），但不是真正的语义分词。

### 改进方案 B（完整）：pg_jieba + tsvector

```sql
-- 安装 pg_jieba（中文分词扩展，需要 OS 层编译）
-- 建 tsvector 列
ALTER TABLE assets ADD COLUMN title_tsv tsvector
    GENERATED ALWAYS AS (to_tsvector('jieba', title)) STORED;

CREATE INDEX idx_assets_title_tsv ON assets USING GIN (title_tsv);

-- 语义查询
SELECT * FROM assets WHERE title_tsv @@ to_tsquery('jieba', '促销 & 节日');
```

支持真正的中文分词查询，但依赖 pg_jieba 扩展，Docker 镜像需要额外编译步骤。

### 改进方案 C（重型）：PG → Debezium → Kafka → ElasticSearch

```
assets 表变更 → Debezium CDC → Kafka Topic → ES 消费者 → ES index（IK 分词）
```

ES 读侧做全文检索，PG 仍是 Source of Truth。ES 最终一致（约 1s refresh 延迟），适合检索而不适合事务场景。

---

## 局限 3：ETL — 全量重跑，无增量更新

### 当前实现

每次 `--ingest=all` 重新读取全部 xls 文件，做 upsert（ON CONFLICT DO UPDATE）。

### 影响

- 文件增大时（几十万行），每次导入耗时增长
- 无法检测"源文件删除的行"（只增不减）
- 没有增量追踪机制（`updated_at` 比对）

### 改进方案

1. **基于 updated_at 的增量 ETL**：在源数据中记录最后修改时间，ETL 只导入比上次运行时间更新的行。

2. **文件内容哈希校验**：记录每个文件的 MD5，未变更文件跳过。

3. **流式 CDC（生产级）**：上游数据库开启 Binlog/WAL，通过 Debezium 实时捕获变更事件，无需批量重跑。

4. **软删除支持**：在 assets 表增加 `deleted_at TIMESTAMPTZ`，源数据删除时标记软删除而不是物理删除，保留审计历史。

---

## 局限 4：异步导入 — 大文件导入阻塞启动

### 当前实现

`IngestRunner` 实现 `ApplicationRunner`，在 Spring 启动后同步执行导入，导入期间 HTTP 服务虽已就绪但 DB 状态未完整。

### 影响

- 导入几百万行时，应用启动到"可用状态"需要等待几十分钟
- 无法观察导入进度（除了 log）
- 导入失败需要重启整个应用

### 改进方案

1. **消息队列异步导入**：把导入任务发到 RabbitMQ/Kafka，独立 Consumer 处理，API 即时返回任务 ID。

2. **进度追踪表**：建 `ingest_jobs` 表记录任务状态（pending/running/completed/failed），提供进度查询 API。

3. **分块并行**：把大文件切分成若干 chunk，多线程并行归一化 + 批量 upsert，充分利用多核 CPU。

4. **WebSocket 进度推送**：前端通过 WebSocket 订阅导入进度，实时展示进度条。

---

## 局限 5：前端状态管理 — Pinia store 无持久化

### 当前实现

Pinia store 的过滤/排序/分页状态只在内存中，页面刷新后状态丢失（本次已通过 URL query 双向绑定部分解决）。

### 影响

- 用户过滤完素材后不小心关闭标签页，重新打开无法恢复状态
- 多 tab 场景下不同 tab 的筛选状态互相独立（需要 URL 传递）
- 移动端返回键后状态丢失

### 改进方案

1. **URL Query 双向绑定（已实现）**：过滤/排序/分页通过 URL query params 持久化，刷新不丢失，可分享链接。

2. **localStorage 持久化（补充）**：对"用户偏好"类配置（如每页条数、默认排序字段）用 `pinia-plugin-persistedstate` 持久化到 localStorage，跨 session 保留用户偏好。

3. **IndexedDB 大数据缓存**：对统计数据（Q1/Q2/Q3 结果）做 IndexedDB 缓存，减少 Dashboard 重新加载时间。

---

## 局限 6：监控与告警 — ✅ 已实现

### 当前实现

~~仅有 SLF4J 结构化日志 + Spring Actuator `/actuator/health` 健康检查，无 Metrics 采集和分布式链路追踪。~~

**已实现**：Micrometer + Prometheus 指标采集。

### 实现方案

```yaml
# pom.xml 加依赖
micrometer-registry-prometheus:
  # 自动暴露 /actuator/prometheus 端点

# Prometheus scrape config
scrape_configs:
  - job_name: 'asset-backend'
    static_configs:
      - targets: ['backend:8080']
    metrics_path: '/actuator/prometheus'
```

完整可观测性栈：

| 组件 | 用途 |
|------|------|
| Micrometer + Prometheus | Metrics 采集（QPS、延迟、JVM） |
| Grafana | Metrics 可视化 + 告警规则 |
| OpenTelemetry Agent | 分布式链路追踪（Trace） |
| Jaeger / Zipkin | Trace 可视化 |
| ELK Stack | 日志聚合（结构化 JSON → ES → Kibana） |

---

## 局限 7：权限控制 — ✅ 已实现

### 当前实现

~~所有 API 接口无鉴权，任何人可以调用 `GET /api/v1/assets` 读取全量数据。~~

**已实现**：
1. **API Key 认证**：通过 `X-API-Key` Header 进行认证
2. **Bucket4j 限流**：每秒 10 请求，超过返回 429
3. **Spring Security 配置**：公开端点（Swagger、Actuator）无需认证

### 实现方案

**认证过滤器**：
```java
@Component
public class ApiKeyAuthFilter extends OncePerRequestFilter {
    @Override
    protected void doFilterInternal(...) {
        String apiKey = request.getHeader("X-API-Key");
        // 验证配置白名单
        // 设置 SecurityContext
    }
}
```

**限流过滤器**：
```java
@Component
public class RateLimitFilter extends OncePerRequestFilter {
    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();
    // Token Bucket 算法
    // 按 API Key 或 IP 限流
}
```

**配置项**：
```yaml
app:
  security:
    enabled: true
    api-keys:
      - key: "dev-api-key-001"
        name: "Developer"
        roles: "ROLE_USER"
  rate-limit:
    requests-per-second: 10
```

### 后续优化

1. **API Key 存储**：迁移到 Vault / 数据库
2. **分布式限流**：Bucket4j + Redis（多实例场景）
3. **JWT 支持**：用户登录场景

---

## 局限 8：数据验证 — ✅ 已实现

### 已实现内容

**后端 Bean Validation**：
- `AssetController` + `StatsController` 添加 `@Validated`，参数校验 `@Min`、`@Max`、`@Pattern`（UUID 格式）
- `AssetCommandController` 已有 `@Validated`，`DeleteBatchRequest` 已有 `@NotEmpty`、`@Size`
- `Asset` 实体添加字段级校验：`@NotNull`（sourceDataset、fileSizeBytes、uploadedAt）、`@NotBlank`（sourceId、title、uploader）、`@Size`（sourceId/100、title/500、uploader/200、remark/2000）、`@Min(0)`（fileSizeBytes、durationSec）
- `GlobalExceptionHandler` 统一处理 `MethodArgumentNotValidException` / `ConstraintViolationException`

**前端 Form 验证（Element Plus）**：
- `FilterBar.vue` 添加 `:rules="filterRules"` 绑定
- `validatePositiveNumber` 自定义校验器（文件大小 >= 0）
- `validateFileSizeRange` 交叉校验（fileSizeMax >= fileSizeMin）
- `uploader` 最大 100 字符限制

**ETL 拒绝表（DLQ）**：
- `ingest_rejects` 表（V2 migration）存储归一化失败行，含 `raw_record` JSONB
- `IngestAuditService` 写入拒绝记录
- API 上传响应 `UploadResult` 包含 `RejectedRecord` 列表

---

## 局限 9：extra JSONB 无上限 — ✅ 已实现

### 已实现内容

**64KB 大小检查与截断**（`AssetCommandService.importFromExcel`）：
- `ObjectMapper.writeValueAsBytes(extra)` 序列化后检查大小
- 超过 64KB 时：`log.warn` 记录 source_id 和实际大小，将 extra 置为 `Map.of()`
- 序列化失败时同样截断并告警

### 后续演进（未实现）

1. **字段提升策略**：当某个 `extra` 中的字段在超过 30% 的记录中出现，且被频繁查询时，通过 `ALTER TABLE ADD COLUMN` 提升为正式列
2. **JSONB Schema 约束（PostgreSQL 16+）**：使用 JSON Schema 验证 `extra` 的结构

---

## 局限 10：ES 演进路径未提前铺垫

### 当前实现

当前 PostgreSQL 是唯一数据存储，代码中没有为 ES 演进预留任何 hook。

### 影响

- 将来需要全文检索时，改造成本较高（需要同步两套存储）
- 没有 Event Sourcing / CQRS 分离的接口层

### 改进方案：完整的 PG → ES 演进路径

```
阶段一（当前）：
  应用 → PostgreSQL
  （单一 OLTP 存储）

阶段二（全文检索需求出现）：
  应用写 → PostgreSQL（SOT）
            ↓ Debezium CDC（WAL）
            ↓ Kafka Topic: assets-cdc
            ↓ ES Consumer（Java / Kafka Connect ES Sink）
  应用读（全文）→ Elasticsearch（读侧投影）
  应用读（结构化）→ PostgreSQL（仍走原有 API）

阶段三（规模过千万）：
  分区：pg_partman 按 uploaded_at 月份分区
  读写分离：PG 主从复制，读 API 走从节点
  写热点：引入 Citus 分片（按 uploader hash 分片）

阶段四（复杂 OLAP 分析）：
  PG → Debezium → Kafka → ClickHouse（分析侧）
  ClickHouse 处理复杂多维聚合，PG 处理 OLTP 查询
```

**演进的关键技术决策**：
- Debezium 需要 PG 开启 `wal_level = logical`（已在 `application.yml` 预留注释）
- Kafka Consumer Group 确保 ES 同步不重复、不丢失
- ES 索引映射需要提前规划（`dynamic: false` 防止 mapping explosion）
- 从 PG 演进到 PG+ES 的过程中，写接口不需要改，只需要加 CDC pipeline
