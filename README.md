# 视频素材查询服务

**核心挑战**：三份来自不同业务系统的 XLS 文件（或更多），字段命名语言、时间格式（Excel序列号整数/含小数/Unix秒）、大小单位（字符串"63MB"/字节整数/数值+单位双列）、状态枚举（中文/英文/混合）、标签分隔符（分号/逗号/Python list字符串）全部不一致。这是真正的**异构数据集成**问题，不是简单的字段映射。

**结果**：74 源行 → 69 有效记录（3 条空标题行级拒绝，2 条重复幂等合并）；API P99 < 2ms；19/19 验收全通过；`bash start-docker.sh` 一键启动。

**技术栈**：Java 17 · Spring Boot 3.3 · MyBatis-Plus · PostgreSQL 15 · Flyway · Testcontainers · Vue 3 · Docker Compose

---

## 快速启动

```bash
git clone https://github.com/yanyinxi/homework.git && cd homework

bash start-docker.sh        # Docker（推荐）：自动处理端口冲突 + 数据导入
bash start-local.sh         # macOS/Linux：脚本自动安装 Java/Maven/Node/PG
.\start-local.ps1           # Windows（管理员 PS）：winget 自动安装依赖
```

| 服务 | Docker | 本地 |
| ------ | -------- | ------ |
| 前端管理后台 | <http://localhost> | <http://localhost:5173> |
| Swagger UI | <http://localhost:8080/swagger-ui.html> | 同左 |
| 健康检查 | <http://localhost:8080/actuator/health> | 同左 |

---

## 代码结构

```text
homework/
├── doc/                                # 原始题目 + 三份源数据集 XLS
├── main/
│   ├── backend/                        # Spring Boot 3.3，port 8080
│   │   └── src/main/java/com/homework/asset/
│   │       ├── api/
│   │       │   ├── AssetController     GET /api/v1/assets（列表+详情）
│   │       │   ├── StatsController     GET /api/v1/stats/*（Q1/Q2/Q3）
│   │       │   ├── IngestController    GET /api/v1/ingest/*（运行记录+拒绝明细）
│   │       │   ├── dto/                ApiEnvelope / AssetDTO / PagedResponse
│   │       │   ├── exception/          ApiException + GlobalExceptionHandler
│   │       │   └── query/              FilterableField / FilterOperator /
│   │       │                           SortableField / QueryDslParser（DSL核心）
│   │       ├── config/
│   │       │   └── PgStringArrayTypeHandler   text[] JDBC 适配（关键组件）
│   │       ├── ingest/
│   │       │   ├── IngestRunner        ApplicationRunner，CLI 入口
│   │       │   ├── IngestBatchService  批量 upsert（ON CONFLICT DO UPDATE）
│   │       │   ├── adapter/            DatasetAdapter 接口 + 三个实现类
│   │       │   ├── excel/              Apache POI ExcelReader
│   │       │   └── normalizer/         EtlNormalizers（5个纯函数 Normalizer）
│   │       ├── mapper/                 AssetMapper + AssetMapper.xml（动态 SQL）
│   │       └── service/                AssetQueryService / AssetStatsService
│   │
│   ├── frontend/                       # Vue 3 + Vite，port 5173/80
│   │   └── src/
│   │       ├── pages/                  Dashboard / AssetList / AssetDetail
│   │       ├── components/             FilterBar / SortControl / FieldSelector
│   │       ├── stores/assetStore       Pinia，查询状态 ↔ URL query 双向绑定
│   │       ├── utils/queryBuilder      前后端契约的唯一序列化来源
│   │       └── services/assetService   Axios HTTP 层
│   │
│   └── docs/                           # 架构设计 / SQL / 验收报告 / 截图
│
├── docker-compose.yml                  三服务编排（postgres + backend + frontend）
└── start-docker.sh / start-local.sh / start-local.ps1
```

---

## 技术挑战与架构难点

### 1. 存储选型：访问模式驱动，拒绝 ElasticSearch

选型标准只有一个：**当前访问模式需要什么**，不是技术热度。

| 访问模式 | PostgreSQL | ElasticSearch |
| -------- | ---------- | ------------- |
| 结构化字段过滤 + 数值范围 | B-tree/GIN，O(log n) | 倒排索引，非本职 |
| GROUP BY 聚合（Q1/Q2/Q3） | SQL HashAggregate，原生 | Aggregation bucket，表达复杂 |
| `text[] @>` 数组包含查询 | GIN 索引，直接支持 | nested field，mapping 复杂 |
| ETL 后立即一致读 | ACID，写完即可查 | 默认 1s refresh 延迟，不可接受 |
| 幂等写入 | `ON CONFLICT DO UPDATE` | 无原生 upsert 语义 |

**ES 的真正优势（全文检索、亿级 facet）本题不存在**。选 ES 是过度设计。

**未来演进**：若出现中文全文检索需求，通过 Debezium CDC → Kafka → ES 建读侧 projection，PG 仍作 Source of Truth——这是演进，不是替换。

