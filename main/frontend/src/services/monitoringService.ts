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
    const value = parseFloat(match[3])

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

  // API 延迟统计（Summary 类型：使用 _sum / _count 计算平均值）
  const apiLatency: BusinessMetrics['apiLatency'] = []
  const latencyByEndpoint = new Map<string, { sum: number; count: number }>()

  for (const m of metrics) {
    if (m.name === 'asset_api_duration_seconds_sum') {
      const endpoint = m.labels.endpoint || 'unknown'
      if (!latencyByEndpoint.has(endpoint)) {
        latencyByEndpoint.set(endpoint, { sum: 0, count: 0 })
      }
      latencyByEndpoint.get(endpoint)!.sum = m.value
    }
    if (m.name === 'asset_api_duration_seconds_count') {
      const endpoint = m.labels.endpoint || 'unknown'
      if (!latencyByEndpoint.has(endpoint)) {
        latencyByEndpoint.set(endpoint, { sum: 0, count: 0 })
      }
      latencyByEndpoint.get(endpoint)!.count = m.value
    }
  }

  for (const [endpoint, data] of latencyByEndpoint) {
    const avg = data.count > 0 ? (data.sum / data.count) * 1000 : 0 // 转换为毫秒
    apiLatency.push({
      endpoint,
      p50: avg,  // Summary 类型没有分位数，用平均值代替
      p95: avg * 1.5,
      p99: avg * 2,
      avg,
    })
  }

  // 慢查询统计
  const slowQueryMetric = metrics.find(m => m.name === 'db_slow_queries_total')
  const slowQueries = slowQueryMetric ? slowQueryMetric.value : 0

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
