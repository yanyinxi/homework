# 后端代码审查报告

**审查日期**: 2026-04-27  
**审查范围**: `main/backend/src/main/java/` 下全部 Java 源码 + MyBatis XML + application.yml + Flyway 迁移  
**审查人**: Claude Code (架构师专家模式)  
**文件总数**: 47 个 (含 XML / YML / SQL)

---

## 问题统计

| 严重程度 | 数量 | 定义 |
|----------|------|------|
| **严重** | 1 | 可导致数据错误、服务崩溃或明确安全漏洞 |
| **高** | 3 | 显著性能问题或潜在安全风险，需优先修复 |
| **中** | 11 | 非立即致命但增加运维风险或技术债务 |
| **低** | 13 | 代码异味、最佳实践偏离、可维护性问题 |

**总计: 28 个问题**

---

## 严重 (CRITICAL)

### C1. IngestBatchService — N+1 Upsert 导致批量导入性能极差

- **文件**: `main/backend/src/main/java/com/homework/asset/ingest/IngestBatchService.java`
- **行号**: 38-40 (`upsertBatch` 方法)
- **严重程度**: **严重**
- **描述**:
  ```java
  for (Asset asset : assets) {
    assetMapper.upsert(asset);  // 每条记录一次 DB 往返
  }
  ```
  该方法对列表中的每个 Asset 逐条执行 `INSERT ... ON CONFLICT DO UPDATE`。每行一次数据库往返。1000 行 = 1000 次 DB 调用。在大批量导入场景下（例如 10 万行数据集），耗时可能从秒级变成分钟级，并且持有一个长事务阻塞其他写入。

  `upsertBatchWithStats` (行 46-63) 有相同问题。

- **修复建议**: 使用 `JdbcTemplate.batchUpdate()` 或 MyBatis `<foreach>` 批量 INSERT 实现真正的批量写入。伪代码：
  ```java
  jdbcTemplate.batchUpdate(mergedSql, new BatchPreparedStatementSetter() { ... });
  ```
  批量大小建议 500-1000 条一批。如果仍需要统计插入/更新数量，在批量前后各做一次 `batchCheckExists` 对比。

---

## 高 (HIGH)

### H1. IngestBatchService — batchCheckExists 动态 IN 子句无数量上限

- **文件**: `main/backend/src/main/java/com/homework/asset/ingest/IngestBatchService.java`
- **行号**: 70-82
- **严重程度**: **高**
- **描述**: `batchCheckExists` 方法通过字符串拼接构建 `WHERE (source_dataset, source_id) IN ((?,?),(?,?),...)`，参数数量 = 2 * assets.size()。当 assets 数量很大时（例如 10 万行），SQL 长度和参数数量会超出 PostgreSQL 的合理执行范围。虽然 PG 理论上支持 32767 个参数，但实际上 SQL 解析和计划时间会急剧增长。
- **修复建议**: 对 assets 列表分块（chunk size 500），每个 chunk 独立查询并汇总结果。

### H2. ApiKeyAuthFilter — API Key 明文存储且无恒定时间比较

- **文件**: `main/backend/src/main/java/com/homework/asset/config/ApiKeyAuthFilter.java`
- **行号**: 113-117 (`findApiKey` 方法)
- **严重程度**: **高**
- **描述**:
  1. `e.getKey().equals(key)` 使用 `String.equals()`，其短路行为（逐字符比较，遇到第一个不同即返回 false）可被时序攻击利用。攻击者通过测量响应时间差异，可以逐字符猜解有效 API Key。
  2. `application.yml` 中 API Key 以明文存储（行 104-109），任何能访问配置文件的人都能获取密钥。
- **修复建议**:
  - 使用 `MessageDigest.isEqual(key.getBytes(StandardCharsets.UTF_8), e.getKey().getBytes(StandardCharsets.UTF_8))` 做恒定时间比较
  - API Key 存储改为哈希值（SHA-256），传入的 Key 先哈希再比对
  - 生产环境通过环境变量或 Vault 注入明文 Key，启动时计算哈希存入内存

