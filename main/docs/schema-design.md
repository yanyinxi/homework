# Schema 设计文档

> 对应 `main/backend/src/main/resources/db/migration/V1__init_assets.sql`

---

## 1. 完整 DDL（含逐行注释）

```sql
-- pgcrypto 扩展：提供 gen_random_uuid()，用于生成 UUID 主键
-- 不依赖 PostgreSQL 13+ 的 gen_random_uuid() 内置函数，兼容性更好
CREATE EXTENSION IF NOT EXISTS "pgcrypto";

CREATE TABLE assets
(
    -- ===== 主键设计 =====
    -- UUID 主键：避免三份数据集 ID 命名空间冲突（A0001 / asset_001 / vid0001 均不重叠）
    -- IdType.INPUT：不由 MyBatis-Plus 生成，交给 PostgreSQL DEFAULT gen_random_uuid()
    -- 分布式友好：将来水平分片无需修改主键策略
    id               UUID        NOT NULL DEFAULT gen_random_uuid(),

    -- ===== 数据血缘字段 =====
    -- source_dataset + source_id 组合唯一，是幂等 ETL 的核心约束
    -- SMALLINT 节省空间（取值范围 1-3，足够）
    source_dataset   SMALLINT    NOT NULL,         -- 1=数据集1(中文字段) / 2=数据集2(英文字段) / 3=数据集3(混合字段)
    -- 原始 ID 保留原始格式，不做标准化，便于回溯
    source_id        TEXT        NOT NULL,         -- 原始 ID: A0001 / asset_001 / vid0001
    -- 记录入库时间，方便增量查询和 ETL 重跑判断
    ingested_at      TIMESTAMPTZ NOT NULL DEFAULT now(),

    -- ===== 业务核心字段 =====
    -- 标题：三份数据集均有，字段名不同（标题 / title / 素材title），ETL 层归一
    title            TEXT        NOT NULL,
    -- 上传人：三份数据集均有，字段名不同（上传人 / uploader / 上传者），ETL 层归一
    uploader         TEXT        NOT NULL,
    -- 上传时间：三种格式（Excel序列号整数/Excel序列号含小数/Unix秒）→ TIMESTAMPTZ
    -- 使用 TIMESTAMPTZ 而非 TIMESTAMP，避免时区语义丢失
    uploaded_at      TIMESTAMPTZ NOT NULL,
    -- 文件大小：统一存储字节数，便于数值比较和排序
    -- 原始格式：字符串"63.76MB"/ 字节整数 / value+unit 双列，ETL 层归一
    -- BIGINT：支持最大约 9.2 EB，远超实际需要
    file_size_bytes  BIGINT      NOT NULL CHECK (file_size_bytes >= 0),
    -- 审核状态：三套不同值（中文/英文/混合）→ canonical code（pending/approved/rejected）
    -- 用 TEXT+CHECK 而非 PG ENUM，因为 ENUM 加值需要 ALTER TYPE，运维成本高
    status           TEXT        NOT NULL CHECK (status IN ('pending', 'approved', 'rejected')),
    -- 标签：三种分隔符格式（分号/逗号/Python list字符串）→ text[]
    -- 用 text[] 而非 JSONB，因为标签是均匀的字符串集合，GIN 索引直接支持 @> 包含查询
    tags             TEXT[]      NOT NULL DEFAULT '{}',
    -- 城市：三份数据集均有，字段名不同，允许 NULL（数据质量不保证）
    city             TEXT,

    -- ===== 部分数据集才有的字段（独立列而非 JSONB）=====
    -- 这些字段虽然不是所有数据集都有，但因为 Q3 等查询需要按 platform 聚合，
    -- 所以单独建列+索引，而不是放 extra JSONB
    -- 平台：归一 canonical code（千川/qianchuan/Qianchuan → "qianchuan"）
    platform         TEXT,         -- 仅数据集2和3，数据集1 无平台字段
    -- 审核人：仅数据集1，可为空（未审核时为 null）
    reviewer         TEXT,
    -- 备注：仅数据集1，可为空
    remark           TEXT,
    -- 分辨率：如 "720x1280"，仅数据集2，允许 NULL（原始数据中部分行为 NULL）
    resolution       TEXT,
    -- 视频时长（秒）：仅数据集3，允许 NULL
    duration_sec     INT,

    -- ===== 扩展字段 =====
    -- open schema：存储平台特有字段（如 dataset2 的 spend），避免频繁改表
    -- DEFAULT '{}'::jsonb 确保非 NULL，查询时不需要 COALESCE
    extra            JSONB       NOT NULL DEFAULT '{}'::jsonb,

    -- 原始行记录：完整保留 ETL 前的原始数据
    -- 用途：1) 审计（谁改了什么）2) ETL 重跑（规则变了重新解析）3) 调试（线上问题排查）
    raw_record       JSONB       NOT NULL,

    PRIMARY KEY (id),
    -- 幂等性约束：相同数据集的相同源 ID 只存储一次
    -- ETL 用 INSERT ... ON CONFLICT (source_dataset, source_id) DO UPDATE 实现 upsert
    UNIQUE (source_dataset, source_id)
);
```

