# 前后端架构与最佳实践审查报告

**审查时间**: 2026-05-01
**审查范围**: 后端 (Spring Boot) + 前端 (Vue 3)
**审查文件数**: 后端约 30 个 Java 文件，前端约 15 个 Vue/TS 文件

---

## 审查概览

| 维度 | 评级 | 说明 |
|------|------|------|
| **后端架构** | 优秀 | 分层清晰，职责明确 |
| **后端代码质量** | 良好 | 异常处理统一，日志规范 |
| **前端架构** | 良好 | 组件拆分合理，路由懒加载 |
| **前端代码质量** | 良好 | TypeScript 类型完整 |
| **安全性** | 优秀 | SQL 注入防护、API Key 认证 |

**发现问题统计**:
- Critical（严重）: 0
- Important（重要）: 3
- Suggestions（建议）: 12

---

## 一、后端审查 (Spring Boot)

### 1.1 分层架构 (Controller -> Service -> Mapper)

**评级**: 优秀

**架构图**:
```
api/          -> AssetController, StatsController, AssetCommandController
service/      -> AssetQueryService, AssetStatsService, AssetCommandService
mapper/       -> AssetMapper (MyBatis-Plus)
domain/       -> Asset Entity
config/       -> 配置类（Security, Metrics, MyBatis）
ingest/       -> ETL 导入服务
```

**优点**:
- Controller 专注接收请求和返回响应
- Service 层处理业务逻辑
- Mapper 层负责数据访问
- 统计查询独立 AssetStatsService，未混入 Command 服务

**建议改进**:

| 文件 | 问题 | 改进建议 |
|------|------|----------|
| service/AssetQueryService.java | 游标编解码逻辑在 Service 层 | 可提取为 CursorUtil 工具类，提高可测试性 |

**示例代码**:

```java
// 当前: AssetQueryService.java 直接处理游标编解码
private String[] decodeCursor(String cursor) {
  try {
    String decoded = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
    return decoded.split("\\|", 2);
  } catch (IllegalArgumentException e) {
    throw new ApiException(400, "Invalid cursor format");
  }
}

// 建议: 提取为工具类
public final class CursorUtil {
  private CursorUtil() {}

  public static String[] decode(String cursor) {
    // ...
  }

  public static String encode(String uploadedAt, String id) {
    // ...
  }
}
```

---

### 1.2 异常处理

**评级**: 优秀

**现状**:
- 统一使用 ApiException + GlobalExceptionHandler
- 响应格式: { code: 0, message: "ok", data: T }
- 覆盖场景: 业务异常、验证异常、文件上传超限、资源不存在、鉴权失败、通用异常

**优点**:
- 所有 Controller 抛出的异常统一格式
- 敏感错误信息不暴露给客户端（如数据库异常）
- 日志记录包含请求上下文

**建议改进**:

| 文件 | 问题 | 改进建议 |
|------|------|----------|
| GlobalExceptionHandler.java | 通用异常返回固定消息 | 可增加 includeStackTrace 环境变量控制，仅在 dev 环境返回详细堆栈 |

---

### 1.3 日志记录

**评级**: 优秀

**现状**:
- 慢查询拦截器 SlowQueryInterceptor 记录 >500ms 的查询
- GlobalExceptionHandler 记录业务异常
- 使用 SLF4J + Logback
- 日志包含: 耗时、SQL 语句、异常堆栈

**优点**:
- 慢查询有独立日志级别 (WARN) + Prometheus 指标
- SQL 日志截断到 200 字符避免日志注入

**建议改进**:

| 文件 | 问题 | 改进建议 |
|------|------|----------|
| SlowQueryInterceptor.java:87 | sql.replaceAll("\\s+", " ") 对复杂 SQL 可能不准确 | 考虑使用 MyBatis 的 BoundSql.getParameterMappings() 替代 |
| AssetController.java:66 | 手动记录耗时 | 可复用 AssetMetrics.recordListRequest() 内的计时逻辑 |

---

### 1.4 配置管理

**评级**: 良好

**现状**:
- application.yml 管理数据库、端口等配置
- ApiKeyProperties 管理 API Key 白名单
- application-prod.yml 生产配置分离

**建议改进**:

| 文件 | 问题 | 改进建议 |
|------|------|----------|
| ApiKeyProperties.java | API Key 硬编码在配置文件 | 建议使用环境变量: ${API_KEY:default-value} |
| 无 config.yaml | 配置分散 | 可考虑将 ETL 相关配置（数据集映射路径）独立为 etl-config.yaml |

---

### 1.5 API 设计

**评级**: 优秀