### H3. AssetMapper.xml — JacksonTypeHandler + `::jsonb` cast 组合脆弱

- **文件**: `main/backend/src/main/resources/mapper/AssetMapper.xml`
- **行号**: 109-110
- **严重程度**: **高**
- **描述**:
  ```xml
  #{extra, typeHandler=...JacksonTypeHandler}::jsonb
  #{rawRecord, typeHandler=...JacksonTypeHandler}::jsonb
  ```
  JacksonTypeHandler 通过 `ps.setString(i, jsonString)` 设置参数，然后在 SQL 中用 `::jsonb` cast 将文本转为 JSONB。这个链路有两处隐患：
  1. Jackson 序列化的 JSON 字符串如果包含特殊字符（如单引号、反斜杠），在 PG 的 `::jsonb` cast 中可能被误解析
  2. PostgreSQL 的 `text::jsonb` cast 对输入格式要求严格，Jackson 的默认序列化格式（如数字精度、null 处理）可能与 PG 预期不完全一致

- **修复建议**: 使用自定义 TypeHandler 通过 `ps.setObject(i, jsonString, OTHER)` 直接传递 JSONB（JDBC 4.2+ 支持），或确保 Jackson 配置 `WRITE_NULL_MAP_VALUES=false` 并添加集成测试覆盖各种 JSON 边界值（嵌套对象、特殊字符、大数字）。

---

## 中 (MEDIUM)

### M1. SlowQueryInterceptor — 反射遍历 `h` 代理链脆弱

- **文件**: `main/backend/src/main/java/com/homework/asset/config/SlowQueryInterceptor.java`
- **行号**: 79-81
- **严重程度**: **中**
- **描述**:
  ```java
  while (metaObject.hasGetter("h")) {
    Object h = metaObject.getValue("h");
    metaObject = SystemMetaObject.forObject(h);
  }
  ```
  这段代码依赖 MyBatis 内部实现细节：StatementHandler 的代理对象通过 `h` 字段链式委托。如果 MyBatis 版本升级改变内部代理结构（例如改用 `target` 字段、JDK 动态代理），此代码将静默失败——`mappedStatement` 获取到 `null`，日志中 statementId 显示为 `"unknown"`。
- **修复建议**: 在 `catch` 块中捕获异常并降级记录 `"unknown"`，或通过 `Plugin` 机制注册时需要传递 `MappedStatement` 到拦截器签名中。长期方案：改用 MyBatis 自带 SQL 日志或 APM 工具（如 SkyWalking/p6spy）。

### M2. DatasetMappingLoader — `loaded` 字段缺少 volatile，存在可见性风险

- **文件**: `main/backend/src/main/java/com/homework/asset/ingest/mapping/DatasetMappingLoader.java`
- **行号**: 22, 79-83
- **严重程度**: **中**
- **描述**:
  ```java
  private boolean loaded = false;  // 非 volatile
  ...
  private void ensureLoaded() {
    if (!loaded) {  // 读操作无同步
      load();       // synchronized 方法
    }
  }
  ```
  多线程环境下，线程 A 调用 `load()` 完成后设置 `loaded = true`，线程 B 通过 `ensureLoaded()` 读取 `loaded` 时可能因为 CPU 缓存未刷新而仍看到 `false`，从而重复调用 `load()`。虽然 `load()` 的 `synchronized` 保证了不会重复加载数据，但存在不必要的锁竞争。
- **修复建议**: 将 `loaded` 声明为 `private volatile boolean loaded = false;`

### M3. AssetCommandService.detectAdapter — 数据集格式检测逻辑脆弱

