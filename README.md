# 视频素材查询服务

基于三份异构数据集统一建模的只读素材查询 API + 轻量管理后台。

**技术栈**：Java 17 · Spring Boot 3.3 · MyBatis-Plus · PostgreSQL 15 · Vue 3 · Docker Compose

---

## 一、快速启动

### 第一步：克隆仓库

```bash
git clone <仓库地址> && cd homework
```

### 第二步：一键启动（三种方式等价，数据结果完全一致）

| 环境 | 命令 | 前提 |
|------|------|------|
| Docker（推荐） | `bash start-docker.sh` | 已装 Docker Desktop |
| macOS / Linux 本地 | `bash start-local.sh` | 无（脚本自动装依赖） |
| Windows 本地 | `.\start-local.ps1`（管理员 PS） | 无（脚本自动装依赖） |

脚本自动处理：端口冲突释放 → 依赖安装 → 数据导入 → 服务启动。

**启动后访问（Docker）**：<http://localhost>（前端）· <http://localhost:8080/swagger-ui.html>（Swagger）

**启动后访问（本地）**：<http://localhost:5173>（前端）· <http://localhost:8080/swagger-ui.html>（Swagger）

---

## 二、作业解答

### Part A · 数据库设计

**为什么选 PostgreSQL，不选 ElasticSearch？**

选型由访问模式决定，不由技术热度决定。本题的访问模式是结构化过滤 + 聚合 + 强一致，PostgreSQL 完全覆盖；ES 的真正优势（全文检索、大规模 facet）本题用不到。具体对比见 [main/docs/design.md §2](main/docs/design.md)。

未来演进路径：若出现全文检索需求，通过 Debezium CDC → Kafka → ES 建读侧 projection，PG 仍作 Source of Truth。

**Schema 如何统一三份格式不一致的数据集？**

一表归一 + ETL 适配器模式。三份数据集在字段命名、时间格式、大小单位、状态枚举、标签分隔符等方面均不一致（真正的异构集成问题，不是简单的字段多少）。每个数据集对应一个 Adapter 做字段映射和归一化，共用同一套 `assets` 表写入逻辑（ON CONFLICT DO UPDATE 幂等）。

**导入结果**（74 源行 → 69 有效记录入库）：

| 数据集 | 源行数 | 写入 | 说明 |
|--------|-------|------|------|
| 1 | 25 | 25 | 无异常 |
| 2 | 27 | 25 | 2 条重复，ON CONFLICT 合并 |
| 3 | 22 | 19 | 3 条 title 为空，归一化拒绝 |

---

**Q1：各上传人平均文件大小（已通过素材）**

```bash
curl http://localhost:8080/api/v1/stats/uploader-avg-size
```

```sql
SELECT uploader, COUNT(*) AS approved_count,
       pg_size_pretty(ROUND(AVG(file_size_bytes))::bigint) AS avg_size_human
FROM assets WHERE status = 'approved'
GROUP BY uploader ORDER BY AVG(file_size_bytes) DESC;
```

实际结果：吴十 573MB · 赵六 571MB · 刘八 494MB · 陈七 432MB · 张三 100MB（完整结果见 [main/docs/queries.md](main/docs/queries.md)）

---

**Q2：标签 Top 5**

```bash
curl http://localhost:8080/api/v1/stats/top-tags?topN=5
```

实际结果：搞笑(20) · 促销(19) · 生活(18) · 节日(17) · 测评(16)

---

**Q3（自选）：各投放平台审核通过率**

业务意义：识别哪些平台频繁违规，优化素材采买策略。

```bash
curl http://localhost:8080/api/v1/stats/platform-approval
```

SQL 用 `CTE + FILTER聚合 + NULLIF防除零`，详见 [main/docs/queries.md](main/docs/queries.md)。

---

### Part B · 只读 API

**设计要点**：