**现状**:
- RESTful 风格，路径清晰: /api/v1/assets, /api/v1/stats
- OpenAPI 注解完整，Swagger UI 可用
- 自研 QueryDSL 支持多字段过滤/排序/分页

**优点**:
- 分页支持 OFFSET 和 Keyset 两种模式
- 支持稀疏字段集 ?fields=title,status
- 统计接口独立 /api/v1/stats 路径

---

## 二、前端审查 (Vue 3)

### 2.1 组件结构

**评级**: 良好

**目录结构**:
```
pages/          -> Dashboard, AssetList, AssetDetail, Monitoring
components/     -> FilterBar, SortControl, FieldSelector
stores/         -> assetStore (Pinia)
services/       -> assetService, monitoringService
utils/          -> queryBuilder, formatters
types/          -> asset.ts (TypeScript 类型定义)
```

**优点**:
- 页面组件和业务组件分离
- 通用工具函数独立 (formatters, queryBuilder)
- 类型定义集中管理

**建议改进**:

| 问题 | 当前代码 | 改进建议 |
|------|----------|----------|
| 缺少通用组件目录 | components/ 混用业务和通用组件 | 创建 components/common/ 用于放置 DateRangePicker、AsyncSelect 等通用组件 |
| Monitoring.vue 单文件过大 | 1337 行 | 按功能拆分为 useAlerts.ts、useCharts.ts composables |

---

### 2.2 状态管理 (Pinia)

**评级**: 优秀

**现状**:
- assetStore.ts 集中管理素材列表、详情、查询状态
- 使用 Composition API 风格 (defineStore with arrow function)
- 状态与 UI 状态分离 (queryState vs selectedFields)

**优点**:
- 加载状态管理完善 (listLoading, detailLoading, uploadLoading)
- 错误处理统一 (handleError 方法)
- URL Query 双向绑定支持页面刷新后恢复状态

**建议改进**:

| 问题 | 改进建议 |
|------|----------|
| Store 依赖 buildQueryParams 等工具函数 | 可将工具函数内联或提取为 composables |
| handleError 方法只做 toast 展示 | 可考虑增加错误上报机制（如 Sentry） |

---

### 2.3 TypeScript 类型使用

**评级**: 优秀

**现状**:
- 所有 API 响应、Props、Emits 都有类型定义
- AssetSparse = Partial<Asset> & { id: string } 正确处理稀疏字段
- 使用 const assertion 锁定枚举数组 (ALL_ASSET_FIELDS as const)

**优点**:
- 后端 snake_case 和前端 camelCase 映射清晰
- 统计查询类型独立 (UploaderAvgSize, TopTag, PlatformApproval)

**建议改进**:

| 问题 | 当前代码 | 改进建议 |
|------|----------|----------|
| AssetDetail.vue 的 assetId 重复计算 | computed(() => String(route.params.id)) | 可使用 useRoute().params.id as string 并添加断言 |

---

### 2.4 错误处理

**评级**: 良好

**现状**:
- assetService.ts 统一拦截 HTTP 响应，code !== 0 抛出 ApiError
- 错误信息通过 ElMessage.error() 展示给用户
- 关键操作（删除、上传）有二次确认

**建议改进**:

| 问题 | 改进建议 |
|------|----------|
| 错误边界缺失 | Vue 3 无内置错误边界，建议添加 errorCaptured 钩子或 Vue Error Reporter |
| 网络超时提示不友好 | 建议区分"网络断开"和"请求超时"两种场景 |

---

### 2.5 性能优化

**评级**: 良好

**现状**:
- 路由懒加载: component: () => import('@/pages/Dashboard.vue')
- ECharts 图表动态 init + dispose
- 监控页面图表支持 resize

**建议改进**:

| 问题 | 当前代码 | 改进建议 |
|------|----------|----------|
| Monitoring.vue 创建 4 个 ECharts 实例 | 手动管理多个 chart 变量 | 可使用 Map<string, ECharts> 统一管理 |
| 大列表无虚拟滚动 | el-table 直接渲染所有数据 | 数据量 > 1000 时建议使用 el-table-v2 虚拟滚动 |

---

## 三、安全审查

### 3.1 SQL 注入防护

**评级**: 优秀

**现状**:
- 字段名白名单: QueryDslParser 解析后仅允许预定义字段
- 操作符白名单: eq/ne/gt/gte/lt/lte/in/like/has
- 返回字段白名单: fields= 参数受 parseFields() 限制
- MyBatis XML 使用 #{} 参数化，禁止 ${}

---

### 3.2 API 认证

**评级**: 优秀

**现状**:
- ApiKeyAuthFilter 校验 X-API-Key 请求头
- 白名单路径免认证 (Swagger, Actuator health)
- RateLimitFilter 限流 (10 req/s)