---

### 2. Schema 统一：单表 + JSONB 兜底，拒绝 EAV 和多表

**拒绝 EAV（属性值表）**：Q1/Q2/Q3 聚合全部需要 JOIN，GROUP BY 性能劣化，索引难以建立。

**拒绝多表（每数据集一张）**：跨数据集聚合强制 UNION，API 层需要合并逻辑，schema 随数据集数量增长。

**选择单表**，关键字段决策：

```sql
-- 状态用 TEXT + CHECK，而非 PG ENUM
-- 原因：ENUM 增加枚举值需要 ALTER TYPE，在生产环境有锁风险
status  TEXT NOT NULL CHECK (status IN ('pending','approved','rejected'))

-- 标签用 TEXT[]，而非 JSONB 数组
-- 原因：TEXT[] + GIN 索引直接支持 @> 包含查询，JSONB 需要额外展开
tags    TEXT[] NOT NULL DEFAULT '{}'

-- 主键用 UUID，而非自增
-- 原因：三份数据集各自有 ID 命名空间（A0001/asset_001/vid0001），自增无法解决血缘追踪
id      UUID NOT NULL DEFAULT gen_random_uuid()

-- 稀疏字段用 JSONB 兜底，避免列爆炸
-- platform 建独立列（Q3 需要 GROUP BY），spend 等低频字段进 extra
extra   JSONB NOT NULL DEFAULT '{}'
```

**索引原则：每个索引对应一个真实查询场景**（不为幻想建索引）：

```sql
idx_assets_status_uploader  -- Q1: WHERE status='approved' GROUP BY uploader（覆盖扫描）
idx_assets_tags_gin         -- tags @> ARRAY['节日']（GIN，@> 专用）
idx_assets_platform         -- Q3: GROUP BY platform WHERE platform IS NOT NULL
idx_assets_uploaded_at      -- 列表默认排序（DESC，与查询方向一致）
idx_assets_file_size_bytes  -- 文件大小范围过滤 + 排序
```

---

### 3. 查询 DSL：自研 bracket-style，不引入 RSQL

作业要求的语法是 `file_size_bytes[lte]=524288000`——这是 bracket-style，与 RSQL（`file_size=le=524288000`）格式不同。引入 RSQL 库需要额外适配层，不如自研（~150行）且能完全掌控安全策略。

**防注入四层设计**（缺任何一层都是漏洞）：

```text
请求参数 → FilterableField 枚举白名单 → 未知字段 400
         → FilterOperator 枚举白名单  → 未知操作符 400
         → MyBatis #{} 参数化        → 值永远不拼接进 SQL
         → SortableField 枚举 + XML <foreach>/<choose> hardcode 列名
           （即使 Java 层有白名单，${orderBy} 也是注入风险，项目规范禁止）
```

**稳定分页**：默认排序追加 `id DESC` 作为 tie-breaker，避免 PostgreSQL 对相同字段值行的不确定排序导致 OFFSET 分页重复/缺失记录。

---

### 4. ETL：应用层归一化，不在数据库层处理

**将归一化放在 DB 触发器/函数里的问题**：失去可测性，脏数据清洗是业务语义判断，不属于数据库职责。

5 个 **纯函数 Normalizer**，各自独立单元测试，覆盖真实的格式复杂度：

| Normalizer | 真实难点 |
| ---------- | -------- |
| `DateNormalizer` | Excel 基准日期是 1899-12-30（含1900年闰年Bug），不是 1900-01-01；`>1e9` 判断为 Unix 秒 |
| `SizeNormalizer` | 字符串 "63.76MB" / 字节整数 / 数值+单位双列 → BigDecimal 避免浮点误差 |
| `TagNormalizer` | Python list 字符串 `"['品牌', '测评']"` 是单引号，不是合法 JSON，正则提取，**禁止 eval** |
| `StatusNormalizer` | 中英混合 + 大小写变体，两阶段匹配（精确 → 不区分大小写），未知值 throw |
| `PlatformNormalizer` | qianchuan/千川/Qianchuan 混用 → canonical code，N/A → null |

**批次事务设计**：行级预校验在归一化阶段提前剔除无效行（null title/uploader/status），避免一行脏数据触发整批事务回滚（Dataset3 的初始实现就踩了这个坑）。

**新增数据集**：只需实现 `DatasetAdapter` 接口，不改 Schema 和 API。

---

### 5. PostgreSQL text[] 的 JDBC 适配

这是一个隐藏的技术陷阱。MyBatis 的 `JacksonTypeHandler` 会把 `List<String>` 序列化为 JSON 格式 `["a","b"]`，但 PostgreSQL text[] 的 JDBC 期望的是 `Array` 对象，不是字符串——直接 `::text[]` cast 会报错。

**解决方案**：自定义 `PgStringArrayTypeHandler`，通过 JDBC `createArrayOf("text", ...)` 创建正确的数组类型：

```java
Array array = ps.getConnection().createArrayOf("text", parameter.toArray(new String[0]));
ps.setArray(i, array);
```