- **过滤**：自研 bracket-style DSL（`field[op]=value`），字段/操作符/返回字段三重白名单。未知字段返回 400，不静默忽略。未选 RSQL，因作业语法 `file_size_bytes[lte]=524288000` 与 RSQL 不兼容。
- **排序防注入**：字段名经枚举映射到列名，MyBatis XML 用 `<foreach>/<choose>` hardcode 列名，禁止 `${orderBy}`。
- **稀疏字段集**：`?fields=title,status` 经白名单过滤后动态生成 SELECT 列，字段不合法返回 400。
- **统一响应格式**：`{"code":0,"message":"ok","data":{...}}`，列表用 `items/total/page/pageSize`。

---

#### 1. 列表查询（多字段过滤）

```bash
GET /api/v1/assets?status=approved&uploader=张三&page=1&page_size=2
```

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "items": [
      {
        "id": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
        "sourceDataset": 1,
        "sourceId": "DS1-001",
        "title": "春节促销视频",
        "uploader": "张三",
        "uploadedAt": "2024-01-15T08:30:00Z",
        "fileSizeBytes": 104857600,
        "status": "approved",
        "tags": ["节日", "促销"],
        "city": "北京",
        "platform": null,
        "reviewer": "李四",
        "remark": "画质清晰，通过",
        "resolution": null,
        "durationSec": null,
        "extra": null,
        "ingestedAt": "2024-04-23T10:00:00Z"
      }
    ],
    "total": 3,
    "page": 1,
    "pageSize": 2
  }
}
```

---

#### 2. 范围过滤（文件大小）

```bash
GET /api/v1/assets?file_size_bytes[lte]=524288000&sort=file_size_bytes:desc&page=1&page_size=2
```

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "items": [
      {
        "id": "7b6e8c12-3d4f-4a19-b2e1-9f8a7c3d5e2b",
        "sourceDataset": 2,
        "sourceId": "DS2-015",
        "title": "夏日户外广告片",
        "uploader": "吴十",
        "uploadedAt": "2024-03-20T14:00:00Z",
        "fileSizeBytes": 524000000,
        "status": "approved",
        "tags": ["生活", "测评"],
        "city": "上海",
        "platform": "抖音",
        "reviewer": null,
        "remark": null,
        "resolution": "1920x1080",
        "durationSec": null,
        "extra": null,
        "ingestedAt": "2024-04-23T10:00:00Z"
      }
    ],
    "total": 45,
    "page": 1,
    "pageSize": 2
  }
}
```

---

#### 3. 多字段排序

```bash
GET /api/v1/assets?sort=uploaded_at:desc,file_size_bytes:asc&page=1&page_size=2
```

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "items": [
      {
        "id": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
        "sourceDataset": 3,
        "sourceId": "DS3-022",
        "title": "搞笑短视频合集",
        "uploader": "赵六",
        "uploadedAt": "2024-04-10T09:15:00Z",
        "fileSizeBytes": 209715200,
        "status": "pending",
        "tags": ["搞笑", "生活"],
        "city": "广州",
        "platform": "快手",
        "reviewer": null,
        "remark": null,
        "resolution": null,
        "durationSec": 180,
        "extra": null,
        "ingestedAt": "2024-04-23T10:00:00Z"
      }
    ],
    "total": 69,
    "page": 1,
    "pageSize": 2
  }
}
```

---

#### 4. 单条记录详情

```bash
GET /api/v1/assets/3fa85f64-5717-4562-b3fc-2c963f66afa6
```

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "id": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
    "sourceDataset": 1,
    "sourceId": "DS1-001",
    "title": "春节促销视频",
    "uploader": "张三",
    "uploadedAt": "2024-01-15T08:30:00Z",
    "fileSizeBytes": 104857600,
    "status": "approved",
    "tags": ["节日", "促销"],
    "city": "北京",
    "platform": null,
    "reviewer": "李四",
    "remark": "画质清晰，通过",
    "resolution": null,
    "durationSec": null,
    "extra": null,
    "ingestedAt": "2024-04-23T10:00:00Z"
  }
}
```

---

#### 5. 稀疏字段集（附加目标）

