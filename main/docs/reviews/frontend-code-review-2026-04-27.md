# 前端代码审查报告

**审查日期**: 2026-04-27
**审查范围**: `main/frontend/src/` 下所有 Vue/TypeScript 代码 (17 个源文件)
**严重程度定义**:
- **严重 (Critical)**: 运行时 bug、功能失效、数据错误
- **高 (High)**: 显著性能问题、安全漏洞、潜在契约断裂
- **中 (Medium)**: 代码异味、可维护性问题、边缘情况处理不足
- **低 (Low)**: 风格问题、冗余代码、微小优化

---

## 问题汇总

| 文件 | 严重 | 高 | 中 | 低 | 合计 |
|------|------|----|----|-----|------|
| services/monitoringService.ts | 3 | 0 | 2 | 1 | 6 |
| pages/Monitoring.vue | 1 | 1 | 3 | 2 | 7 |
| main.ts | 0 | 2 | 0 | 1 | 3 |
| services/assetService.ts | 0 | 1 | 1 | 1 | 3 |
| pages/Dashboard.vue | 0 | 0 | 3 | 3 | 6 |
| pages/AssetList.vue | 0 | 0 | 1 | 1 | 2 |
| pages/AssetDetail.vue | 0 | 0 | 0 | 1 | 1 |
| stores/assetStore.ts | 0 | 0 | 0 | 2 | 2 |
| utils/queryBuilder.ts | 0 | 0 | 1 | 1 | 2 |
| components/FilterBar.vue | 0 | 0 | 0 | 0 | 0 |
| components/SortControl.vue | 0 | 0 | 0 | 0 | 0 |
| components/FieldSelector.vue | 0 | 0 | 0 | 1 | 1 |
| router/index.ts | 0 | 0 | 0 | 1 | 1 |
| types/asset.ts | 0 | 0 | 0 | 0 | 0 |
| utils/formatters.ts | 0 | 0 | 0 | 0 | 0 |
| App.vue | 0 | 0 | 0 | 0 | 0 |
| vite.config.ts | 0 | 0 | 0 | 1 | 1 |
| package.json | 0 | 0 | 0 | 1 | 1 |
| **合计** | **4** | **4** | **11** | **18** | **37** |

---

## 严重问题 (Critical)

### C1: Prometheus Summary 分位数数据完全错误

- **文件**: `main/frontend/src/services/monitoringService.ts`
- **行号**: 134-161
- **严重程度**: **严重 (Critical)**
- **描述**:

  `extractBusinessMetrics` 函数在处理 `asset_api_duration_seconds` Summary 指标时，仅提取了 `_sum` 和 `_count` 后缀的样本，计算平均值后手工构造 P95 / P99 值为 `avg * 1.5` 和 `avg * 2`。完全忽略了 Prometheus Summary 类型实际暴露的分位数数据。

  Prometheus Summary 指标的真实结构:
  ```
  asset_api_duration_seconds{endpoint="list",quantile="0.5"} 0.015
  asset_api_duration_seconds{endpoint="list",quantile="0.95"} 0.089
  asset_api_duration_seconds{endpoint="list",quantile="0.99"} 0.135
  asset_api_duration_seconds_sum{endpoint="list"} 4.2
  asset_api_duration_seconds_count{endpoint="list"} 320
  ```

  代码只匹配了 `_sum` 和 `_count`，但真正的 `quantile="0.5"` / `"0.95"` / `"0.99"` 样本因为精确匹配 `asset_api_duration_seconds_sum` / `_count` 而被忽略。

  **影响**: Monitoring 页面展示的 P50/P95/P99 延迟数据全部为手工人造的虚假数据，不是真实分位数。人工倍数估算 (x1.5, x2) 与实际分布可能相差 10 倍以上，运维人员据此无法真正识别性能瓶颈。

- **修复建议**:

  改为匹配带有 `quantile` label 的 `asset_api_duration_seconds` 指标（不含 `_sum`/`_count` 后缀），按 `endpoint` + `quantile` 分组提取真实的 P50/P95/P99 值。

---

### C2: API 延迟图表数据渲染为字符串而非数字