---

## 作业解答

### Part A · 数据库设计

**Q1：各上传人已通过素材平均文件大小**

```bash
curl http://localhost:8080/api/v1/stats/uploader-avg-size
```

实际结果：吴十 573MB · 赵六 571MB · 刘八 494MB · 陈七 432MB · 张三 100MB
（完整 JSON + SQL 解析见 [queries.md](main/docs/queries.md)）

**Q2：标签 Top 5**

```bash
curl "http://localhost:8080/api/v1/stats/top-tags?topN=5"
```

实际结果：搞笑(20) · 促销(19) · 生活(18) · 节日(17) · 测评(16)

**Q3（自选）：各平台审核通过率**

业务意义：识别哪些平台频繁违规，指导素材采买策略。`CTE + FILTER聚合 + NULLIF防除零`。

```bash
curl http://localhost:8080/api/v1/stats/platform-approval
```

实际结果：qianchuan 29.41%（34条，10通过）· 巨量引擎 null（7条全未通过，NULLIF防除零）

**数据质量**（74 源行 → 69 有效记录）：

| 数据集 | 写入 | 跳过原因 |
| -------- | ------ | --------- |
| 1（25行） | 25 | 无 |
| 2（27行） | 25 | 2条 source_id 重复，ON CONFLICT 幂等合并 |
| 3（22行） | 19 | 3条 title 为空字符串，行级校验拒绝入库 |

---

### Part B · 只读 API

统一响应格式：`{"code":0,"message":"ok","data":{...}}`，列表用 `{items, total, page, pageSize}`。

**过滤操作符**（bracket-style DSL）：

| 语法 | 语义 |
| ------ | ------ |
| `field=v` / `field[eq]=v` | 等于 |
| `field[ne/gt/gte/lt/lte]=v` | 不等于 / 范围 |
| `field[in]=a,b,c` | 枚举包含 |
| `field[like]=x` | 模糊匹配 |
| `tags[has]=x` | 数组包含（PG `@>`） |

**典型请求**：

```bash
# 多字段过滤 + 排序 + 稀疏字段
curl 'http://localhost:8080/api/v1/assets?status=approved&tags[has]=节日&sort=uploaded_at:desc&fields=title,uploader,status'

# 文件大小范围过滤
curl 'http://localhost:8080/api/v1/assets?file_size_bytes[lte]=524288000&sort=file_size_bytes:desc'

# 稀疏字段详情（DB 层 SELECT 投影 + DTO 剥字段双保险）
curl 'http://localhost:8080/api/v1/assets/{id}?fields=title,status,uploader'
```

完整可执行文档：<http://localhost:8080/swagger-ui.html>

---

## 生产差距与演进路径

知道自己的边界，才是架构成熟度的体现。

| 当前局限 | 触发条件 | 生产改进方案 |
| -------- | -------- | ------------ |
| OFFSET 分页，大页 O(N) | 数据量 > 10万行 | Keyset/Cursor 分页，O(1) |
| ILIKE 无中文分词，全表扫描 | 全文检索需求 | `pg_trgm`（轻量）→ `pg_jieba + tsvector`（完整）→ ES（重型） |
| ETL 全量重跑，无增量 | 数据源变为实时流 | Debezium CDC + DLQ（拒绝队列） |
| 无认证鉴权 | 对外暴露 | API Key + Bucket4j 速率限制 → OAuth2 JWT |
| 无可观测性 | 进入生产 | Micrometer + Prometheus + Grafana + OpenTelemetry |
| 未预留 tenant_id | 多租户 | Schema 变更 + 行级安全策略（RLS） |

---

## 运行测试

```bash
cd main/backend
mvn test     # 单元测试：5 Normalizer + QueryDslParser + 3 Adapter（无需 Docker）
mvn verify   # 集成测试：Testcontainers 起真实 PG 容器（禁止 H2，PG 特性不兼容）
```

---

## 文档索引

| 文档 | 内容 |
| ------ | ------ |
| [design.md](main/docs/design.md) | 架构决策完整版、EXPLAIN ANALYZE 分析、前端设计、扩展性演进路径 |
| [schema-design.md](main/docs/schema-design.md) | 完整 DDL + 9个索引设计说明 + 查询计划解读 |
| [queries.md](main/docs/queries.md) | Q1/Q2/Q3 SQL + 实际执行结果 JSON + 时间复杂度分析 |
| [improvements.md](main/docs/improvements.md) | 10项局限性详解与生产级改进路径 |
| [验收报告.md](main/docs/验收报告.md) | 19/19 验收明细 + ETL 修复记录 |
| [验收截图.md](main/docs/验收截图.md) | Docker 启动、前端页面、API 调用截图 |
| [PRD.md](main/docs/PRD.md) | 产品需求、验收标准、技术决策记录（ADR） |

---

✅ **19/19 全部通过**（2026-04-24 真实服务运行验证）——详见 [验收报告.md](main/docs/验收报告.md)。