```bash
GET /api/v1/assets/3fa85f64-5717-4562-b3fc-2c963f66afa6?fields=title,status,uploader
```

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "title": "春节促销视频",
    "status": "approved",
    "uploader": "张三"
  }
}
```

字段不在白名单时返回 400：

```json
{
  "code": 400,
  "message": "Invalid field name: unknown_field",
  "data": null
}
```

---

#### 6. 其他接口

| 接口 | 说明 |
|------|------|
| `GET /api/v1/stats/uploader-avg-size` | Q1：各上传人已通过素材的平均文件大小（含 `avgSizeBytes` + `avgSizeHuman`） |
| `GET /api/v1/stats/top-tags?topN=5` | Q2：标签使用频次 Top N |
| `GET /api/v1/stats/platform-approval` | Q3：各投放平台审核通过率（`CTE + FILTER聚合`，含通过/总量/比例） |
| `GET /api/v1/ingest/runs` | ETL 每次导入运行记录（数据集编号、写入行数、拒绝行数、耗时） |
| `GET /api/v1/ingest/rejects` | ETL 拒绝行明细（原始行内容 + 失败原因，用于数据质量审计） |

完整交互文档（含可执行请求）：<http://localhost:8080/swagger-ui.html>

---

## 三、技术选型

| 技术 | 选型理由 |
| ------ | --------- |
| PostgreSQL 15 | 强一致 + 结构化过滤 + SQL 聚合，ES 在本题无优势 |
| MyBatis-Plus + XML | 动态 SQL 灵活，XML 可完全控制注入防护 |
| Flyway | 版本化 Schema，容器启动自动迁移，无需手动建表 |
| Testcontainers | 集成测试用真实 PostgreSQL，禁止 H2（不支持 text[]/JSONB） |
| Docker Compose | 健康检查 depends_on，启动顺序确定性，评审人一键复现 |

---

## 四、API 速查

### 过滤操作符

| 语法 | 含义 |
|------|------|
| `field=v` / `field[eq]=v` | 等于 |
| `field[ne]=v` | 不等于 |
| `field[gt/gte/lt/lte]=v` | 范围 |
| `field[in]=a,b,c` | 枚举 |
| `field[like]=x` | 模糊 |
| `tags[has]=x` | 标签包含 |

### 全部端点

```bash
GET /api/v1/assets                    # 列表（过滤+排序+分页+稀疏字段）
GET /api/v1/assets/{id}               # 单条详情
GET /api/v1/assets/{id}?fields=a,b    # 稀疏字段详情
GET /api/v1/stats/uploader-avg-size   # Q1：各上传人平均文件大小
GET /api/v1/stats/top-tags?topN=5     # Q2：标签 Top N
GET /api/v1/stats/platform-approval   # Q3：各平台审核通过率
GET /api/v1/ingest/runs               # ETL 运行记录
GET /api/v1/ingest/rejects            # ETL 失败行明细
```

---

## 五、相关文档

| 文档 | 内容 |
| ------ | ------ |
| [design.md](main/docs/design.md) | 架构决策、DB 选型 trade-off、ETL 架构、API 设计、EXPLAIN ANALYZE、演进路径 |
| [schema-design.md](main/docs/schema-design.md) | 完整 DDL + 索引设计说明 + 查询计划解读 |
| [queries.md](main/docs/queries.md) | Q1/Q2/Q3 SQL + 实际执行结果 JSON |
| [improvements.md](main/docs/improvements.md) | 10 项局限性详解与生产级改进方案 |
| [验收报告.md](main/docs/验收报告.md) | 验收结果（19/19 PASS）+ 数据导入明细 + ETL 修复记录 |
| [验收截图.md](main/docs/验收截图.md) | 功能截图（Docker 启动、前端页面、API 调用） |
| [PRD.md](main/docs/PRD.md) | 产品需求文档、验收标准、技术决策记录（ADR） |

**运行测试**：

```bash
cd main/backend
mvn test      # 单元测试（无需 Docker）
mvn verify    # 集成测试（Testcontainers 自动起 PG 容器）
```

---

## 六、验收报告

> **评估日期**: 2026-04-24 | **评估方式**: 真实服务运行 + psql 直查 + API 端点验证 | **最终状态**: ✅ **PASS**

### 总体结果

| 维度 | 状态 | 通过率 |
|------|------|--------|
| 一键启动 | ✅ PASS | 3/3 服务正常 |
| 数据导入 | ✅ PASS | 3/3 数据集（69 条有效记录） |
| Part A 数据库设计 | ✅ PASS | 5/5 要求 |
| Part B API 接口 | ✅ PASS | 6/6 要求 |
| 前端功能 | ✅ PASS | 3/3 页面正常 |

**通过率**: 19/19 (100%)

### Part A · 数据库设计验证

| # | 验收要求 | 状态 | 证据 |
|---|----------|------|------|
| A1 | Schema 包含字段类型定义 | ✅ PASS | `V1__init_assets.sql` + Asset.java 实体 |
| A2 | 索引策略 | ✅ PASS | 8 个索引，对应 Query 场景 |
| A3 | 说明选择原因 | ✅ PASS | §2 访问模式驱动分析 |
| A4 | 导入至少一份数据集 | ✅ PASS | 三份全部导入，共 69 条有效记录 |
| A5 | Q1: 已通过素材各上传人平均大小 | ✅ PASS | 8 位上传人聚合结果 |
| A6 | Q2: 标签 Top 5 | ✅ PASS | 5 个标签及数量 |
| A7 | Q3: 自选查询 | ✅ PASS | 平台审核通过率 |

### Part B · API 接口验证

| # | 验收要求 | 状态 | 证据 |
|---|----------|------|------|
| B1 | GET 列出素材 | ✅ PASS | `/api/v1/assets` 返回 69 条 |
| B2 | 多字段过滤 | ✅ PASS | `?status=approved&sort=uploaded_at:desc` |
| B3 | bracket-style 操作符 | ✅ PASS | `?file_size_bytes[lte]=200000000` |
| B4 | 多字段排序 | ✅ PASS | `?sort=uploaded_at:desc,file_size_bytes:asc` |
| B5 | GET 单条记录 | ✅ PASS | `/api/v1/assets/{id}` |
| B6 | 稀疏字段集 | ✅ PASS | `?fields=title,status,uploader` |

### 数据导入情况

| 数据集 | XLS 行数 | 有效导入 | 跳过原因 |
|--------|----------|----------|----------|
| 数据集1 | 25 | 25 | 无跳过 |
| 数据集2 | 27 | 25 | 2 条 source_id 重复（ON CONFLICT DO UPDATE 合并） |
| 数据集3 | 22 | 19 | 3 条脏数据（title 为空，NOT NULL 约束拒绝） |
| **合计** | **74** | **69** | |

### ETL 修复记录

| 问题 | 根因 | 修复方案 |
|------|------|----------|
| Dataset3 导入 0 条（整批回滚） | `素材title` 为空的行加入批次 → NOT NULL 约束导致整个事务回滚 | 增加行级必填字段校验，null title/uploader/status 行在归一化阶段即被跳过 |
| `"Reject"` 状态归一化失败 | STATUS_MAPPING 未覆盖大小写变体 | 两阶段查找：精确匹配 → 不区分大小写英文匹配 |
| docker-compose 需手动 exec 导入 | IngestRunner 只在 `--ingest` 参数存在时执行 | docker-compose backend 追加 `command: ["--ingest=all"]` |

---

## 七、验收截图

### 一键启动（Docker）

![启动截图1](main/docs/验收截图/image-1.png)
![启动截图2](main/docs/验收截图/image-2.png)
![启动截图3](main/docs/验收截图/image-3.png)
![启动截图4](main/docs/验收截图/image-4.png)

### 前端页面

**数据概览**

![数据概览](main/docs/验收截图/image-5.png)

**素材列表**

![素材列表](main/docs/验收截图/image-8.png)

**展示字段设置**

![字段设置](main/docs/验收截图/image-9.png)

**素材详情**

![素材详情](main/docs/验收截图/image-10.png)

### API 接口

![Swagger UI](main/docs/验收截图/image-15.png)

![API 调用1](main/docs/验收截图/image-12.png)

`GET /api/v1/ingest/runs?page=1&page_size=20`

![ETL 运行记录](main/docs/验收截图/image-14.png)

![API 调用2](main/docs/验收截图/image-17.png)

`GET /api/v1/assets?status=approved&uploader=%E5%BC%A0%E4%B8%89&sort=uploaded_at%3Adesc&fields=title%2Cstatus%2Cuploader&page=1&page_size=20`

![稀疏字段过滤](main/docs/验收截图/image-18.png)

---

## 八、技术设计文档

### §1 系统概述

基于三份字段结构不一致的视频素材数据集，统一建模为单张 `assets` 表，实现只读查询 API 和轻量可视化管理前端。

**核心挑战**：三份数据集在字段命名语言、时间格式、大小单位、枚举值、标签分隔符等方面均不一致——是真正的异构数据集成（Heterogeneous Data Integration）问题，不是简单的字段多少。

---

### §2 数据库选型决策

**选型**：PostgreSQL 15

| 访问模式 | 特征 | 对 DB 的真正要求 |
|---------|------|----------------|
| 写入 | 一次性 ETL，要求幂等 | UNIQUE 约束 + ON CONFLICT DO UPDATE |
| 读取核心 | 多字段结构化过滤 + 排序分页 | B-tree + 组合索引 |
| 读取聚合 | 三条 GROUP BY / UNNEST / FILTER 查询 | 关系代数，SQL 原生 |
| 动态字段 | 三份数据集的稀疏扩展字段 | JSONB + GIN |
| 标签查询 | 数组包含（`tags @> ARRAY[...]`） | text[] + GIN |
| 一致性 | ETL 后立刻需要查询验证 | 强一致，不接受 refresh 延迟 |

**ElasticSearch 为什么不选**：数据量小（~75条），无全文检索需求，ETL 后立查需要强一致，PG 的 JSONB 已覆盖动态字段需求，ES 3节点集群运维成本不合算。

**未来演进路径**：若出现全文检索需求，通过 Debezium CDC → Kafka → ES 建立读侧 projection，PG 仍作为 Source of Truth。

---

### §3 Schema 设计

| 决策 | 选择 | 理由 |
|------|------|------|
| 主键类型 | UUID（gen_random_uuid()） | 避免三套数据集 ID 命名空间冲突 |
| 文件大小单位 | BIGINT bytes | 统一单位，避免精度损失 |
| 时间字段 | TIMESTAMPTZ（UTC） | 避免 Excel 本地时间歧义 |
| 标签字段 | text[] + GIN | 避免分隔符陷阱，支持 `@>` 查询 |
| 状态枚举 | TEXT + CHECK（非 PG ENUM） | ENUM 加值要 ALTER TYPE，CHECK 约束更灵活 |
| 稀疏字段 | extra JSONB + GIN | 避免 EAV 模式，保持单表 |
| 血缘字段 | raw_record JSONB | 支持审计和重跑 ETL |
| 幂等约束 | UNIQUE(source_dataset, source_id) | ETL ON CONFLICT DO UPDATE |

**完整 DDL**：

```sql
CREATE EXTENSION IF NOT EXISTS "pgcrypto";