- **文件**: `main/frontend/src/pages/Monitoring.vue`
- **行号**: 1213, 1219, 1225
- **严重程度**: **严重 (Critical)**
- **描述**:

  `renderLatencyChart` 函数中，bar series 的 data 使用了 `.toFixed(2)`:
  ```typescript
  data: data.map(l => l.p50.toFixed(2)),   // 返回 "0.00" 字符串
  data: data.map(l => l.p95.toFixed(2)),   // 返回 "0.00" 字符串
  data: data.map(l => l.p99.toFixed(2)),   // 返回 "0.00" 字符串
  ```
  `Number.prototype.toFixed()` 返回 `string` 类型，导致 ECharts 接收到的 bar 数据值是字符串而非数字。ECharts 在渲染时可能将其解析为 0 或产生异常的轴刻度。

- **修复建议**:

  使用 `parseFloat(l.p50.toFixed(2))` 确保数据为数字类型，或将 `.toFixed(2)` 移到 label formatter 中保留精度而不改变数据类型。

---

### C3: 慢查询计数仅取第一个匹配

- **文件**: `main/frontend/src/services/monitoringService.ts`
- **行号**: 165-166
- **严重程度**: **严重 (Critical)**
- **描述**:

  ```typescript
  const slowQueryMetric = metrics.find(m => m.name === 'db_slow_queries_total')
  const slowQueries = slowQueryMetric ? slowQueryMetric.value : 0
  ```

  使用 `Array.find()` 只返回第一个匹配项。如果 `db_slow_queries_total` 指标有多个样本（例如按不同标签分组），只有第一个被计数，其余被静默丢弃。

- **修复建议**:

  使用 `metrics.filter().reduce()` 累加所有匹配样本的值，或在确认指标不含维度标签时添加注释说明。

---

### C4: Prometheus NaN / +Inf / -Inf 值未被处理

- **文件**: `main/frontend/src/services/monitoringService.ts`
- **行号**: 40
- **严重程度**: **严重 (Critical)**
- **描述**:

  `parsePrometheusText` 函数中对 values 使用 `parseFloat(match[3])`。Prometheus 指标可能出现特殊浮点值:
  - `NaN` → `parseFloat("NaN")` = `NaN`
  - `+Inf` → `parseFloat("+Inf")` = `NaN` (parseFloat 无法解析 Infinity)
  - `-Inf` → `parseFloat("-Inf")` = `NaN`

  `NaN` 值进入下游计算后:
  - `avgLatency` computed 产生 `NaN`
  - `memoryUsagePercent` computed 产生 `NaN`
  - 告警条件 `NaN >= 80` → `false`（告警静默失效）
  - ECharts 接收 NaN 可能导致图表崩溃

- **修复建议**:

  在 `parsePrometheusText` 中对 values 做规范化: 识别 `NaN` / `+Inf` / `-Inf` 字符串，分别转换为 `Number.NaN`、`Infinity`、`-Infinity`。在 `extractBusinessMetrics` 中使用 `Number.isFinite()` 过滤非有限值。

---

## 高优先级问题 (High)

### H1: main.ts 全量加载 ECharts（每次页面均加载 ~1MB）

- **文件**: `main/frontend/src/main.ts`
- **行号**: 13, 19-22
- **严重程度**: **高 (High)**
- **描述**:

  `import * as echarts from 'echarts'` 在主入口文件顶层导入完整的 ECharts 库（包含所有图表类型），并挂载到 `window.echarts`。ECharts 全量库 gzip 后约 300KB+，解压后约 1MB。这导致:
  1. 所有页面（包括不使用图表的 AssetList）都需下载和解压 ECharts
  2. 首屏加载时间显著增加
  3. 无法利用 tree-shaking 或按需加载

  项目 `package.json` 已经安装了 `vue-echarts`（行 21），但完全未使用。

- **修复建议**:

  方案 A: 仅在 Dashboard 和 Monitoring 页面 `onMounted` 中动态 `import('echarts')`。
  方案 B: 使用 `vue-echarts` 组件替代手写 ECharts 集成，利用 Vite 的代码分割自动按需加载。

---

### H2: main.ts 全量注册 Element Plus 图标（数百个图标）