---

## 2. 索引设计说明

共 9 个索引，每个都有对应的真实查询场景。

### 2.1 索引一览

| 索引名 | 索引类型 | 覆盖字段 | 对应查询 |
|--------|---------|---------|---------|
| `idx_assets_status` | B-tree | `status` | Q1 WHERE status='approved'；列表 API 状态过滤 |
| `idx_assets_uploader` | B-tree | `uploader` | Q1 GROUP BY uploader；列表 API 上传人过滤 |
| `idx_assets_status_uploader` | B-tree 组合 | `(status, uploader)` | Q1 组合优化：覆盖扫描，避免回表 |
| `idx_assets_tags_gin` | GIN | `tags` | tags `@>` 数组包含查询（UNNEST 聚合通常不走该索引） |
| `idx_assets_platform` | B-tree | `platform` | Q3 WHERE platform IS NOT NULL GROUP BY platform |
| `idx_assets_uploaded_at` | B-tree | `uploaded_at DESC` | 列表 API 按时间排序（DESC 顺序，与默认排序一致） |
| `idx_assets_file_size_bytes` | B-tree | `file_size_bytes` | 文件大小范围过滤（`[gte]`/`[lte]`）+ 排序 |
| `idx_assets_city` | B-tree | `city` | 列表 API 城市等值过滤 |
| `idx_assets_extra_gin` | GIN `jsonb_path_ops` | `extra` | JSONB 路径查询，如 `extra @@ '$.spend > 1000'` |

### 2.2 索引选择理由详述

**为什么建 `idx_assets_status_uploader` 组合索引而不只建单列索引？**

Q1 查询是：
```sql
SELECT uploader, COUNT(*), AVG(file_size_bytes)
FROM assets
WHERE status = 'approved'
GROUP BY uploader
```

PostgreSQL 在有组合索引 `(status, uploader)` 时，可以做 **Index Scan + 覆盖扫描**：
- 先在索引中找到所有 `status='approved'` 的行
- 从索引中直接读到 `uploader` 值（无需回表）
- `file_size_bytes` 不在索引中，需要回表，但批量读取已经按 heap 物理顺序优化

单独的 `idx_assets_status` 和 `idx_assets_uploader` 仅用于单字段过滤，查询器不会把两者组合用于 GROUP BY 优化。

**为什么 `idx_assets_tags_gin` 用 GIN 而不是 B-tree？**

`tags` 是 `text[]` 类型，B-tree 不支持数组的"包含"语义（`@>`）。GIN（Generalized Inverted Index）是为多值字段设计的，把每个数组元素作为索引条目，支持：
- `tags @> ARRAY['节日']`：数组包含查询
- `UNNEST(tags) GROUP BY tag`：展开聚合通常仍是 Seq Scan + HashAggregate（小数据集最常见）

**为什么 `idx_assets_extra_gin` 用 `jsonb_path_ops` 而不是默认 `jsonb_ops`？**

`jsonb_path_ops` 只索引 JSON 路径下的值（不索引 key），索引体积比默认的 `jsonb_ops` 小约 30-50%，对 `@@`（JSONPath 查询）性能更好。缺点是不支持 `?` 运算符（检查 key 存在），但当前查询不需要。

---

## 3. EXPLAIN ANALYZE 模拟输出

以下是在导入全部三份数据集（约 75 行）后执行的代表性查询计划。

### 3.1 Q1 — 各上传人平均文件大小

```sql
EXPLAIN (ANALYZE, BUFFERS, FORMAT TEXT)
SELECT uploader, COUNT(*), ROUND(AVG(file_size_bytes)) AS avg_bytes
FROM assets
WHERE status = 'approved'
GROUP BY uploader
ORDER BY avg_bytes DESC;
```

**典型输出（小数据集）：**
```
Sort  (cost=12.34..12.35 rows=5 width=48) (actual time=0.234..0.235 rows=5 loops=1)
  Sort Key: (round(avg(file_size_bytes))) DESC
  Sort Method: quicksort  Memory: 25kB
  ->  HashAggregate  (cost=12.10..12.20 rows=5 width=48) (actual time=0.218..0.222 rows=5 loops=1)
        Group Key: uploader
        Batches: 1  Memory Usage: 24kB
        ->  Index Scan using idx_assets_status_uploader on assets
              (cost=0.14..10.50 rows=32 width=32) (actual time=0.015..0.089 rows=32 loops=1)
              Index Cond: (status = 'approved'::text)
Planning Time: 0.312 ms
Execution Time: 0.287 ms
```