CREATE TABLE assets (
    id               UUID        NOT NULL DEFAULT gen_random_uuid(),
    source_dataset   SMALLINT    NOT NULL,
    source_id        TEXT        NOT NULL,
    ingested_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    title            TEXT        NOT NULL,
    uploader         TEXT        NOT NULL,
    uploaded_at      TIMESTAMPTZ NOT NULL,
    file_size_bytes  BIGINT      NOT NULL CHECK (file_size_bytes >= 0),
    status           TEXT        NOT NULL CHECK (status IN ('pending', 'approved', 'rejected')),
    tags             TEXT[]      NOT NULL DEFAULT '{}',
    city             TEXT,
    platform         TEXT,
    reviewer         TEXT,
    remark           TEXT,
    resolution       TEXT,
    duration_sec     INT,
    extra            JSONB       NOT NULL DEFAULT '{}'::jsonb,
    raw_record       JSONB       NOT NULL,
    PRIMARY KEY (id),
    UNIQUE (source_dataset, source_id)
);
```

**索引策略**（每个索引对应真实查询场景）：

| 索引名 | 类型 | 字段 | 对应查询 |
|--------|------|------|---------|
| `idx_assets_status_uploader` | B-tree 复合 | (status, uploader) | Q1 聚合：覆盖扫描，避免回表 |
| `idx_assets_tags_gin` | GIN | tags | `tags @>` 数组包含查询 |
| `idx_assets_platform` | B-tree | platform | Q3 聚合，WHERE platform IS NOT NULL |
| `idx_assets_uploaded_at` | B-tree DESC | uploaded_at | 列表默认排序 |
| `idx_assets_file_size_bytes` | B-tree | file_size_bytes | 范围过滤 + 排序 |
| `idx_assets_city` | B-tree | city | 城市等值过滤 |
| `idx_assets_extra_gin` | GIN (jsonb_path_ops) | extra | JSONB 路径查询 |

---

### §4 ETL 架构

```
xls → ExcelReader → DatasetXAdapter → CanonicalAsset → Upsert → PostgreSQL
                           ↓
                    5 个 Normalizer（纯函数、可单元测试）