- **文件**: `main/frontend/src/main.ts`
- **行号**: 10, 28-30
- **严重程度**: **高 (High)**
- **描述**:

  ```typescript
  import * as ElementPlusIconsVue from '@element-plus/icons-vue'
  // ...
  for (const [key, component] of Object.entries(ElementPlusIconsVue)) {
    app.component(key, component)
  }
  ```

  `@element-plus/icons-vue` 包含数百个 SVG 图标组件。全量注册为全局组件导致:
  1. 首屏 JS 包体积膨胀
  2. 每个图标作为 Vue 组件注册（即使只用到了 10-15 个）
  3. 与 Vite tree-shaking 冲突（`import *` 阻止了摇树优化）

- **修复建议**:

  按需导入实际使用的图标:
  ```typescript
  import { VideoCamera, DataAnalysis, Film, Monitor, ArrowLeft,
           Upload, Search, Setting, SortUp, SortDown, Refresh,
           UploadFilled, QuestionFilled, InfoFilled, CircleCheck,
           Document, Histogram, Link, Bell, ArrowRight, TrendCharts,
           Timer, Warning, Cpu, Loading } from '@element-plus/icons-vue'
  ```
  仅注册以上 24 个实际使用的图标。

---

### H3: 统计 API 响应字段契约潜在断裂

- **文件**: `main/frontend/src/services/assetService.ts`
- **行号**: 122
- **严重程度**: **高 (High)**
- **描述**:

  `fetchUploaderAvgSize` 函数（行 122）注释明确指出:
  ```
  // 后端返回 List<Map<String,Object>>，字段名是 snake_case
  ```
  但 TypeScript 类型 `UploaderAvgSize` 全部使用 camelCase:
  ```typescript
  interface UploaderAvgSize {
    uploader: string      // 若后端返回 "uploader" (无下划线) 无问题
    avgSizeBytes: number   // 若后端返回 avg_size_bytes → 契约断裂
    avgSizeHuman: string   // 若后端返回 avg_size_human → 契约断裂
    approvedCount?: number // 若后端返回 approved_count → 契约断裂
  }
  ```

  根据项目协作规范 (`.claude/rules/collaboration.md`): "禁止的补丁写法：`row.fieldName ?? row.field_name`"。目前前端未使用 fallback 写法，但若后端未做 camelCase 转换，`fetchUploaderAvgSize`、`fetchTopTags`、`fetchPlatformApproval` 三个接口返回的 snake_case key 将导致数据静默丢失（TypeScript 不校验运行时对象 key）。

  Dashboard.vue 中使用了 `d.avgSizeBytes`、`d.uploader`、`d.approvalRate` 等 camelCase 字段访问。如果后端返回 snake_case，图表数据将全部为空。

  **注意**: 需要确认后端是否已通过 SQL alias (`AS "avgSizeBytes"`) 或 Jackson 序列化做了 camelCase 转换。若后端使用 `Map<String,Object>` 直接返回且 SQL alias 为 snake_case，则此问题为真实契约断裂。

- **修复建议**:

  1. 验证后端统计接口 (`/stats/uploader-avg-size`, `/stats/top-tags`, `/stats/platform-approval`) 的实际 JSON 响应字段名
  2. 若为 snake_case，后端应修改 SQL alias 为 camelCase（如 `AS "avgSizeBytes"`）
  3. 同步更新注释，移除误导性说明

---

### H4: Actuator 请求绕过认证体系

- **文件**: `main/frontend/src/services/monitoringService.ts` (行 62, 75) 和 `main/frontend/src/pages/Monitoring.vue` (行 959)
- **行号**: monitoringService.ts:62,75; Monitoring.vue:959
- **严重程度**: **高 (High)**
- **描述**:

  Monitoring 相关功能使用两种方式绕过项目的 Axios 实例 (`http`):

  1. `monitoringService.ts` 使用原始 `axios` 直接访问 `/actuator/prometheus`：
     ```typescript
     const response = await axios.get('/actuator/prometheus', { ... })  // 无 X-API-Key
     ```

  2. `Monitoring.vue` 使用原始 `fetch` API 访问 `/actuator/metrics`：
     ```typescript
     const response = await fetch('/actuator/metrics')  // 无 X-API-Key, 无响应拦截
     ```

  相比之下，`assetService.ts` 使用配置了 X-API-Key 的 `http` 实例（行 37-42）。如果后端 Actuator 端点后续启用了认证保护，Monitoring 页面将完全不可用，且无错误提示（fetch 的错误不会被 `ApiError` 包装）。