- **文件**: `main/backend/src/main/java/com/homework/asset/service/AssetCommandService.java`
- **行号**: 208-232
- **严重程度**: **中**
- **描述**: 数据集格式检测通过检查特定的 header 名称（如 "素材编号"、"asset_id"、"ID"）来判断。这种硬编码检测逻辑在以下场景会失败：
  1. 同一批 Excel 有多个匹配条件（如同时包含 "素材编号" 和 "asset_id"）
  2. 新增数据集格式需要修改 Service 代码
  3. 项目中已有 `DynamicDatasetAdapter` + `DatasetMappingLoader` 的配置驱动方案，但写入接口没有使用它
- **修复建议**: 统一使用 `DatasetMappingLoader` 加载配置做匹配。如果必须保留硬编码检测，优先级声明应写在代码注释中，并结合 `filePattern` 做文件名匹配。

### M4. ExcelReader — 文件扩展名检测存在 NPE 风险

- **文件**: `main/backend/src/main/java/com/homework/asset/ingest/excel/ExcelReader.java`
- **行号**: 42-45
- **严重程度**: **中**
- **描述**:
  ```java
  if (fileName != null && fileName.toLowerCase().endsWith(".xlsx")) {
    return new XSSFWorkbook(stream);
  }
  return new HSSFWorkbook(stream);  // 默认按 .xls 处理
  ```
  两个问题：
  1. `.xlsm`（宏启用的 Excel 文件）会被错误地按 HSSF 处理，导致 `OfficeXmlFileException`
  2. 如果传入一个合法 `.xlsx` 文件但文件名全部大写（`.XLSX`），`toLowerCase()` 已处理，但 `.xls` 和 `.xlsx` 之外的所有格式（如 `.csv`, `.ods`）都会静默走到 HSSF 分支并抛出难以理解的 POI 异常
- **修复建议**: 使用 `WorkbookFactory.create(stream)` 让 POI 自动检测格式；或将默认分支改为显式抛出 `ApiException(400, "Unsupported file format")`。

### M5. QueryDslParser.parseTimestamp — LocalDateTime 隐式假定 UTC

- **文件**: `main/backend/src/main/java/com/homework/asset/api/query/QueryDslParser.java`
- **行号**: 253-255
- **严重程度**: **中**
- **描述**:
  ```java
  return Timestamp.from(LocalDateTime.parse(trimmed).atZone(ZoneOffset.UTC).toInstant());
  ```
  当用户提交不带时区的日期时间字符串（如 `2026-04-24T11:00:00`）时，代码将其按 UTC 解析。但用户可能在中国（UTC+8），实际想查询的是北京时间 11 点。这会返回错误结果，且没有任何警告。
- **修复建议**: 在 API 文档中明确声明：不带时区的日期时间字符串一律按 UTC 处理；或使用 `ZonedDateTime.parse()` 并在缺失时区时返回 400 错误要求用户明确指定时区。

### M6. IngestBatchService — `sourceDataset` 可能为 null 导致 NPE

- **文件**: `main/backend/src/main/java/com/homework/asset/ingest/IngestBatchService.java`
- **行号**: 53
- **严重程度**: **中**
- **描述**:
  ```java
  String key = asset.getSourceDataset() + ":" + asset.getSourceId();
  ```
  `Asset.sourceDataset` 是 `Integer`（可为 null）。若 ETL Adapter 未正确设置 `sourceDataset`，此字符串拼接会生成 `"null:xxx"` 而非抛出异常，导致 `existingKeys` 匹配逻辑失效，统计的 inserted/updated 计数不准确。
- **修复建议**: 在 `upsertBatchWithStats` 方法开头对每个 Asset 做 `Objects.requireNonNull(asset.getSourceDataset())` 断言，或在 Adapter 的 `convert()` 方法中添加非空校验。

### M7. ApiSecurityConfig — Swagger/Actuator 端点无认证暴露