```

**三份数据集差异处理**：

| 维度 | 数据集1 | 数据集2 | 数据集3 | 归一化方案 |
|------|---------|---------|---------|-----------|
| 时间格式 | Excel 序列号整数 | Excel 序列号含小数 | Unix 秒时间戳 | 阈值 1e9 区分 Unix/Excel |
| 文件大小 | 字符串 "63.76MB" | 字节整数 | 数值+单位双列 | 三路 SizeNormalizer |
| 审核状态 | 中文 | 英文 | 混合 | 映射表 StatusNormalizer |
| 标签格式 | 分号 | 逗号 | Python list 字符串 | 正则提取（禁止 eval） |
| 平台命名 | 无 | 千川 | qianchuan/千川混用 | PlatformNormalizer |

**为什么在应用层做归一化而不在 DB 层**：脏数据清洗是业务语义判断（"通过 = approved"），放 DB 层失去可测性；每个 Normalizer 可独立单元测试覆盖边界值；新增数据集只加一个 Adapter，不改 schema 和 API。

---

### §5 API 设计

题面要求 `file_size[lte]=524288000` 是 bracket-style，与 RSQL/FIQL 不同，自研解析器（~150行）。

**安全四原则**：
1. 字段名 → FilterableField 枚举白名单，未知字段 400
2. 操作符 → FilterOperator 枚举白名单，未知操作符 400
3. 所有值 → MyBatis `#{}` 参数化，零字符串拼接
4. 排序字段 → SortableField 枚举映射到 DB 列名，XML 内 `<foreach>/<choose>` 硬编码列名，完全消除 `${}` 拼接