- **修复建议**:

  统一使用 `assetService.ts` 中的 `http` 实例访问所有后端端点。如果 Actuator 端点不需要认证，使用 `http` 实例也无害（额外的 X-API-Key header 不影响未保护端点）。

---

## 中优先级问题 (Medium)

### M1: Monitoring 页面轮询无并发保护

- **文件**: `main/frontend/src/pages/Monitoring.vue`
- **行号**: 1311-1317
- **描述**:

  自动刷新使用 `setInterval(refreshMetrics, 5000)` 每 5 秒触发一次。如果某次请求耗时超过 5 秒（网络慢或后端慢），下一次 interval 仍会触发，导致多个并行请求堆积。

- **修复建议**:

  改用 `setTimeout` 递归模式，在 `refreshMetrics` 完成后才安排下一次:
  ```typescript
  function scheduleNextRefresh() {
    refreshTimer = setTimeout(async () => {
      await refreshMetrics()
      if (autoRefresh.value) scheduleNextRefresh()
    }, 5000)
  }
  ```

---

### M2: ECharts 实例每次刷新都 dispose+recreate，存在视觉闪烁

- **文件**: `main/frontend/src/pages/Monitoring.vue`
- **行号**: 1084-1086, 1130-1132, 1176-1178, 1236-1238
- **描述**:

  四个渲染函数 (`renderRequestPieChart`, `renderRequestBarChart`, `renderLatencyChart`, `renderConnectionChart`) 在每次刷新时无条件 `dispose()` 旧实例并 `init()` 新实例。实际上 ECharts 实例创建后可通过 `setOption` 更新数据，无需销毁重建。

  dispose+recreate 导致:
  1. 不必要的 DOM 操作，产生视觉闪烁
  2. 旧实例的事件监听器泄漏（若有）
  3. 额外的 GC 压力

  Dashboard.vue 的渲染函数也采用同样模式 (行 135-136, 188-189, 262-263)。

- **文件**: `main/frontend/src/pages/Dashboard.vue`
- **行号**: 135-136, 188-189, 262-263
- **描述**: 同上问题。

- **修复建议**:

  首次调用时 `init()`，后续调用仅使用 `setOption(option, { notMerge: true })` 更新。仅在组件卸载时 `dispose()`。

---

### M3: ECharts 初始化使用 setTimeout 延迟（疑似 workaround）

- **文件**: `main/frontend/src/pages/Monitoring.vue`
- **行号**: 1321-1323
- **描述**:

  ```typescript
  onMounted(() => {
    setTimeout(() => {
      refreshMetrics()
    }, 100)
  })
  ```

  使用 `setTimeout(100ms)` 硬编码延迟来触发初始数据加载。注释未解释原因，疑似是对时序问题（DOM 未完全渲染 / ECharts 容器未就绪）的 workaround。

- **修复建议**:

  使用 `nextTick()` + DOM 可用性检查（容器元素存在判断）替代硬编码 setTimeout。或在 `refreshMetrics` 内部对容器不存在的情况做防御。

---

### M4: filteredRawMetrics 过滤算法缺陷

- **文件**: `main/frontend/src/pages/Monitoring.vue`
- **行号**: 767-789
- **描述**:

  `filteredRawMetrics` computed 的过滤逻辑: 当数据行不匹配时删除之前添加的注释行。但 Prometheus 每个指标可能有 3 行注释（`# HELP`、`# TYPE`、指标行），只删除最后一个被添加的注释行会导致部分注释残留。

- **修复建议**:

  采用两阶段处理: 先按空行分组，再判断每组是否包含匹配项，整组保留或丢弃。

---

### M5: Dashboard.vue 加载状态与图表渲染之间存在空白窗口

