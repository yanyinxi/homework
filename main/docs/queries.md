# 查询文档

> 三条核心查询 SQL + 说明 + JSON 响应样例

---

## Q1：各上传人平均文件大小（审核已通过）

### 业务背景

统计每位上传人在"已审核通过"素材中的平均文件大小，帮助运营团队识别"哪些上传人的素材质量/规格更优"，作为素材生产指导依据。

### SQL

```sql
SELECT uploader,
       COUNT(*)                                                     AS approved_count,
       ROUND(AVG(file_size_bytes))                                  AS avg_size_bytes,
       pg_size_pretty(ROUND(AVG(file_size_bytes))::bigint)          AS avg_size_human
FROM   assets
WHERE  status = 'approved'
GROUP  BY uploader
ORDER  BY avg_size_bytes DESC;
```

### 说明

- `WHERE status = 'approved'`：命中 `idx_assets_status_uploader` 组合索引。
- `pg_size_pretty()`：PostgreSQL 内置函数，把字节数格式化为 "63 MB"、"1.2 GB" 等人类可读格式，避免前端做单位换算。
- `ROUND(AVG(...))`：平均字节数取整，精度与业务需求匹配。
- 无 LIMIT：上传人数量有限（通常 < 100），全量返回比分页更简洁。

### 时间复杂度

- 索引扫描：O(k) 其中 k 是 `status='approved'` 的行数
- HashAggregate（GROUP BY uploader）：O(k)
- 排序：O(n log n) 其中 n 是不同上传人数量
- **总体：O(k + n log n)**，k 远小于全表行数，实际接近 O(1) for small datasets

### API 端点

```
GET /api/v1/stats/uploader-avg-size
```

### JSON 响应样例

> 以下为三份数据集（69 条有效记录）合并后的实际执行结果。

```json
{
  "code": 0,
  "message": "ok",
  "data": [
    { "uploader": "吴十", "approvedCount": 1, "avgSizeBytes": 600834048, "avgSizeHuman": "573 MB" },
    { "uploader": "赵六", "approvedCount": 3, "avgSizeBytes": 598237075, "avgSizeHuman": "571 MB" },
    { "uploader": "刘八", "approvedCount": 1, "avgSizeBytes": 518122373, "avgSizeHuman": "494 MB" },
    { "uploader": "陈七", "approvedCount": 6, "avgSizeBytes": 452877905, "avgSizeHuman": "432 MB" },
    { "uploader": "周九", "approvedCount": 1, "avgSizeBytes": 351576554, "avgSizeHuman": "335 MB" },
    { "uploader": "王五", "approvedCount": 2, "avgSizeBytes": 268681872, "avgSizeHuman": "256 MB" },
    { "uploader": "李四", "approvedCount": 4, "avgSizeBytes": 254503118, "avgSizeHuman": "243 MB" },
    { "uploader": "张三", "approvedCount": 2, "avgSizeBytes": 104626914, "avgSizeHuman": "100 MB" }
  ]
}
```

---

## Q2：标签 Top N 素材数量统计（UNNEST 展开）

### 业务背景

统计各标签出现频次，帮助运营团队了解"哪些素材标签最热门"，用于内容策略分析和标签体系优化。

### SQL

```sql
SELECT tag, COUNT(*) AS cnt
FROM   assets, UNNEST(tags) AS tag
WHERE  tag IS NOT NULL AND tag <> ''
GROUP  BY tag
ORDER  BY cnt DESC
LIMIT  :topN;   -- 默认 5，支持参数化
```

### 说明

- `UNNEST(tags)`：把 `text[]` 数组展开为行，每行一个标签值。SQL 标准的 `LATERAL` join 写法等价于上面的逗号写法，PostgreSQL 支持两种。
- `WHERE tag IS NOT NULL AND tag <> ''`：防止空标签进入统计（理论上 ETL 已过滤，双重保险）。
- `GROUP BY tag`：对展开后的标签做聚合。
- `LIMIT :topN`：参数化，API 支持 `?topN=10` 自定义返回数量（默认 5，上限 50）。
- GIN 索引 `idx_assets_tags_gin` 在数组包含查询 `@>` 时使用；UNNEST 展开聚合场景下 PostgreSQL 可能做 Seq Scan（小数据集）或 Bitmap Index Scan（大数据集）。

### 时间复杂度

