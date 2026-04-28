/**
 * monitoringService.ts - Prometheus 监控数据服务
 *
 * 职责：
 * - 获取 Prometheus 指标数据
 * - 解析 Prometheus 文本格式
 * - 提供业务指标可视化数据
 */

import axios from 'axios'

// Prometheus 指标原始数据类型
export interface PrometheusMetric {
  name: string
  labels: Record<string, string>
  value: number
}

/**
 * 解析 Prometheus 文本格式
 *
 * 示例输入：
 * asset_api_requests_total{endpoint="list",method="GET"} 1523.0
 * asset_api_duration_seconds{endpoint="list",quantile="0.5"} 0.015
 */
export function parsePrometheusText(text: string): PrometheusMetric[] {
  const metrics: PrometheusMetric[] = []
  const lines = text.split('\n')

  for (const line of lines) {
    // 跳过注释和空行
    if (line.startsWith('#') || !line.trim()) continue

    // 解析格式：metric_name{labels} value
    const match = line.match(/^([a-zA-Z_:][a-zA-Z0-9_:]*)((?:\{[^}]*\})?)?\s+(.+)$/)
    if (!match) continue

    const name = match[1]
    const labelsStr = match[2] || ''
    // 处理 Prometheus 特殊浮点值：+Inf, -Inf, NaN
    let value: number
    const rawValue = match[3].trim()
    if (rawValue === '+Inf') {
      value = Infinity
    } else if (rawValue === '-Inf') {
      value = -Infinity
    } else if (rawValue === 'NaN') {
      value = Number.NaN
    } else {
      value = parseFloat(rawValue)
    }

    // 解析标签 {label1="value1",label2="value2"}
    const labels: Record<string, string> = {}
    if (labelsStr) {
      const labelMatches = labelsStr.matchAll(/([a-zA-Z_][a-zA-Z0-9_]*)="([^"]*)"/g)
      for (const lm of labelMatches) {
        labels[lm[1]] = lm[2]
      }
    }

    metrics.push({ name, labels, value })
  }

  return metrics
}

/**
 * 获取 Prometheus 指标数据
 */
export async function fetchPrometheusMetrics(): Promise<PrometheusMetric[]> {
  // 直接访问后端 Actuator 端点（通过 Vite proxy 或 Nginx proxy）
  const response = await axios.get('/actuator/prometheus', {
    responseType: 'text',
    headers: {
      'Accept': 'text/plain',
    },
  })
  return parsePrometheusText(response.data)
}

/**
 * 获取 Prometheus 原始文本数据
 */
export async function fetchPrometheusRawText(): Promise<string> {
  const response = await axios.get('/actuator/prometheus', {
    responseType: 'text',
    headers: {
      'Accept': 'text/plain',
    },
  })
  return response.data
}

/**
 * 业务指标汇总
 */
export interface BusinessMetrics {
  // API 请求统计
  apiRequests: {
    endpoint: string
    method: string
    count: number
  }[]
  // API 延迟统计（P50, P95, P99）
  apiLatency: {
    endpoint: string
    p50: number
    p95: number
    p99: number
    avg: number
  }[]
  // 慢查询统计
  slowQueries: number
  // JVM 内存使用
  jvmMemory: {
    used: number
    max: number
  }
  // 数据库连接池
  dbConnections: {
    active: number
    idle: number
    max: number
  }
}

/**
 * 提取业务指标
 */