- **文件**: `main/frontend/src/pages/Dashboard.vue`
- **行号**: 147-148
- **描述**:

  ```typescript
  q1Loading.value = false    // 关闭加载动画
  await nextTick()            // 等待 DOM 更新
  renderQ1Chart(sorted)       // 渲染图表
  ```

  在第 147 行和 149 行之间，loading skeleton 已消失，但图表容器还未渲染（因为需要在 `nextTick` 之后才执行 `renderQ1Chart`）。用户看到短暂的空卡片状态。Q2/Q3 同理 (行 195-197, 269-271)。

- **修复建议**:

  `q1Loading.value = false` 移到 `renderQ1Chart()` 之后，先渲染图表再关闭 loading。

---

### M6: assetStore 中写入/删除失败后状态不一致

- **文件**: `main/frontend/src/stores/assetStore.ts`
- **行号**: 179-194, 200-212, 219-230
- **描述**:

  `uploadExcel` (行 184): 上传成功后调用 `fetchAssets()` 刷新列表。但如果 `fetchAssets()` 失败（网络在两次请求间出现故障），列表变为空 (`assets.value = []`, `total.value = 0`)，但上传实际已成功。
  `deleteAsset` (行 204): 同样问题 — 删除成功后若刷新列表失败，列表清空。

  另外，`deleteBatch` (行 219) 使用同一 `deleteLoading.value`，无法区分单个删除和批量删除的进度。

- **修复建议**:

  操作成功后使用乐观更新（本地移除/添加数据），而非全量重新拉取。或至少 `fetchAssets` 失败时不清空现有数据。

---

### M7: queryBuilder 默认字段与 Store 默认字段不一致

- **文件**: `main/frontend/src/utils/queryBuilder.ts` (行 133) vs `main/frontend/src/stores/assetStore.ts` (行 72-81)
- **描述**:

  `queryBuilder.ts` 的 `createDefaultQueryState()` 返回 `fields: []`，表示"返回所有字段"。但 `assetStore.ts` 中 `selectedFields` 默认值为 8 个特定字段。这意味着 store 在构建查询参数时会用 `selectedFields` 覆盖 `queryState.fields`，但 `queryState.fields` 的初始值 `[]` 从未被使用。代码阅读者容易产生困惑。

- **修复建议**:

  统一默认字段集合的来源。将 `selectedFields` 的默认值合并到 `createDefaultQueryState()` 中，或删除 `queryState.fields` 字段（因为 store 总是用 `selectedFields` 覆盖它）。

---

### M8: 无请求取消机制

- **文件**: `main/frontend/src/stores/assetStore.ts` (行 89-111) 和 `main/frontend/src/pages/Monitoring.vue` (行 1292-1305)
- **描述**:

  用户快速切换页面时，进行中的 API 请求未被取消。这会导致:
  1. 已卸载组件的状态更新警告（"Can't perform a React state update..." 的 Vue 等价警告）
  2. 无效的网络带宽消耗
  3. 潜在的数据竞态条件（旧请求的响应覆盖新请求的结果）

  例如，快速切换 AssetDetail 不同资产时，前一个 `fetchAssetById` 的响应可能在新的请求之后到达，导致详情页显示错误的资产数据。

- **修复建议**:

  使用 AbortController 或 Axios CancelToken 取消进行中的请求。在 store action 中保存当前请求的 AbortController，新请求到达时取消旧的。

---

### M9: Dashboard.vue 三个 ref 变量声明但从未使用

- **文件**: `main/frontend/src/pages/Dashboard.vue`
- **行号**: 126, 180, 254
- **描述**:

  定义了 `q1ChartRef`、`q2ChartRef`、`q3ChartRef` 三个模板 ref，但在代码中全部使用 `document.querySelector('#q1-chart')` 方式获取 DOM 元素，ref 变量从未赋值或读取。这是死代码，表明从 ref 方案迁移到 querySelector 方案时遗留。

- **修复建议**:

  删除三个未使用的 ref 声明，或者改为使用 `ref` 方式（需要调整模板中 `id="q1-chart"` 为 `ref="q1ChartRef"`）。

---

### M10: Store 查询状态字段隐式依赖