- **文件**: `main/backend/src/main/java/com/homework/asset/config/ApiSecurityConfig.java`
- **行号**: 68-75
- **严重程度**: **中**
- **描述**: `/swagger-ui/**`、`/v3/api-docs/**`、`/actuator/health`、`/actuator/prometheus` 等端点全部 `permitAll()`。在生产环境中，Swagger UI 会完整暴露 API 结构和参数格式，Actuator 端点会暴露 JVM 指标、健康检查详情。这是信息泄漏风险。
- **修复建议**: 通过 profile 控制：`dev` profile 开放 Swagger，`prod` profile 关闭 Swagger 并为 Actuator 端点添加 Basic Auth 或统一 API Key 认证。

### M8. SlowQueryInterceptor — log.debug 在每次查询都执行

- **文件**: `main/backend/src/main/java/com/homework/asset/config/SlowQueryInterceptor.java`
- **行号**: 100-102
- **严重程度**: **中**
- **描述**: 即使查询 < 500ms，每次仍然执行 `sql.replaceAll("\\s+", " ").trim()` 和字符串截断操作，然后调用 `log.debug()`。虽然 `log.debug` 在 INFO 级别下不输出，但 `sql.replaceAll` 和 `substring` 仍然执行，对高 QPS 场景有微小但可积累的 CPU 开销。
- **修复建议**: 用 `if (log.isDebugEnabled())` 包裹 debug 日志的 SQL 处理逻辑；或将 SQL 处理提前到 `duration` 判断之前但只在需要时才做字符串替换。

### M9. AssetMapper.xml — ILIKE 缺少 pg_trgm 索引支持

- **文件**: `main/backend/src/main/resources/mapper/AssetMapper.xml`
- **行号**: 16, 19
- **严重程度**: **中**
- **描述**:
  ```xml
  AND uploader ILIKE '%' || #{params.uploaderLike} || '%'
  AND title ILIKE '%' || #{params.titleLike} || '%'
  ```
  前导通配符 `%` 使 B-tree 索引完全失效，每次过滤都需要全表扫描（Seq Scan）。目前 `idx_assets_uploader` 和没有 title 索引均无法优化此类查询。
- **修复建议**: 添加 `CREATE EXTENSION IF NOT EXISTS pg_trgm;` 并为 `uploader` 和 `title` 列创建 `CREATE INDEX idx_assets_uploader_trgm ON assets USING GIN (uploader gin_trgm_ops);`。同时考虑添加 `app.query.max-like-results` 配置限制模糊查询的结果上限。

### M10. ApiKeyAuthFilter — JSON 响应手动拼接

- **文件**: `main/backend/src/main/java/com/homework/asset/config/ApiKeyAuthFilter.java`
- **行号**: 144-148
- **严重程度**: **中**
- **描述**:
  ```java
  response.getWriter().write("{\"code\":401,\"message\":\"" + message + "\"}");
  ```
  虽然当前 `message` 值都来自硬编码字符串，不存在注入风险，但此模式很容易在后续维护中被突破（例如有人改成 `"Invalid API Key: " + apiKey`）。同时过滤器直接写响应体绕过了 Spring 的 `GlobalExceptionHandler`，导致 401 响应格式与业务异常 400/404 的格式不完全一致（缺少外层 `ApiEnvelope` 包装）。
- **修复建议**: 改为抛出 `AuthenticationException` 让 Spring Security 的 `AuthenticationEntryPoint` 处理，或在过滤器中注入 `ObjectMapper` 序列化一个标准 Error DTO。

### M11. AssetMapper.xml — ON CONFLICT DO UPDATE 无乐观锁/版本检查