**稳定分页策略**：即使未传 `sort`，默认也按 `uploaded_at DESC, id DESC` 排序；用户自定义排序时自动追加 `id DESC` 作为 tie-breaker，避免 offset 分页结果漂移。

---

### §6 前端设计

轻量 Vue 3 管理后台，3 页面：

| 页面 | 路由 | 核心功能 |
|------|------|---------|
| Dashboard | `/` | ECharts 可视化三条聚合查询结果 |
| AssetList | `/assets` | 多字段过滤 + 排序 + 稀疏字段 + 分页 |
| AssetDetail | `/assets/:id` | 详情 + `?fields` 开关 |

`utils/queryBuilder.ts` 是前后端契约的唯一来源，把 UI 状态序列化为 DSL 语法。

---

### §7 扩展性设计

> 当前 MVP 采用硬编码 Adapter，以下是面向 **100/1000 份异构数据集** 的演进路径。

**7.1 元数据驱动映射**：将字段血缘配置写入 `dataset_metadata` 表，新增数据集 = 填配置行 + 上传文件，零代码改动。

**7.2 规则优先级引擎**：将清洗规则写入 `normalizer_rules` 表，优先级匹配（高优先级优先），匹配失败回退到默认值或拒绝导入。

**为什么当前版本用硬编码**：3 份数据集明确边界，硬编码更直观可读；每个 Adapter/Normalizer 独立单元测试，无需 mock 配置；5 天 MVP 节奏，清晰的三层 ETL 架构比配置驱动框架更容易展示设计思路。

---