- **文件**: `main/frontend/src/stores/assetStore.ts`
- **行号**: 92-97
- **描述**:

  ```typescript
  const stateWithFields = {
    ...queryState,
    fields: selectedFields.value,
  }
  const params = buildQueryParams(stateWithFields)
  ```

  `queryState` 是 `reactive` 对象，但通过展开运算符 `...queryState` 传递给 `buildQueryParams` 时，`queryState.fields` 被就地覆盖。这种隐式的字段覆盖是脆弱的 — 如果将来 `buildQueryParams` 使用 `state.fields` 前做了其他处理，可能忽略这个覆盖。

- **修复建议**:

  在 `queryState` 中不要包含 `fields` 属性，或显式构造参数对象而非使用展开覆盖。

---

### M11: main.ts 缺少全局错误处理

- **文件**: `main/frontend/src/main.ts`
- **行号**: 38
- **描述**:

  未注册 `app.config.errorHandler`。Vue 3 中未被捕获的组件错误（如同步渲染中的异常）可能导致白屏。在生产环境中，这些错误不会被上报，也不会有任何用户提示。

- **修复建议**:

  添加全局错误处理器:
  ```typescript
  app.config.errorHandler = (err, instance, info) => {
    console.error('Vue Error:', err, info)
    // 可接入错误上报服务 (Sentry 等)
  }
  ```

---

## 低优先级问题 (Low)

### L1: AssetDetail.vue 默认 asset 对象类型不够安全

- **文件**: `main/frontend/src/pages/AssetDetail.vue`
- **行号**: 213-215
- **描述**:
  ```typescript
  const asset = computed<AssetSparse>(() => {
    return store.currentAsset ?? { id: '' }
  })
  ```
  当 `currentAsset` 为 null 时返回 `{ id: '' }`，此时 `asset.title` 等字段为 undefined。模板中虽有 `v-if="asset.status"` / `{{ asset.title || '未命名素材' }}` 等防御，但类型标注 `AssetSparse` 暗示完整类型，实际可能缺失字段。可改为返回更明确的类型或使用 computed guard。

- **修复建议**: 保持现状或抽取为独立的 computed `hasAsset` 和类型缩窄。

---

### L2: formatJson 可处理循环引用但无提示

- **文件**: `main/frontend/src/pages/AssetDetail.vue`
- **行号**: 230-236
- **描述**:
  ```typescript
  function formatJson(obj: unknown): string {
    try { return JSON.stringify(obj, null, 2) }
    catch { return String(obj) }
  }
  ```
  当 `JSON.stringify` 因循环引用失败时，静默降级为 `String(obj)`，结果通常为 `[object Object]`，对用户无意义。

- **修复建议**: catch 中返回显式提示如 `"(无法序列化: 可能包含循环引用)"`。

---

### L3: vite.config.ts 中冗余的 rewrite 配置

- **文件**: `main/frontend/vite.config.ts`
- **行号**: 19
- **描述**: `rewrite: (path) => path` 是恒等函数，与不写 rewrite 效果相同。无实际作用。

- **修复建议**: 删除 `rewrite` 配置。

---

### L4: package.json 中未使用的依赖 vue-echarts

- **文件**: `main/frontend/package.json`
- **行号**: 21
- **描述**: `"vue-echarts": "^6.7.1"` 已安装但项目中无任何文件导入此包。所有 ECharts 图表均通过手写 `window.echarts` API 渲染。

- **修复建议**: 移除依赖，或迁移到 vue-echarts 组件化集成。

---

### L5: router 缺少滚动行为配置细节

- **文件**: `main/frontend/src/router/index.ts`
- **行号**: 47-49
- **描述**: `scrollBehavior` 总是返回 `{ top: 0 }`，即每次路由切换都强制滚动到顶部。浏览器原生的前进/后退保留滚动位置的行为被覆盖。

- **修复建议**: 使用 `savedPosition`:
  ```typescript
  scrollBehavior(to, from, savedPosition) {
    return savedPosition || { top: 0 }
  }
  ```

---

### L6: Monitoring.vue 中 parseFloat + toFixed 来回转换