- UNNEST 展开：O(T)，T 为所有素材标签总数
- HashAggregate（GROUP BY tag）：O(T)
- 排序 + LIMIT：O(D log D)，D 为不同标签数量
- **总体：O(T + D log D)**，T 通常是行数的 3-5 倍

### API 端点

```
GET /api/v1/stats/top-tags?topN=5
```

### JSON 响应样例

> 以下为三份数据集合并后的实际执行结果（topN=5）。

```json
{
  "code": 0,
  "message": "ok",
  "data": [
    { "tag": "搞笑", "count": 20 },
    { "tag": "促销", "count": 19 },
    { "tag": "生活", "count": 18 },
    { "tag": "节日", "count": 17 },
    { "tag": "测评", "count": 16 }
  ]
}
```

---

## Q3：各平台审核通过率（CTE + FILTER 聚合）

### 业务背景

统计各投放平台的审核通过率（已通过 / 总数），帮助运营团队识别"哪些平台的素材更容易通过审核"，优化素材采买策略；也能发现哪些平台频繁违规需要预警。

### SQL

```sql
WITH base AS (
    SELECT platform, status, COUNT(*) AS cnt
    FROM   assets
    WHERE  platform IS NOT NULL
    GROUP  BY platform, status
)
SELECT platform,
       SUM(cnt)                                                         AS total,
       SUM(cnt) FILTER (WHERE status = 'approved')                      AS approved_cnt,
       ROUND(
           100.0 * SUM(cnt) FILTER (WHERE status = 'approved')
                   / NULLIF(SUM(cnt), 0),
           2
       )                                                                AS approval_rate_pct
FROM   base
GROUP  BY platform
ORDER  BY approval_rate_pct DESC NULLS LAST;
```

### 说明

- **CTE（WITH base）**：先在 `(platform, status)` 粒度聚合，减少外层聚合的输入行数，对大数据集有优化效果（PostgreSQL 13+ 会内联 CTE，小数据集效果相近）。
- **`FILTER (WHERE status = 'approved')`**：SQL:2003 标准的条件聚合语法，等价于 `SUM(CASE WHEN status='approved' THEN cnt ELSE 0 END)`，但更语义清晰。
- **`NULLIF(SUM(cnt), 0)`**：防止除零异常。当平台有数据但全部为非 approved 时，返回 NULL 而不是报错。
- **`NULLS LAST`**：通过率为 NULL 的平台（理论上不存在，除非数据异常）排在最后。
- `idx_assets_platform` 索引用于过滤 `platform IS NOT NULL`，命中 Index Cond。

### 时间复杂度

- 内层 CTE（HashAggregate）：O(N)，N 为 `platform IS NOT NULL` 的行数
- 外层聚合（HashAggregate）：O(P * S)，P 为平台数，S 为每平台状态数（通常为 3）
- 排序：O(P log P)
- **总体：O(N + P log P)**，P 极小（个位数），实际接近 O(N)

### API 端点

```
GET /api/v1/stats/platform-approval
```

### JSON 响应样例

> 以下为三份数据集合并后的实际执行结果。`巨量引擎` 平台 7 条记录均未通过审核，通过率返回 null（SQL 使用 `NULLIF` 防除零，`NULLS LAST` 排序）。

```json
{
  "code": 0,
  "message": "ok",
  "data": [
    {
      "platform": "qianchuan",
      "total": 34,
      "approvedCnt": 10,
      "approvalRatePct": 29.41
    },
    {
      "platform": "巨量引擎",
      "total": 7,
      "approvedCnt": 0,
      "approvalRatePct": null
    }
  ]
}
```

---

## 通用说明

### 统一响应格式

所有统计接口使用统一的 `ApiEnvelope` 包装：

```json
{
  "code": 0,
  "message": "ok",
  "data": [ ... ]
}
```

错误响应：

```json
{
  "code": 400,
  "message": "Invalid topN parameter: must be between 1 and 50",
  "errors": []
}
```

### 参数说明

| 接口 | 参数 | 说明 |
|------|------|------|
| `/stats/top-tags` | `topN` | 返回 Top N 标签，默认 5，最大 50 |
| `/stats/uploader-avg-size` | 无 | 返回所有通过审核的上传人统计 |
| `/stats/platform-approval` | 无 | 返回所有有 platform 数据的平台通过率 |