### §8 查询性能（EXPLAIN ANALYZE）

> 环境：PostgreSQL 15，69 条记录，Docker 容器本地执行。数据量小，Seq Scan 是预期行为。

**Q1 — 各上传人平均文件大小**：

```
Sort  (actual time=0.127..0.128 rows=8)
  -> HashAggregate  (actual time=0.052..0.056 rows=8)
       Group Key: uploader
       -> Seq Scan on assets (rows=20 approved of 69)
            Filter: (status = 'approved')
Planning Time: 4.185 ms  |  Execution Time: 1.275 ms
```

**Q2 — 标签 Top 5（UNNEST 展开聚合）**：

```
Limit (rows=5)  (actual time=0.228..0.230 rows=5)
  -> Sort (sort key: count DESC)
       -> HashAggregate (rows=8 distinct tags from 133 expansions)
            -> Nested Loop (69 rows × avg 2 tags/row)
Planning Time: 3.182 ms  |  Execution Time: 0.432 ms
```

**Q3 — 各平台审核通过率（CTE + FILTER）**：

```
Sort (rows=2 platforms)
  -> HashAggregate (Group Key: platform)
       -> HashAggregate (Group Key: platform+status, rows=5 combinations)
            -> Seq Scan on assets (rows=41 with platform IS NOT NULL)
Planning Time: 2.811 ms  |  Execution Time: 0.285 ms
```

---

### §9 局限性与未来改进

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

### §10 技术栈一览

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

---

## 九、核心查询 SQL

### Q1：各上传人平均文件大小（审核已通过）

```sql
SELECT uploader,
       COUNT(*)                                                AS approved_count,
       ROUND(AVG(file_size_bytes))                            AS avg_size_bytes,
       pg_size_pretty(ROUND(AVG(file_size_bytes))::bigint)    AS avg_size_human
FROM   assets
WHERE  status = 'approved'
GROUP  BY uploader
ORDER  BY avg_size_bytes DESC;
```

**实际执行结果**（三份数据集合并，69 条有效记录）：

```json
[
  { "uploader": "吴十",  "approvedCount": 1, "avgSizeBytes": 600834048, "avgSizeHuman": "573 MB" },
  { "uploader": "赵六",  "approvedCount": 3, "avgSizeBytes": 598237075, "avgSizeHuman": "571 MB" },
  { "uploader": "刘八",  "approvedCount": 1, "avgSizeBytes": 518122373, "avgSizeHuman": "494 MB" },
  { "uploader": "陈七",  "approvedCount": 6, "avgSizeBytes": 452877905, "avgSizeHuman": "432 MB" },
  { "uploader": "周九",  "approvedCount": 1, "avgSizeBytes": 351576554, "avgSizeHuman": "335 MB" },
  { "uploader": "王五",  "approvedCount": 2, "avgSizeBytes": 268681872, "avgSizeHuman": "256 MB" },
  { "uploader": "李四",  "approvedCount": 4, "avgSizeBytes": 254503118, "avgSizeHuman": "243 MB" },
  { "uploader": "张三",  "approvedCount": 2, "avgSizeBytes": 104626914, "avgSizeHuman": "100 MB" }
]
```

---

### Q2：标签 Top N（UNNEST 展开聚合）

```sql
SELECT tag, COUNT(*) AS cnt
FROM   assets, UNNEST(tags) AS tag
WHERE  tag IS NOT NULL AND tag <> ''
GROUP  BY tag
ORDER  BY cnt DESC
LIMIT  :topN;
```

**说明**：`UNNEST(tags)` 把 `text[]` 展开为行，每行一个标签值；`LIMIT :topN` 参数化，API 支持 `?topN=10`（默认 5，上限 50）。

**实际执行结果**（topN=5）：

```json
[
  { "tag": "搞笑", "count": 20 },
  { "tag": "促销", "count": 19 },
  { "tag": "生活", "count": 18 },
  { "tag": "节日", "count": 17 },
  { "tag": "测评", "count": 16 }
]
```

---

### Q3：各平台审核通过率（CTE + FILTER 聚合）