- **文件**: `main/frontend/src/pages/Monitoring.vue`
- **行号**: 375
- **描述**: `:percentage="parseFloat(memoryUsagePercent.toFixed(2))"` — 先将 number 格式化为 2 位小数字符串，再 parseFloat 转回 number。净效果等同于四舍五入到 2 位小数，但产生不必要的 string/number 来回转换。

- **修复建议**: 使用 `Math.round(memoryUsagePercent.value * 100) / 100`。

---

### L7: FilterBar 初始化 watch 可能触发额外一次 sync

- **文件**: `main/frontend/src/components/FilterBar.vue`
- **行号**: 108-113
- **描述**: `watch` 立即同步 `props.modelValue` 到 `localFilters`。当组件创建时，若 modelValue 已被 AssetList 的 `restoreFromQuery` 更新过，watch 会触发一次 `Object.assign`，即使值相同。Vue 的 deep watch 会比较值并跳过相同值，但 `Object.assign` 本身已执行。

- **修复建议**: 使用 `immediate: true` 确保首次同步明确，或在 watch 回调中比较新旧值。

---

### L8: FieldSelector 中 modelValue 变化 watch 仅浅拷贝

- **文件**: `main/frontend/src/components/FieldSelector.vue`
- **行号**: 79-83
- **描述**: `localSelected.value = [...val]` — 当 `val` 引用未变但内容变化时（数组 push/pop），watch 可能不触发。但在本项目中 `val` 总是通过 emit 新数组，所以实际上不会触发此问题。

- **修复建议**: 添加 `{ deep: true }` 选项以处理直接修改场景，或保持现状并注释说明调用约定。

---

### L9: fetchUploaderAvgSize 缺少 limit 参数控制

- **文件**: `main/frontend/src/services/assetService.ts`
- **行号**: 121-125
- **描述**: Dashboard Q1 展示"Top 10"，但 API 调用 `fetchUploaderAvgSize()` 无 limit 参数，由前端对全量数据做 `.slice(0, 10)`。如果上传人数量巨大（> 1000），网络传输和前端排序的开销不必要。

- **修复建议**: 添加 `limit` 参数传递给后端，由 SQL `LIMIT` 控制返回量。

---

### L10: createDefaultQueryState 硬编码默认 pageSize 20

- **文件**: `main/frontend/src/utils/queryBuilder.ts`
- **行号**: 133
- **描述**: 默认 pageSize 为 20，而 `assetStore.ts` 的 `selectedFields` 默认也与 queryBuilder 独立。若有多个调用点使用默认值，修改需同步。

- **修复建议**: 提取为常量 `DEFAULT_PAGE_SIZE = 20`。

---

### L11: Dashboard.vue 缺少 onUnmounted 中取消 resize 监听时的空值检查

- **文件**: `main/frontend/src/pages/Dashboard.vue`
- **行号**: 379-383
- **描述**: `onUnmounted` 中直接调用 `q1Chart?.dispose()`，如果 `q1Chart` 因异常未初始化，空值保护有效。但 `window.removeEventListener` 在 `onMounted` 中已注册 `handleResize`，卸载时移除。这是正确的。

- **修复建议**: 无需修改，确认当前实现正确。仅标记为已验证。

---

### L12: Dashboard.vue 错误状态下的 Loading 标志未关闭

- **文件**: `main/frontend/src/pages/Dashboard.vue`
- **行号**: 150-154
- **描述**: `catch` 中 `q1Error.value` 被设置，但 `finally` 中 `if (q1Loading.value) q1Loading.value = false` 处理了 loading 状态。如果 `renderQ1Chart` 在 try 块中抛出异常，loading 已在第 147 行设为 false，catch 设置 error 状态。这是正确行为。但第二个 finally 中的条件判断显得多余 — `q1Loading.value` 在第 147 行已被设 false，只有在成功路径且 `fetchUploaderAvgSize` 不抛异常时才到达。而 renderQ1Chart 抛异常会进入 catch，此时 q1Loading 已是 false。

- **修复建议**: 去掉 try 块中的 `q1Loading.value = false`，统一在 finally 中关闭。简化控制流。

---

### L13: store.deleteBatch 和 store.deleteAsset 共用 deleteLoading 标志