export function extractBusinessMetrics(metrics: PrometheusMetric[]): BusinessMetrics {
  // API 请求统计
  const apiRequests: BusinessMetrics['apiRequests'] = []
  const requestMetrics = metrics.filter(m => m.name === 'asset_api_requests_total')
  for (const m of requestMetrics) {
    apiRequests.push({
      endpoint: m.labels.endpoint || 'unknown',
      method: m.labels.method || 'GET',
      count: m.value,
    })
  }

  // API 延迟统计（Summary 类型：提取真实分位数 + 计算平均值）
  const apiLatency: BusinessMetrics['apiLatency'] = []
  // Step 1: 收集 _sum / _count 用于计算 avg
  const sumCountByEndpoint = new Map<string, { sum: number; count: number }>()
  for (const m of metrics) {
    if (m.name === 'asset_api_duration_seconds_sum') {
      const ep = m.labels.endpoint || 'unknown'
      if (!sumCountByEndpoint.has(ep)) sumCountByEndpoint.set(ep, { sum: 0, count: 0 })
      sumCountByEndpoint.get(ep)!.sum = m.value
    }
    if (m.name === 'asset_api_duration_seconds_count') {
      const ep = m.labels.endpoint || 'unknown'
      if (!sumCountByEndpoint.has(ep)) sumCountByEndpoint.set(ep, { sum: 0, count: 0 })
      sumCountByEndpoint.get(ep)!.count = m.value
    }
  }

  // Step 2: 提取真实 Prometheus Summary 分位数
  // 真实格式: asset_api_duration_seconds{endpoint="list",quantile="0.95"} 0.089
  const quantileByEndpoint = new Map<string, Map<string, number>>()
  for (const m of metrics) {
    if (m.name === 'asset_api_duration_seconds' && m.labels.quantile) {
      const ep = m.labels.endpoint || 'unknown'
      if (!quantileByEndpoint.has(ep)) quantileByEndpoint.set(ep, new Map())
      quantileByEndpoint.get(ep)!.set(m.labels.quantile, m.value)
    }
  }

  // Step 3: 汇总每个 endpoint 的延迟数据
  const allEndpoints = new Set<string>()
  for (const ep of sumCountByEndpoint.keys()) allEndpoints.add(ep)
  for (const ep of quantileByEndpoint.keys()) allEndpoints.add(ep)

  for (const endpoint of allEndpoints) {
    const sc = sumCountByEndpoint.get(endpoint) || { sum: 0, count: 0 }
    const avg = sc.count > 0 ? (sc.sum / sc.count) * 1000 : 0 // 转换为毫秒

    const qm = quantileByEndpoint.get(endpoint)
    // 从真实分位数中提取 P50/P95/P99，并转换为毫秒
    const getQuantile = (q: string): number => {
      if (!qm || !qm.has(q)) return 0
      const v = qm.get(q)!
      return Number.isFinite(v) ? v * 1000 : 0
    }

    // 如果 Prometheus 没有上报该分位数，回退到用平均值估算（仅在有 sum/count 数据时）
    const hasRealQuantiles = qm && qm.size > 0
    const p50 = hasRealQuantiles ? getQuantile('0.5') : avg
    const p95 = hasRealQuantiles ? getQuantile('0.95') : (sc.count > 0 ? avg * 1.5 : 0)
    const p99 = hasRealQuantiles ? getQuantile('0.99') : (sc.count > 0 ? avg * 2 : 0)

    apiLatency.push({ endpoint, p50, p95, p99, avg })
  }

  // 慢查询统计：聚合所有 db_slow_queries_total 样本（可能按不同标签分组）
  const slowQueries = metrics
    .filter(m => m.name === 'db_slow_queries_total')
    .reduce((sum, m) => (Number.isFinite(m.value) ? sum + m.value : sum), 0)

  // JVM 内存（按 area="heap" 聚合）
  let jvmUsed = 0
  let jvmMax = 0
  for (const m of metrics) {
    if (m.name === 'jvm_memory_used_bytes' && m.labels.area === 'heap') {
      jvmUsed += m.value
    }
    if (m.name === 'jvm_memory_max_bytes' && m.labels.area === 'heap' && m.value > 0) {
      jvmMax += m.value
    }
  }

  // 数据库连接池
  const hikariTotal = metrics.find(m => m.name === 'hikaricp_connections')
  const hikariActive = metrics.find(m => m.name === 'hikaricp_connections_active')
  const hikariIdle = metrics.find(m => m.name === 'hikaricp_connections_idle')

  return {
    apiRequests,
    apiLatency,
    slowQueries,
    jvmMemory: {
      used: jvmUsed,
      max: jvmMax > 0 ? jvmMax : 1,
    },
    dbConnections: {
      active: hikariActive?.value || 0,
      idle: hikariIdle?.value || 0,
      max: hikariTotal?.value || 20,
    },
  }
}