```sql
WITH base AS (
    SELECT platform, status, COUNT(*) AS cnt
    FROM   assets
    WHERE  platform IS NOT NULL
    GROUP  BY platform, status
)
SELECT platform,
       SUM(cnt)                                                          AS total,
       SUM(cnt) FILTER (WHERE status = 'approved')                       AS approved_cnt,
       ROUND(
           100.0 * SUM(cnt) FILTER (WHERE status = 'approved')
                   / NULLIF(SUM(cnt), 0),
           2
       )                                                                 AS approval_rate_pct
FROM   base
GROUP  BY platform
ORDER  BY approval_rate_pct DESC NULLS LAST;
```

**说明**：`FILTER (WHERE ...)` 为 SQL:2003 标准条件聚合；`NULLIF(SUM(cnt), 0)` 防除零；`NULLS LAST` 保证通过率 NULL 的平台排在末尾。

**实际执行结果**：

```json
[
  { "platform": "qianchuan", "total": 34, "approvedCnt": 10, "approvalRatePct": 29.41 },
  { "platform": "巨量引擎",   "total":  7, "approvedCnt":  0, "approvalRatePct": null  }
]
```

> `巨量引擎` 平台 7 条记录均未通过审核，通过率返回 null（SQL 使用 `NULLIF` 防除零，`NULLS LAST` 排序）。

---

## 十、局限性详解与改进路径

### 局限 1：OFFSET 分页在大表上劣化

**当前**：`LIMIT 20 OFFSET N`，扫描并丢弃前 N 行，时间 O(N)。

**改进**：Keyset 分页（Cursor 分页）——用上一页最后一行的 `(uploaded_at, id)` 作为游标，每次 O(1)，无论翻到多深。缺点：不支持随机跳页，适合"无限滚动"场景。

---

### 局限 2：ILIKE 不支持中文分词

**当前**：`WHERE title ILIKE '%促销%'`，字节级模糊匹配，前导通配符导致全表扫描。

**改进方案 A（轻量）**：`pg_trgm` + GIN trigram 索引，ILIKE 自动走索引，支持中文（按 UTF-8 码点拆分，非语义分词）。

**改进方案 B（完整）**：`pg_jieba` + `tsvector` 中文分词列，支持语义查询（`@@`），需 Docker 镜像额外编译步骤。

**改进方案 C（重型）**：PG → Debezium → Kafka → ElasticSearch（IK 分词），ES 作读侧 projection，PG 仍为 Source of Truth，约 1s 最终一致延迟。

---

### 局限 3：ETL 全量重跑，无增量更新

**改进**：基于 `updated_at` 的增量 ETL；或文件内容 MD5 校验，未变更文件跳过；生产级则用 Debezium 实时 CDC，无需批量重跑。

---

### 局限 4：无认证鉴权

**方案 A（简单）**：`Authorization: ApiKey your-secret-key`，OncePerRequestFilter 验证 Header。

**方案 B（完整）**：JWT + Spring Security OAuth2 Resource Server。

**方案 C（补充）**：Bucket4j + Redis 速率限制，防爬虫和 DDoS。

---

### 局限 5：无 Metrics/Trace

**改进**：Micrometer + Prometheus（暴露 `/actuator/prometheus`）→ Grafana 可视化；OpenTelemetry Agent → Jaeger/Zipkin 链路追踪；ELK Stack 日志聚合。

---

### 局限 6：ES 演进路径未提前铺垫

```
阶段一（当前）：应用 → PostgreSQL（单一 OLTP 存储）

阶段二（全文检索需求出现）：
  写 → PostgreSQL（SOT）→ Debezium CDC → Kafka → ES Consumer
  读（全文）→ Elasticsearch（读侧投影）
  读（结构化）→ PostgreSQL（原有 API 不变）

阶段三（规模过千万）：
  pg_partman 按 uploaded_at 月份分区；PG 主从复制读写分离；Citus 分片

阶段四（复杂 OLAP）：
  PG → Debezium → Kafka → ClickHouse（多维聚合分析侧）
```

**关键决策**：Debezium 需开启 `wal_level = logical`；从 PG 演进到 PG+ES 的过程中，写接口不需要改，只需加 CDC pipeline。