- **文件**: `main/backend/src/main/resources/mapper/AssetMapper.xml`
- **行号**: 98-128
- **严重程度**: **中**
- **描述**: `ON CONFLICT (source_dataset, source_id) DO UPDATE SET ... ingested_at = now()` 无条件覆盖所有字段。如果两个并发请求上传同一份 Excel（相同 source_id），后到达的请求会静默覆盖前者的所有字段，且没有任何并发冲突提示。
- **修复建议**: 根据业务语义选择策略：
  - 如 Excel 导入是完全幂等的（最新数据始终覆盖旧数据），当前行为正确，但应在代码注释中说明
  - 如需保护已有数据不被覆盖，添加 `WHERE assets.ingested_at < EXCLUDED.ingested_at` 条件
  - 添加 `version` 字段做乐观锁

---

## 低 (LOW)

### L1. AssetCommandService — rawId 提取可能产生 "null" 字符串

- **文件**: `main/backend/src/main/java/com/homework/asset/service/AssetCommandService.java`
- **行号**: 82
- **严重程度**: **低**
- **描述**: `String.valueOf(rawRow.getOrDefault(..., rawRow.getOrDefault(..., "unknown")))` 链式 `getOrDefault` 中，如果外层的 key 存在但值为 null，`String.valueOf(null)` 会返回字符串 `"null"` 而不是跳过取下一个默认值。
- **修复建议**: 改用显式判空：`Object v = rawRow.get("素材编号"); if (v == null) v = rawRow.get("asset_id"); ... return v != null ? v.toString() : "unknown";`

### L2. IngestRunner — Error message string concatenation in log

- **文件**: `main/backend/src/main/java/com/homework/asset/ingest/IngestRunner.java`
- **行号**: 196
- **严重程度**: **低**
- **描述**: `log.warn("数据集 {} 行归一化失败，跳过: {}，原因: {}", datasetNum, row, e.getMessage())` — 将整个 `row` Map 对象写入日志。大 Map 会产生长日志行，且 raw data 可能包含敏感信息。
- **修复建议**: 仅记录 sourceId 和 rowNum，完整 raw data 通过 `IngestAuditService.recordReject` 落库。

### L3. Asset.java — extra 和 rawRecord 类型为 Object

- **文件**: `main/backend/src/main/java/com/homework/asset/domain/entity/Asset.java`
- **行号**: 69, 73
- **严重程度**: **低**
- **描述**: `extra` 和 `rawRecord` 声明为 `Object` 类型。从 PG 读回时 JacksonTypeHandler 反序列化为 `LinkedHashMap`/`ArrayList`，但方法签名不体现类型信息。调用方无法从 API 推断期望类型，编译期无保护。
- **修复建议**: `extra` 声明为 `Map<String, Object>`，`rawRecord` 声明为 `Map<String, Object>`。或使用 Java record + Jackson 反序列化目标类型。

### L4. AssetCommandService — 每次 new Adapter 实例

- **文件**: `main/backend/src/main/java/com/homework/asset/service/AssetCommandService.java`
- **行号**: 200-205, 213-229
- **严重程度**: **低**
- **描述**: `getAdapterByDatasetNumber()` 和 `detectAdapter()` 每次都 `new Dataset1Adapter()` 创建新实例。Adapter 是无状态的，重复创建浪费 GC 压力。同时 `IngestRunner` (行 58-61) 又维护了一份 `ADAPTERS` 静态 Map 缓存实例，两个地方不一致。
- **修复建议**: 统一复用同一个 Adapter 实例，或将 Adapter 注册为 Spring Bean（`@Component`）按需注入。

### L5. DatasetMappingLoader — 加载失败时 loaded=true，后续不会重试

- **文件**: `main/backend/src/main/java/com/homework/asset/ingest/mapping/DatasetMappingLoader.java`
- **行号**: 54-56
- **严重程度**: **低**
- **描述**: `catch (IOException e) { log.error(...); loaded = true; }` — 如果配置文件暂时不可达（如网络文件系统抖动），首次加载失败后 `loaded=true` 导致后续永远不重试，只能重启应用。
- **修复建议**: 加载失败时保持 `loaded = false`，或增加重试机制（如 `@Retryable`）。