- **文件**: `main/frontend/src/stores/assetStore.ts`
- **行号**: 201, 220
- **描述**: 单个删除和批量删除共享 `deleteLoading` 标志。如果未来 UX 需要区分单个删除的行级 loading 状态（例如按钮 loading），当前结构无法支持。

- **修复建议**: 提取独立的 loading 状态，或在行级维护删除中状态。

---

### L14: 侧边栏 active menu 映射使用 route.name 字符串比较

- **文件**: `main/frontend/src/App.vue`
- **行号**: 80
- **描述**: `route.name === 'AssetDetail'` 使用硬编码字符串。如果路由 name 被修改，此处会静默失效（侧边栏高亮错误）。

- **修复建议**: 使用枚举或常量定义路由名称，避免魔术字符串。

---

### L15: AssetList.vue 删除操作 swallow 所有异常

- **文件**: `main/frontend/src/pages/AssetList.vue`
- **行号**: 517-519
- **描述**:
  ```typescript
  } catch {
    // 用户取消或错误已在 store 中处理
  }
  ```
  空 catch 块吞噬所有异常，包括 ElMessageBox 取消（正常行为）和 deleteAsset 失败（store 已用 ElMessage.error 提示）。当前逻辑正确但注释未区分两种场景。

- **修复建议**: 添加更精确的注释，说明取消（正常）和失败（已处理）两种情况。

---

### L16: AssetList.vue 列配置重复代码

- **文件**: `main/frontend/src/pages/AssetList.vue`
- **行号**: 59-212
- **描述**: 每个 `el-table-column` 都是独立的模板块，13 个列包含大量重复的 `v-if="isFieldVisible(...)"` 和格式模板。可提取为数据驱动的列配置数组，减少模板代码量。

- **修复建议**: 定义列配置数组，使用 `v-for` 渲染动态列。

---

### L17: App.vue 中的 router-view 过渡动画 name 为硬编码

- **文件**: `main/frontend/src/App.vue`
- **行号**: 58
- **描述**: `<transition name="fade" mode="out-in">` 硬编码过渡名称。如果未来要自定义过渡效果，需修改模板。

- **修复建议**: 可保持现状，当前需求简单。

---

### L18: vite.config.ts outDir 使用默认值

- **文件**: `main/frontend/vite.config.ts`
- **行号**: 28
- **描述**: `outDir: 'dist'` 是 Vite 的默认值，显式写出来增加了配置行但无实际作用。

- **修复建议**: 删除或保留作为显式文档，两种选择均可接受。

---

## 正面评价

以下方面值得肯定:

1. **类型安全**: TypeScript 类型定义完整，使用 `strict: true`，`ApiEnvelope<T>` 泛型包装使 API 响应类型安全。
2. **URL 状态持久化**: `AssetList.vue` 的 `restoreFromQuery` / `syncToQuery` 双向绑定是成熟方案，刷新不丢失筛选状态。
3. **防御性编程**: `formatFileSize`、`formatDateTime` 等格式化函数均有 null/undefined 保护。
4. **无契约断裂 fallback**: 审查未发现 `row.fieldName ?? row.field_name` 类型的 snake_case fallback 模式，符合 `.claude/rules/collaboration.md` 规范。
5. **API 层封装**: `assetService.ts` 的 Axios 拦截器统一处理 API Key 认证和业务错误码，封装良好。
6. **组件职责分明**: FilterBar / SortControl / FieldSelector 三个组件的职责边界清晰。
7. **错误状态覆盖**: Dashboard 和 Monitoring 页面都有 loading / error / empty 三种状态的 UI 覆盖。

---

## 优先修复建议

1. **立即修复** (C1, C2, C3, C4): 监控页面的数据准确性是核心价值，虚假的分位数数据和 NaN 处理缺失应立即修复。
2. **本周修复** (H1, H2): 包体积优化影响所有用户的首次加载体验。
3. **本周修复** (H3): 需与后端确认统计接口的字段名 case，避免契约断裂导致 Dashboard 数据为空。
4. **下个迭代** (M1-M11): 代码质量和鲁棒性提升。
5. **技术债** (L1-L18): 可在日常开发中逐步清理。