---

### 3.3 前端安全问题

**评级**: 良好

**现状**:
- API Key 在 assetService.ts 硬编码
- 使用 axios 统一管理请求，方便统一加签或 token 刷新

**建议改进**:

| 问题 | 改进建议 |
|------|----------|
| API Key 暴露在前端代码 | 建议使用后端代理模式，前端只访问 /api/*，后端转发到真实服务 |
| XSS 风险 (Monitoring.vue) | dangerouslyUseHTMLString: true 用于告警详情展示，建议对输入做严格过滤 |

---

## 四、最佳实践改进清单

### 4.1 Critical（必须修复）

**无 Critical 问题** - 项目安全性整体良好。

---

### 4.2 Important（重要改进）

#### IMP-1: ECharts 实例管理优化

**文件**: main/frontend/src/pages/Dashboard.vue, main/frontend/src/pages/Monitoring.vue

**问题**: 手动管理多个 ECharts 实例变量，代码重复且容易遗漏 dispose。

**当前代码**:
```typescript
let q1Chart: ECharts | null = null
let q2Chart: ECharts | null = null
let q3Chart: ECharts | null = null

// 重复的 dispose 逻辑...
```

**改进建议**:
```typescript
// 使用 Map 统一管理 ECharts 实例
const chartInstances = new Map<string, ECharts>()

function initChart(id: string, container: HTMLElement): ECharts {
  const existing = chartInstances.get(id)
  existing?.dispose()

  const chart = window.echarts.init(container)
  chartInstances.set(id, chart)
  return chart
}

function disposeAllCharts() {
  chartInstances.forEach(chart => chart.dispose())
  chartInstances.clear()
}
```

---

#### IMP-2: 监控页面组件拆分

**文件**: main/frontend/src/pages/Monitoring.vue

**问题**: 1337 行单文件，包含图表渲染、告警计算、对话框等多种职责。

**改进建议**:
```typescript
// composables/useAlerts.ts
export function useAlerts(businessMetrics: Ref<BusinessMetrics | null>) {
  // 告警规则配置和计算逻辑
}

// composables/useCharts.ts
export function useCharts() {
  // ECharts 初始化、销毁、resize 逻辑
}

// pages/Monitoring.vue (精简到约 300 行)
import { useAlerts } from '@/composables/useAlerts'
import { useCharts } from '@/composables/useCharts'
```

---

#### IMP-3: API Key 配置外置

**文件**: main/frontend/src/services/assetService.ts:37

**问题**: API Key 硬编码在源码中，暴露安全风险。

**改进建议**:
```typescript
// vite.config.ts
export default defineConfig({
  define: {
    __API_KEY__: JSON.stringify(process.env.VITE_API_KEY || 'default-key')
  }
})

// assetService.ts
const API_KEY = __API_KEY__
```

---

### 4.3 Suggestions（建议）

#### SUG-1: 后端 - 游标编解码工具化

**文件**: main/backend/src/main/java/com/homework/asset/service/AssetQueryService.java

**改进**: 将 decodeCursor 和 encodeCursor 提取为 CursorUtil 工具类。

---

#### SUG-2: 后端 - 慢查询 SQL 截断优化

**文件**: main/backend/src/main/java/com/homework/asset/config/SlowQueryInterceptor.java:87

**改进**: 使用 BoundSql.getParameterMappings() 而非简单字符串替换获取安全的 SQL 表示。

---

#### SUG-3: 前端 - 虚拟滚动支持

**文件**: main/frontend/src/pages/AssetList.vue

**改进**: 当 store.assets.length > 1000 时使用 el-table-v2 虚拟滚动。

```vue
<template v-if="store.assets.length > 1000">
  <el-table-v2
    :columns="virtualColumns"
    :data="store.assets"
    :row-height="48"
  />
</template>
<template v-else>
  <el-table :data="store.assets">...</el-table>
</template>
```

---

#### SUG-4: 前端 - 通用组件目录

**文件**: main/frontend/src/components/

**改进**: 创建 components/common/ 目录，将 FilterBar.vue 等可复用组件归入。

```
components/
├── common/          # 通用 UI 组件
│   ├── ConfirmDialog.vue
│   └── DateRangePicker.vue
├── FilterBar.vue    # 业务组件（素材过滤）
├── SortControl.vue  # 业务组件（排序控制）
└── FieldSelector.vue # 业务组件（字段选择）
```

---

#### SUG-5: 前端 - 错误上报机制

**文件**: main/frontend/src/stores/assetStore.ts

**改进**: 考虑集成 Sentry 或自建错误收集服务。

```typescript
function handleError(error: unknown, defaultMsg: string) {
  if (error instanceof ApiError) {
    ElMessage.error(`${defaultMsg}：${error.message}`)
    // 上报到错误监控系统
    captureError(error, { context: 'assetStore' })
  } else {
    ElMessage.error(defaultMsg)
    captureError(error)
  }
}
```

---

#### SUG-6: 前端 - 路由元数据类型

**文件**: main/frontend/src/router/index.ts

**改进**: 添加路由 meta 类型定义。

```typescript
interface RouteMeta {
  title: string
  requiresAuth?: boolean
}

const routes: RouteRecordRaw[] = [
  {
    path: '/assets',
    name: 'AssetList',
    component: () => import('@/pages/AssetList.vue'),
    meta: { title: '素材列表' } as RouteMeta,
  },
]

router.afterEach((to) => {
  const meta = to.meta as RouteMeta
  document.title = `${meta.title ?? '素材管理'} - 视频素材管理后台`
})
```

---

#### SUG-7: 后端 - 生产配置加密

**文件**: main/backend/src/main/resources/application-prod.yml

**改进**: 数据库密码等敏感配置使用 Jasypt 或 Vault 管理。

```yaml
spring:
  datasource:
    password: ENC(加密后的密文)
```

---

#### SUG-8: 后端 - 接口版本演进

**文件**: main/backend/src/main/java/com/homework/asset/api/AssetController.java

**改进**: 当前使用 /api/v1/ 版本前缀，未来可考虑 /api/v2/ 渐进迁移。

---

#### SUG-9: 前端 - 请求取消机制

**文件**: main/frontend/src/services/assetService.ts

**改进**: 使用 AbortController 支持请求取消，避免快速切换时的竞态。

```typescript
let currentController: AbortController | null = null

export async function listAssets(params: Record<string, string>) {
  currentController?.abort()
  currentController = new AbortController()

  const response = await http.get('/assets', {
    params,
    signal: currentController.signal
  })
  return unwrap(response)
}
```

---

#### SUG-10: 前端 - 缓存策略

**文件**: main/frontend/src/stores/assetStore.ts

**改进**: 考虑使用 pinia-plugin-persistedstate 持久化查询状态，减少重复请求。

```typescript
export const useAssetStore = defineStore('asset', () => {
  // ...
}, {
  persist: {
    key: 'asset-query-state',
    paths: ['queryState', 'selectedFields'],
  }
})
```

---

#### SUG-11: 后端 - 健康检查增强

**文件**: main/backend/src/main/java/com/homework/asset/config/AssetMetrics.java

**改进**: 增加数据库连接池探测、Redis 探测等自定义健康指标。

---

#### SUG-12: 前端 - 国际化支持

**文件**: main/frontend/src/types/asset.ts

**改进**: 将硬编码的中文 label 提取为 i18n keys。

```typescript
// 当前
export const STATUS_LABELS: Record<AssetStatus, string> = {
  pending: '待审核',
  approved: '已通过',
  rejected: '已拒绝',
}

// 改进后
import { useI18n } from 'vue-i18n'
const { t } = useI18n()

export const STATUS_LABELS: Record<AssetStatus, string> = {
  pending: () => t('status.pending'),
  approved: () => t('status.approved'),
  rejected: () => t('status.rejected'),
}
```

---

## 五、审查总结

### 5.1 整体评价

该项目架构设计合理，前后端分离清晰，具有以下亮点：

1. **后端分层明确**: Controller -> Service -> Mapper 职责清晰，异常处理统一
2. **安全防护到位**: SQL 注入三重白名单、API Key 认证、限流保护
3. **前端工程化完整**: TypeScript 类型安全、Pinia 状态管理、路由懒加载
4. **可观测性强**: Prometheus 指标、慢查询监控、监控页面完整

### 5.2 优先改进项

| 优先级 | 改进项 | 影响 |
|--------|--------|------|
| P1 | API Key 配置外置 | 安全性 |
| P1 | ECharts 实例管理优化 | 可维护性 |
| P2 | 监控页面组件拆分 | 可维护性 |
| P2 | 虚拟滚动支持 | 性能 |
| P3 | 错误上报机制 | 可观测性 |
| P3 | 请求取消机制 | 健壮性 |

### 5.3 长期规划建议

1. **微前端架构**: 当前为单页应用，未来可考虑 qiankun 微前端框架支持多团队协作
2. **服务网格**: 后端服务增多时可考虑 Istio 服务治理
3. **GraphQL**: 当前 REST API 可演进为 GraphQL 减少前后端契约摩擦

---

**审查人**: Claude Code (code-reviewer)
**审查工具**: 静态代码分析 + 最佳实践对照
