# 视频素材查询服务

基于三份异构数据集统一建模的只读素材查询 API + 轻量管理后台。

**技术栈**：Java 17 · Spring Boot 3.3 · MyBatis-Plus · PostgreSQL 15 · Vue 3 · Docker Compose

---

## 一、快速启动

### 第一步：克隆仓库

```bash
git clone https://github.com/yanyinxi/homework.git && cd homework
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

### 项目结构

```text
homework/
├── main/
│   ├── backend/        # Spring Boot 3.3，API + ETL，port 8080
│   ├── frontend/       # Vue 3 + Vite，管理后台，port 5173 / 80
│   └── docs/           # 架构设计、SQL、验收报告、截图
├── doc/                # 原始题目 + 三份数据集 XLS
├── docker-compose.yml
└── start-docker.sh / start-local.sh / start-local.ps1
```

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
| [PRD.md](main/docs/PRD.md) | 产品需求文档、验收标准、技术决策记录（ADR） |
| [design.md](main/docs/design.md) | 架构决策、DB 选型 trade-off、ETL 架构、API 设计、EXPLAIN ANALYZE、演进路径 |
| [schema-design.md](main/docs/schema-design.md) |schema设计，完整 DDL + 索引设计说明 + 查询计划解读 |
| [queries.md](main/docs/queries.md) | Q1/Q2/Q3 SQL + 实际执行结果 JSON |
| [improvements.md](main/docs/improvements.md) | 架构迭代重点关注点：10 项局限性详解与生产级改进方案 |
| [验收截图.md](main/docs/验收截图.md) | 功能截图（Docker 启动、前端页面、API 调用） |

 

---

## 六、验收结论

✅ **19/19 全部通过**（2026-04-24 真实服务运行验证）——详见 [验收报告.md](main/docs/验收报告.md)。