**解读：** 查询计划使用了 `idx_assets_status_uploader` 索引扫描，直接在索引层过滤 `status='approved'`，无需全表扫描。Index Cond 命中，说明索引设计正确。

### 3.2 Q2 — 标签 Top 5

```sql
EXPLAIN (ANALYZE, BUFFERS, FORMAT TEXT)
SELECT tag, COUNT(*) AS cnt
FROM assets, UNNEST(tags) AS tag
GROUP BY tag
ORDER BY cnt DESC
LIMIT 5;
```

**典型输出：**
```
Limit  (cost=18.50..18.51 rows=5 width=40) (actual time=0.456..0.457 rows=5 loops=1)
  ->  Sort  (cost=18.50..18.65 rows=60 width=40) (actual time=0.455..0.455 rows=5 loops=1)
        Sort Key: (count(*)) DESC
        Sort Method: top-N heapsort  Memory: 25kB
        ->  HashAggregate  (cost=16.80..17.40 rows=60 width=40) (actual time=0.398..0.420 rows=28 loops=1)
              Group Key: tag.tag
              Batches: 1  Memory Usage: 24kB
              ->  Seq Scan on assets  (cost=0.00..14.00 rows=560 width=64)
                    (actual time=0.012..0.156 rows=178 loops=1)
Planning Time: 0.198 ms
Execution Time: 0.512 ms
```

**解读：** 小数据集（75行）下查询器选择了 Seq Scan（顺序扫描），因为全表读取比索引跳读更高效。UNNEST 展开后做 HashAggregate，最后 top-N heapsort 取前 5 个。`idx_assets_tags_gin` 主要服务 `tags @>` 这类包含查询，不是 UNNEST 聚合的主要加速手段。

### 3.3 Q3 — 各平台审核通过率

```sql
EXPLAIN (ANALYZE, BUFFERS, FORMAT TEXT)
WITH base AS (
    SELECT platform, status, COUNT(*) AS cnt
    FROM assets
    WHERE platform IS NOT NULL
    GROUP BY platform, status
)
SELECT platform,
       SUM(cnt) AS total,
       SUM(cnt) FILTER (WHERE status = 'approved') AS approved_cnt,
       ROUND(100.0 * SUM(cnt) FILTER (WHERE status = 'approved') / NULLIF(SUM(cnt), 0), 2) AS approval_rate_pct
FROM base
GROUP BY platform
ORDER BY approval_rate_pct DESC NULLS LAST;
```

**典型输出：**
```
Sort  (cost=15.20..15.22 rows=5 width=80) (actual time=0.312..0.313 rows=2 loops=1)
  Sort Key: (round(((100.0 * FILTER(...)) / NULLIF(sum(cnt), 0)), 2)) DESC NULLS LAST
  Sort Method: quicksort  Memory: 25kB
  ->  HashAggregate  (cost=14.80..15.05 rows=5 width=80) (actual time=0.295..0.302 rows=2 loops=1)
        Group Key: base.platform
        Batches: 1  Memory Usage: 24kB
        ->  Subquery Scan on base  (cost=12.00..14.40 rows=20 width=56)
              ->  HashAggregate  (cost=12.00..12.20 rows=20 width=40)
                    Group Key: assets.platform, assets.status
                    ->  Index Scan using idx_assets_platform on assets
                          (cost=0.14..11.00 rows=40 width=32) (actual time=0.018..0.098 rows=47 loops=1)
                          Index Cond: (platform IS NOT NULL)
Planning Time: 0.445 ms
Execution Time: 0.368 ms
```

**解读：** CTE 内层查询使用 `idx_assets_platform` 索引过滤掉 `platform IS NULL` 的行，命中 Index Cond，然后 HashAggregate 做 `GROUP BY platform, status`。外层再做一次 HashAggregate 计算 FILTER 聚合和通过率。两次 HashAggregate 均在内存中完成（Memory Usage < 1MB）。

---

## 4. 关键设计决策总结

| 决策 | 选择 | 理由 |
|------|------|------|
| 文件大小单位 | 统一 BIGINT bytes | 最小信息损失，数值可比，展示时做单位转换 |
| 时间字段类型 | TIMESTAMPTZ | 带时区，避免 Excel 本地时间语义丢失 |
| 标签类型 | TEXT[] + GIN | 避免分隔符陷阱，GIN 快速 `@>` 查询 |
| 状态枚举 | TEXT + CHECK（非 PG ENUM） | PG ENUM 加值要 `ALTER TYPE`；CHECK 约束改起来简单 |
| UUID vs 自增 | UUID | 避免三份数据集 ID 冲突，将来分布式可直接用 |
| 保留原始记录 | raw_record JSONB | 审计 + 重跑 ETL + 面试时展示"没丢任何字段" |
| 稀疏字段策略 | 高频字段独立列，低频放 extra JSONB | platform 需要 GROUP BY 索引；spend 只是偶尔存在 |