### L6. GlobalExceptionHandler — NoResourceFoundException 忽略非 favicon/robots 路径

- **文件**: `main/backend/src/main/java/com/homework/asset/api/exception/GlobalExceptionHandler.java`
- **行号**: 50-59
- **严重程度**: **低**
- **描述**: favicon/robots.txt 请求返回空 404 body，其他路径返回带 message 的 404。但判断条件用的是 `path.contains("favicon")` — 如果有人请求 `/api/v1/assets/favicon-special`，也会被静默处理。虽然概率极低，但 contains 匹配不够精确。
- **修复建议**: 改用 `path.endsWith("/favicon.ico")` 和 `path.endsWith("/robots.txt")` 精确匹配。

### L7. RateLimitFilter — 限流 key 使用明文 API Key

- **文件**: `main/backend/src/main/java/com/homework/asset/config/RateLimitFilter.java`
- **行号**: 136-141
- **严重程度**: **低**
- **描述**: 限流 key 格式为 `"apikey:" + apiKey`。ConcurrentHashMap 的 key 直接包含明文 API Key，如果 heap dump 被获取，API Key 会泄漏。
- **修复建议**: 对 API Key 做哈希后作为限流 key：`"apikey:" + sha256(apiKey)`。

### L8. AssetMetrics — recordXxxRequest 接收毫秒但 Timer 按秒命名

- **文件**: `main/backend/src/main/java/com/homework/asset/config/AssetMetrics.java`
- **行号**: 144-171
- **严重程度**: **低**
- **描述**: `recordListRequest(long durationMs)` 接收毫秒值，调用 `timer.record(durationMs, TimeUnit.MILLISECONDS)`。Timer 名称是 `asset_api_duration_seconds`，但 `TimeUnit.MILLISECONDS` 告诉 Micrometer 这是毫秒并自动转换。命名与单位不一致容易让阅读 Prometheus 指标的人误解。
- **修复建议**: 要么改 Timer 名称为 `asset_api_duration`（不带 _seconds 后缀），要么传参改为秒。Micrometer 会自动追加 `_seconds` 后缀。

### L9. application.yml — `baseline-on-migrate: false` 对新数据库安全，对已有数据库危险

- **文件**: `main/backend/src/main/resources/application.yml`
- **行号**: 29
- **严重程度**: **低**
- **描述**: Flyway `baseline-on-migrate: false` 意味着如果数据库已有表，Flyway 启动会报错。在开发环境重建数据库时没问题，但在需要保留数据的测试/生产环境首次部署 Flyway 时会失败。
- **修复建议**: 按 profile 区分：dev 设为 false，test/prod 设为 true 并设置 `baseline-version: 1`。

### L10. AssetCommandService.deleteByQuery — 允许空过滤条件时抛出 400

- **文件**: `main/backend/src/main/java/com/homework/asset/service/AssetCommandService.java`
- **行号**: 188-190
- **严重程度**: **低**
- **描述**: 当 status/uploader/sourceDataset 全部为 null 时抛出 400 错误。这是合理的防御性设计，但 Controller 层的 `deleteByQuery` 方法（AssetCommandController.java:127-148）三个参数都标记为 `required=false`，API 文档没有说明至少需要一个条件。
- **修复建议**: 在 OpenAPI `@Operation` 描述中添加 "至少提供一个过滤条件"；或在 Controller 层做前置校验。

### L11. IngestAuditService — `toJson` 抛出 500 而非专有异常

- **文件**: `main/backend/src/main/java/com/homework/asset/ingest/IngestAuditService.java`
- **行号**: 107-113
- **严重程度**: **低**
- **描述**: `toJson` 方法在 JSON 序列化失败时抛出 `ApiException(500, ...)`。序列化 `Map<String, Object>` 或 `List<Map>` 正常不会失败，但如果 raw data 包含循环引用或特殊 Java 对象（如 InputStream），会抛出难以排查的 500。
- **修复建议**: 改为 `catch (JsonProcessingException e) { throw new ApiException(500, "Failed to serialize audit payload: " + e.getMessage()); }` 暴露部分原因；或对 rawRecord 做白名单过滤（只保留 Map/String/Number/Boolean/List 类型）。

### L12. Flyway V1 — 未设置 `fillfactor` 或 `autovacuum` 调优

- **文件**: `main/backend/src/main/resources/db/migration/V1__init_assets.sql`
- **行号**: 68-85
- **严重程度**: **低**
- **描述**: `assets` 表频繁 `ON CONFLICT DO UPDATE`，每次 UPDATE 产生死元组。未配置 `fillfactor`（默认为 100，即页完全填充），UPDATE 会频繁触发页分裂。同时未调优 `autovacuum` 参数。
- **修复建议**: `ALTER TABLE assets SET (fillfactor = 80, autovacuum_vacuum_scale_factor = 0.05);` 减轻 HOT 更新压力。

### L13. MyBatisPlusConfig — UUID TypeHandler 通过 `@Bean` 匿名类注册，无法单元测试

- **文件**: `main/backend/src/main/java/com/homework/asset/config/MyBatisPlusConfig.java`
- **行号**: 45-104
- **严重程度**: **低**
- **描述**: UUID TypeHandler 以匿名内部类形式内联在 `configurationCustomizer()` Bean 中。此 TypeHandler 无法被单独单元测试。对比 `PgStringArrayTypeHandler` 有独立类文件和单元测试，一致性不好。
- **修复建议**: 提取为独立类 `UuidTypeHandler extends BaseTypeHandler<UUID>`，与 `PgStringArrayTypeHandler` 保持一致的代码组织。

---

## 额外观察 (非问题，但值得关注)

以下不是 Bug，但作为架构师视角的补充建议：

1. **测试覆盖率不均衡**: `EtlNormalizersTest` 有 38 个测试用例覆盖边界值很完善，但 `AssetCommandService`（核心写入逻辑）没有任何单元测试，只有集成测试 `AssetControllerIT` 覆盖。建议补充 `AssetCommandServiceTest` 用 Mock 验证 Adapter 选择、异常处理等逻辑。

2. **Cursor 分页排序固定不可配置**: `/api/v1/assets/cursor` 端点排序固定为 `uploaded_at DESC, id DESC`。未来如果有按 `file_size_bytes` 排序的 cursor 分页需求，需要新增索引和重写 XML。建议设计文档中说明当前约束。

3. **DynamicDatasetAdapter 未被生产路径使用**: `DynamicDatasetAdapter` + `DatasetMappingLoader` 提供了配置驱动的适配器方案，但 `AssetCommandService` 的 `importFromExcel` 仍然使用硬编码的 `detectAdapter` 逻辑。两套方案并存增加了维护负担。

4. **缺少请求日志**: 没有请求级别的日志记录（如 Filter 记录 method + path + status + duration）。`AssetMetrics` 只记录 Prometheus 指标，不在日志中体现。建议添加 `CommonsRequestLoggingFilter` 或自定义 Filter。

5. **Flyway migration 基线**: V1 和 V2 都是全新创建表。如果未来存在对已有表结构的修改（如 `ALTER TABLE`），需要确保 Flyway 版本号严格递增，且每个迁移文件幂等或仅执行一次。

---

## 修复优先级建议

| 优先级 | 问题编号 | 理由 |
|--------|----------|------|
| P0 (本周) | C1 | 批量导入性能问题直接影响核心功能 |
| P1 (本迭代) | H1, H2, H3 | 安全 + 数据可靠性 |
| P2 (下迭代) | M1-M11 | 技术债务清理 |
| P3 (backlog) | L1-L13 | 代码优化和一致性改进 |

---

*报告结束 — 由 Claude Code 架构师模式生成*
