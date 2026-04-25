<template>
  <div class="monitoring-page">
    <!-- 快捷入口 - 放到最上面 -->
    <el-card class="links-card">
      <template #header>
        <div class="card-header">
          <span>快捷入口</span>
        </div>
      </template>
      <div class="link-grid">
        <div class="link-item" @click="openUrl('/actuator/health')">
          <el-icon size="24" color="#10b981"><CircleCheck /></el-icon>
          <span>健康检查</span>
        </div>
        <div class="link-item" @click="openUrl('/actuator/info')">
          <el-icon size="24" color="#3b82f6"><InfoFilled /></el-icon>
          <span>应用信息</span>
        </div>
        <div class="link-item" @click="openUrl('/swagger-ui.html')">
          <el-icon size="24" color="#85ea2d"><Document /></el-icon>
          <span>API 文档</span>
        </div>
        <div class="link-item" @click="openUrl('/api-docs')">
          <el-icon size="24" color="#f59e0b"><Document /></el-icon>
          <span>OpenAPI 文档</span>
        </div>
        <div class="link-item" @click="openMetricsListDialog">
          <el-icon size="24" color="#8b5cf6"><Histogram /></el-icon>
          <span>指标列表</span>
        </div>
      </div>
    </el-card>

    <!-- 操作栏 -->
    <el-card class="action-bar">
      <div class="action-content">
        <div class="left">
          <el-button type="primary" @click="refreshMetrics" :loading="loading">
            <el-icon><Refresh /></el-icon>
            刷新数据
          </el-button>
          <el-switch
            v-model="autoRefresh"
            active-text="自动刷新"
            inactive-text="手动刷新"
            style="margin-left: 16px"
          />
          <span class="last-update" v-if="lastUpdate">
            上次更新: {{ lastUpdate }}
          </span>
        </div>
        <div class="right">
          <el-button @click="openUrl('/actuator/prometheus')">
            <el-icon><Link /></el-icon>
            原始数据
          </el-button>
        </div>
      </div>
    </el-card>

    <!-- 告警记录 -->
    <el-card class="alert-card">
      <template #header>
        <div class="card-header">
          <span>
            <el-icon style="margin-right: 8px"><Bell /></el-icon>
            告警记录
          </span>
          <el-badge :value="alerts.length" :max="99" class="alert-badge" />
        </div>
      </template>
      <div class="alert-list" v-if="alerts.length > 0">
        <div 
          class="alert-item" 
          v-for="alert in alerts" 
          :key="alert.id"
          @click="showAlertDetail(alert)"
        >
          <div class="alert-level">
            <el-tag 
              :type="getAlertTagType(alert.level)" 
              size="small"
              effect="dark"
            >
              {{ alert.level }}
            </el-tag>
          </div>
          <div class="alert-content">
            <div class="alert-message">{{ alert.message }}</div>
            <div class="alert-time">{{ alert.time }}</div>
          </div>
          <div class="alert-arrow">
            <el-icon><ArrowRight /></el-icon>
          </div>
        </div>
      </div>
      <el-empty v-else description="暂无告警记录" :image-size="80" />
    </el-card>

    <!-- 核心指标卡片 -->
    <el-row :gutter="20" class="metric-cards">
      <el-col :span="6">
        <el-card class="metric-card clickable" shadow="hover" @click="openRawDialog('asset_api_requests_total')">
          <div class="metric-icon requests">
            <el-icon size="28"><TrendCharts /></el-icon>
          </div>
          <div class="metric-info">
            <div class="metric-value">{{ totalRequests }}</div>
            <div class="metric-label">总请求数</div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card class="metric-card clickable" shadow="hover" @click="openRawDialog('asset_api_duration_seconds')">
          <div class="metric-icon latency">
            <el-icon size="28"><Timer /></el-icon>
          </div>
          <div class="metric-info">
            <div class="metric-value">{{ avgLatency.toFixed(1) }}ms</div>
            <div class="metric-label">平均延迟</div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card class="metric-card clickable" shadow="hover" @click="openRawDialog('db_slow_queries')">
          <div class="metric-icon slow">
            <el-icon size="28"><Warning /></el-icon>
          </div>
          <div class="metric-info">
            <div class="metric-value">{{ businessMetrics?.slowQueries || 0 }}</div>
            <div class="metric-label">慢查询</div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card class="metric-card clickable" shadow="hover" @click="openRawDialog('jvm_memory_used_bytes')">
          <div class="metric-icon memory">
            <el-icon size="28"><Cpu /></el-icon>
          </div>
          <div class="metric-info">
            <div class="metric-value">{{ memoryUsagePercent.toFixed(2) }}%</div>
            <div class="metric-label">JVM 内存</div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- API 请求统计 - 两种展示形式 -->
    <el-row :gutter="20">
      <!-- API 请求分布 - 饼图 -->
      <el-col :span="12">
        <el-card class="chart-card">
          <template #header>
            <div class="card-header">
              <span>API 请求分布（饼图）</span>
              <el-tooltip placement="left" effect="light">
                <template #content>
                  <div class="metric-tooltip">
                    <div class="tooltip-title">指标说明</div>
                    <div class="tooltip-item">
                      <strong>数据来源：</strong>Prometheus 指标 <code>asset_api_requests_total</code>
                    </div>
                    <div class="tooltip-item">
                      <strong>计算逻辑：</strong>按 endpoint 标签分组求和
                    </div>
                    <div class="tooltip-item">
                      <strong>公式：</strong>Σ(asset_api_requests_total{endpoint="xxx"})
                    </div>
                    <div class="tooltip-item">
                      <strong>含义：</strong>统计各 API 端点的累计请求次数
                    </div>
                  </div>
                </template>
                <el-tag size="small" type="info" style="cursor: help">
                  <el-icon><QuestionFilled /></el-icon>
                  指标说明
                </el-tag>
              </el-tooltip>
            </div>
          </template>
          <div id="request-pie-chart" class="chart-container"></div>
        </el-card>
      </el-col>

      <!-- API 请求分布 - 柱状图 -->
      <el-col :span="12">
        <el-card class="chart-card">
          <template #header>
            <div class="card-header">
              <span>API 请求分布（柱状图）</span>
              <el-tooltip placement="left" effect="light">
                <template #content>
                  <div class="metric-tooltip">
                    <div class="tooltip-title">指标说明</div>
                    <div class="tooltip-item">
                      <strong>数据来源：</strong>Prometheus 指标 <code>asset_api_requests_total</code>
                    </div>
                    <div class="tooltip-item">
                      <strong>计算逻辑：</strong>按 endpoint 标签分组求和
                    </div>
                    <div class="tooltip-item">
                      <strong>公式：</strong>Σ(asset_api_requests_total{endpoint="xxx"})
                    </div>
                    <div class="tooltip-item">
                      <strong>含义：</strong>统计各 API 端点的累计请求次数，按方法分类
                    </div>
                  </div>
                </template>
                <el-tag size="small" type="info" style="cursor: help">
                  <el-icon><QuestionFilled /></el-icon>
                  指标说明
                </el-tag>
              </el-tooltip>
            </div>
          </template>
          <div id="request-bar-chart" class="chart-container"></div>
        </el-card>
      </el-col>
    </el-row>

    <el-row :gutter="20" style="margin-top: 20px">
      <!-- API 延迟分布 -->
      <el-col :span="12">
        <el-card class="chart-card">
          <template #header>
            <div class="card-header">
              <span>API 延迟分布</span>
              <el-tooltip placement="left" effect="light">
                <template #content>
                  <div class="metric-tooltip">
                    <div class="tooltip-title">指标说明</div>
                    <div class="tooltip-item">
                      <strong>数据来源：</strong>Prometheus Summary 类型 <code>asset_api_duration_seconds</code>
                    </div>
                    <div class="tooltip-item">
                      <strong>计算逻辑：</strong>
                      <ul style="margin: 4px 0; padding-left: 16px;">
                        <li>P50 = 平均值（Summary 类型无分位数）</li>
                        <li>P95 = 平均值 × 1.5（估算）</li>
                        <li>P99 = 平均值 × 2（估算）</li>
                      </ul>
                    </div>
                    <div class="tooltip-item">
                      <strong>公式：</strong>avg = _sum / _count × 1000（转毫秒）
                    </div>
                    <div class="tooltip-item">
                      <strong>含义：</strong>API 响应时间分布，用于识别性能瓶颈
                    </div>
                  </div>
                </template>
                <el-tag size="small" type="warning" style="cursor: help">
                  <el-icon><QuestionFilled /></el-icon>
                  指标说明
                </el-tag>
              </el-tooltip>
            </div>
          </template>
          <div id="latency-chart" class="chart-container"></div>
        </el-card>
      </el-col>

      <!-- 数据库连接池 -->
      <el-col :span="12">
        <el-card class="chart-card">
          <template #header>
            <div class="card-header">
              <span>数据库连接池</span>
              <el-tooltip placement="left" effect="light">
                <template #content>
                  <div class="metric-tooltip">
                    <div class="tooltip-title">指标说明</div>
                    <div class="tooltip-item">
                      <strong>数据来源：</strong>HikariCP 指标
                      <ul style="margin: 4px 0; padding-left: 16px;">
                        <li><code>hikaricp_connections</code> - 总连接数</li>
                        <li><code>hikaricp_connections_active</code> - 活跃连接</li>
                        <li><code>hikaricp_connections_idle</code> - 空闲连接</li>
                      </ul>
                    </div>
                    <div class="tooltip-item">
                      <strong>计算逻辑：</strong>活跃连接数 / 最大连接数
                    </div>
                    <div class="tooltip-item">
                      <strong>含义：</strong>监控数据库连接使用情况，避免连接耗尽
                    </div>
                    <div class="tooltip-item">
                      <strong>当前状态：</strong>活跃 {{ dbActive }} / 总计 {{ dbTotal }}
                    </div>
                  </div>
                </template>
                <el-tag size="small" type="success" style="cursor: help">
                  <el-icon><QuestionFilled /></el-icon>
                  指标说明
                </el-tag>
              </el-tooltip>
            </div>
          </template>
          <div id="connection-chart" class="chart-container"></div>
          <div class="connection-detail" v-if="businessMetrics">
            <el-row :gutter="20">
              <el-col :span="8">
                <div class="connection-stat">
                  <div class="stat-label">活跃连接</div>
                  <div class="stat-value active">{{ businessMetrics.dbConnections.active }}</div>
                </div>
              </el-col>
              <el-col :span="8">
                <div class="connection-stat">
                  <div class="stat-label">空闲连接</div>
                  <div class="stat-value idle">{{ businessMetrics.dbConnections.idle }}</div>
                </div>
              </el-col>
              <el-col :span="8">
                <div class="connection-stat">
                  <div class="stat-label">最大连接</div>
                  <div class="stat-value max">{{ businessMetrics.dbConnections.max }}</div>
                </div>
              </el-col>
            </el-row>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <el-row :gutter="20" style="margin-top: 20px">
      <!-- JVM 内存 -->
      <el-col :span="12">
        <el-card class="chart-card">
          <template #header>
            <div class="card-header">
              <span>JVM 堆内存</span>
              <el-tooltip placement="left" effect="light">
                <template #content>
                  <div class="metric-tooltip">
                    <div class="tooltip-title">指标说明</div>
                    <div class="tooltip-item">
                      <strong>数据来源：</strong>JVM 指标
                      <ul style="margin: 4px 0; padding-left: 16px;">
                        <li><code>jvm_memory_used_bytes{area="heap"}</code></li>
                        <li><code>jvm_memory_max_bytes{area="heap"}</code></li>
                      </ul>
                    </div>
                    <div class="tooltip-item">
                      <strong>计算逻辑：</strong>按 area="heap" 聚合所有内存池
                    </div>
                    <div class="tooltip-item">
                      <strong>公式：</strong>使用率 = Σ(used) / Σ(max) × 100%
                    </div>
                    <div class="tooltip-item">
                      <strong>含义：</strong>监控 JVM 堆内存使用，预防 OOM
                    </div>
                    <div class="tooltip-item">
                      <strong>阈值：</strong>
                      <ul style="margin: 4px 0; padding-left: 16px;">
                        <li>&lt; 50%：正常（绿色）</li>
                        <li>50% - 80%：警告（黄色）</li>
                        <li>&gt; 80%：危险（红色）</li>
                      </ul>
                    </div>
                  </div>
                </template>
                <el-tag size="small" style="cursor: help">
                  <el-icon><QuestionFilled /></el-icon>
                  指标说明
                </el-tag>
              </el-tooltip>
            </div>
          </template>
          <div class="memory-info">
            <el-progress
              :percentage="parseFloat(memoryUsagePercent.toFixed(2))"
              :stroke-width="20"
              :color="memoryColor"
            />
            <div class="memory-detail">
              <span>已用: {{ formatBytes(businessMetrics?.jvmMemory.used || 0) }}</span>
              <span>最大: {{ formatBytes(businessMetrics?.jvmMemory.max || 0) }}</span>
            </div>
          </div>
        </el-card>
      </el-col>

      <!-- 慢查询统计 -->
      <el-col :span="12">
        <el-card class="chart-card">
          <template #header>
            <div class="card-header">
              <span>慢查询统计</span>
              <el-tooltip placement="left" effect="light">
                <template #content>
                  <div class="metric-tooltip">
                    <div class="tooltip-title">指标说明</div>
                    <div class="tooltip-item">
                      <strong>数据来源：</strong>自定义指标 <code>db_slow_queries_total</code>
                    </div>
                    <div class="tooltip-item">
                      <strong>计算逻辑：</strong>MyBatis 拦截器统计执行时间超过 500ms 的 SQL
                    </div>
                    <div class="tooltip-item">
                      <strong>阈值：</strong>500ms（可配置）
                    </div>
                    <div class="tooltip-item">
                      <strong>含义：</strong>识别性能瓶颈，优化数据库查询
                    </div>
                  </div>
                </template>
                <el-tag size="small" type="danger" style="cursor: help">
                  <el-icon><QuestionFilled /></el-icon>
                  指标说明
                </el-tag>
              </el-tooltip>
            </div>
          </template>
          <div class="slow-query-info">
            <div class="slow-query-value">
              <span class="value">{{ businessMetrics?.slowQueries || 0 }}</span>
              <span class="unit">条</span>
            </div>
            <div class="slow-query-desc">
              执行时间超过 500ms 的 SQL 查询
            </div>
            <el-divider />
            <div class="slow-query-tip">
              <el-icon><InfoFilled /></el-icon>
              <span>慢查询会增加 API 响应时间，建议检查索引或优化 SQL</span>
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 原始数据对话框 -->
    <el-dialog
      v-model="showRawDialog"
      title="Prometheus 原始指标数据"
      width="80%"
      top="5vh"
    >
      <div class="raw-dialog-content">
        <div class="raw-filter">
          <el-input
            v-model="rawFilterInput"
            placeholder="输入指标名称过滤（如：asset_api_requests_total）"
            clearable
            @input="rawFilterKey = rawFilterInput"
          >
            <template #prefix>
              <el-icon><Search /></el-icon>
            </template>
          </el-input>
          <div class="filter-hint">
            快捷过滤：
            <el-tag
              v-for="tag in ['asset_api_requests', 'asset_api_duration', 'jvm_memory', 'hikaricp', 'db_slow']"
              :key="tag"
              size="small"
              class="filter-tag"
              @click="rawFilterInput = tag; rawFilterKey = tag"
            >
              {{ tag }}
            </el-tag>
          </div>
        </div>
        <div class="raw-metrics-container">
          <pre class="raw-metrics-text">{{ filteredRawMetrics || '加载中...' }}</pre>
        </div>
      </div>
      <template #footer>
        <el-button @click="showRawDialog = false">关闭</el-button>
        <el-button type="primary" @click="openUrl('/actuator/prometheus')">
          在新窗口打开完整数据
        </el-button>
      </template>
    </el-dialog>

    <!-- 指标列表对话框 -->
    <el-dialog
      v-model="showMetricsListDialog"
      title="Prometheus 指标列表"
      width="70%"
      top="5vh"
    >
      <div class="metrics-list-content">
        <div class="metrics-filter">
          <el-input
            v-model="metricsListFilter"
            placeholder="搜索指标名称或描述"
            clearable
            style="width: 300px"
          >
            <template #prefix>
              <el-icon><Search /></el-icon>
            </template>
          </el-input>
        </div>
        
        <div v-if="metricsListLoading" class="metrics-loading">
          <el-icon class="is-loading" size="32"><Loading /></el-icon>
          <span>加载中...</span>
        </div>
        
        <div v-else class="metrics-groups">
          <div 
            v-for="(metrics, category) in filteredMetricsList" 
            :key="category"
            class="metrics-group"
          >
            <div class="group-header">
              <el-tag size="small" type="info">{{ category }}</el-tag>
              <span class="group-count">{{ metrics.length }} 个指标</span>
            </div>
            <el-table :data="metrics" size="small" stripe>
              <el-table-column prop="name" label="中文名称" width="180">
                <template #default="{ row }">
                  <span class="metric-cn-name">{{ row.name }}</span>
                </template>
              </el-table-column>
              <el-table-column prop="key" label="指标 Key" width="280">
                <template #default="{ row }">
                  <code class="metric-key">{{ row.key }}</code>
                </template>
              </el-table-column>
              <el-table-column prop="desc" label="描述">
                <template #default="{ row }">
                  <span class="metric-desc">{{ row.desc }}</span>
                </template>
              </el-table-column>
              <el-table-column label="操作" width="100">
                <template #default="{ row }">
                  <el-button 
                    size="small" 
                    text 
                    type="primary"
                    @click="showMetricsListDialog = false; openRawDialog(row.key)"
                  >
                    查看数据
                  </el-button>
                </template>
              </el-table-column>
            </el-table>
          </div>
        </div>
      </div>
      <template #footer>
        <el-button @click="showMetricsListDialog = false">关闭</el-button>
        <el-button type="primary" @click="openUrl('/actuator/metrics')">
          在新窗口打开原始列表
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted, watch, nextTick } from 'vue'
import type { ECharts } from 'echarts'
import {
  fetchPrometheusMetrics,
  fetchPrometheusRawText,
  extractBusinessMetrics,
  type BusinessMetrics,
} from '@/services/monitoringService'
import { ElMessageBox } from 'element-plus'

declare global {
  interface Window {
    echarts: typeof import('echarts')
  }
}

// 告警记录类型
interface AlertRecord {
  id: number
  level: 'CRITICAL' | 'WARNING' | 'INFO'
  message: string
  time: string
  detail: {
    metric: string
    threshold: string
    currentValue: string
    duration: string
    suggestion: string
  }
}

const baseUrl = 'http://localhost:8080'
const loading = ref(false)
const autoRefresh = ref(false)
const lastUpdate = ref('')
const businessMetrics = ref<BusinessMetrics | null>(null)
let refreshTimer: ReturnType<typeof setInterval> | null = null

let requestPieChart: ECharts | null = null
let requestBarChart: ECharts | null = null
let latencyChart: ECharts | null = null
let connectionChart: ECharts | null = null

// 告警规则配置
const ALERT_RULES = {
  jvmMemory: {
    warning: 80,    // 80% 警告
    critical: 90,   // 90% 严重
  },
  dbConnections: {
    warning: 70,    // 70% 警告
    critical: 90,   // 90% 严重
  },
  apiLatency: {
    warning: 200,   // 200ms 警告
    critical: 500,  // 500ms 严重
  },
  slowQueries: {
    warning: 1,     // 1条慢查询警告
    critical: 5,    // 5条慢查询严重
  },
}

// 基于实时指标计算告警记录
const alerts = computed<AlertRecord[]>(() => {
  if (!businessMetrics.value) return []
  
  const alertList: AlertRecord[] = []
  const now = new Date().toLocaleString('zh-CN', { hour12: false })
  
  // 1. JVM 内存告警
  const memoryPercent = memoryUsagePercent.value
  if (memoryPercent >= ALERT_RULES.jvmMemory.critical) {
    alertList.push({
      id: 1,
      level: 'CRITICAL',
      message: `JVM 堆内存使用率严重过高 (${memoryPercent.toFixed(1)}%)`,
      time: now,
      detail: {
        metric: 'jvm_memory_used_bytes',
        threshold: `${ALERT_RULES.jvmMemory.critical}%`,
        currentValue: `${memoryPercent.toFixed(1)}%`,
        duration: '当前状态',
        suggestion: '建议立即检查内存泄漏或重启应用，考虑增加 JVM 堆内存配置',
      },
    })
  } else if (memoryPercent >= ALERT_RULES.jvmMemory.warning) {
    alertList.push({
      id: 1,
      level: 'WARNING',
      message: `JVM 堆内存使用率偏高 (${memoryPercent.toFixed(1)}%)`,
      time: now,
      detail: {
        metric: 'jvm_memory_used_bytes',
        threshold: `${ALERT_RULES.jvmMemory.warning}%`,
        currentValue: `${memoryPercent.toFixed(1)}%`,
        duration: '当前状态',
        suggestion: '建议检查内存使用情况，关注是否有内存泄漏',
      },
    })
  }
  
  // 2. 数据库连接池告警
  const { active, max } = businessMetrics.value.dbConnections
  const connectionPercent = max > 0 ? (active / max) * 100 : 0
  if (connectionPercent >= ALERT_RULES.dbConnections.critical) {
    alertList.push({
      id: 2,
      level: 'CRITICAL',
      message: `数据库连接池即将耗尽 (${active}/${max})`,
      time: now,
      detail: {
        metric: 'hikaricp_connections_active',
        threshold: `${ALERT_RULES.dbConnections.critical}%`,
        currentValue: `${active}/${max} (${connectionPercent.toFixed(1)}%)`,
        duration: '当前状态',
        suggestion: '建议立即检查慢查询或增加连接池大小',
      },
    })
  } else if (connectionPercent >= ALERT_RULES.dbConnections.warning) {
    alertList.push({
      id: 2,
      level: 'WARNING',
      message: `数据库连接池使用率偏高 (${active}/${max})`,
      time: now,
      detail: {
        metric: 'hikaricp_connections_active',
        threshold: `${ALERT_RULES.dbConnections.warning}%`,
        currentValue: `${active}/${max} (${connectionPercent.toFixed(1)}%)`,
        duration: '当前状态',
        suggestion: '建议关注慢查询，考虑优化 SQL 或增加连接池大小',
      },
    })
  }
  
  // 3. 慢查询告警
  const slowQueries = businessMetrics.value.slowQueries
  if (slowQueries >= ALERT_RULES.slowQueries.critical) {
    alertList.push({
      id: 3,
      level: 'CRITICAL',
      message: `检测到 ${slowQueries} 条慢查询`,
      time: now,
      detail: {
        metric: 'db_slow_queries_total',
        threshold: `${ALERT_RULES.slowQueries.critical} 条`,
        currentValue: `${slowQueries} 条`,
        duration: '累计统计',
        suggestion: '建议检查索引配置，优化 SQL 查询语句',
      },
    })
  } else if (slowQueries >= ALERT_RULES.slowQueries.warning) {
    alertList.push({
      id: 3,
      level: 'WARNING',
      message: `检测到 ${slowQueries} 条慢查询`,
      time: now,
      detail: {
        metric: 'db_slow_queries_total',
        threshold: `${ALERT_RULES.slowQueries.warning} 条`,
        currentValue: `${slowQueries} 条`,
        duration: '累计统计',
        suggestion: '建议关注查询性能，考虑添加索引',
      },
    })
  }
  
  // 4. API 延迟告警
  const avgLat = avgLatency.value
  if (avgLat >= ALERT_RULES.apiLatency.critical) {
    alertList.push({
      id: 4,
      level: 'CRITICAL',
      message: `API 平均响应时间过长 (${avgLat.toFixed(1)}ms)`,
      time: now,
      detail: {
        metric: 'asset_api_duration_seconds',
        threshold: `${ALERT_RULES.apiLatency.critical}ms`,
        currentValue: `${avgLat.toFixed(1)}ms`,
        duration: '当前平均值',
        suggestion: '建议检查后端性能瓶颈，优化数据库查询',
      },
    })
  } else if (avgLat >= ALERT_RULES.apiLatency.warning) {
    alertList.push({
      id: 4,
      level: 'WARNING',
      message: `API 平均响应时间偏高 (${avgLat.toFixed(1)}ms)`,
      time: now,
      detail: {
        metric: 'asset_api_duration_seconds',
        threshold: `${ALERT_RULES.apiLatency.warning}ms`,
        currentValue: `${avgLat.toFixed(1)}ms`,
        duration: '当前平均值',
        suggestion: '建议关注 API 性能，检查慢查询或增加缓存',
      },
    })
  }
  
  return alertList
})

// 原始数据对话框相关
const showRawDialog = ref(false)
const rawMetricsText = ref('')
const rawFilterKey = ref('')
const rawFilterInput = ref('')

const filteredRawMetrics = computed(() => {
  if (!rawMetricsText.value) return ''
  const filter = rawFilterKey.value || rawFilterInput.value
  if (!filter) return rawMetricsText.value
  
  const lines = rawMetricsText.value.split('\n')
  const filtered: string[] = []
  let currentMetric = ''
  
  for (const line of lines) {
    if (line.startsWith('#')) {
      // 注释行，记录当前指标组
      currentMetric = line
      filtered.push(line)
    } else if (line.trim()) {
      // 数据行，检查是否匹配过滤条件
      if (line.includes(filter)) {
        filtered.push(line)
      } else {
        // 移除之前添加的注释行（如果没有匹配的数据）
        if (filtered.length > 0 && filtered[filtered.length - 1].startsWith('#')) {
          filtered.pop()
        }
      }
    }
  }
  
  return filtered.join('\n')
})

async function openRawDialog(filterKey?: string) {
  rawFilterKey.value = filterKey || ''
  rawFilterInput.value = filterKey || ''
  showRawDialog.value = true
  
  if (!rawMetricsText.value) {
    try {
      rawMetricsText.value = await fetchPrometheusRawText()
    } catch (error) {
      console.error('Failed to fetch raw metrics:', error)
    }
  }
}

// 指标名称中文描述映射表
const METRIC_DESCRIPTIONS: Record<string, { name: string; desc: string; category: string }> = {
  // 业务指标
  asset_api_requests_total: { name: 'API 请求总数', desc: '统计各 API 端点的累计请求次数', category: '业务指标' },
  asset_api_duration_seconds: { name: 'API 响应时间', desc: '各 API 端点的响应时间分布（秒）', category: '业务指标' },
  'db.query.duration': { name: '数据库查询耗时', desc: 'SQL 查询执行时间统计', category: '业务指标' },
  'db.slow.queries': { name: '慢查询数量', desc: '执行时间超过阈值的 SQL 查询次数', category: '业务指标' },
  
  // JVM 内存
  'jvm.memory.used': { name: 'JVM 已用内存', desc: 'JVM 各内存区域已使用的字节数', category: 'JVM 内存' },
  'jvm.memory.max': { name: 'JVM 最大内存', desc: 'JVM 各内存区域最大可用字节数', category: 'JVM 内存' },
  'jvm.memory.committed': { name: 'JVM 已提交内存', desc: 'JVM 已向操作系统申请的内存量', category: 'JVM 内存' },
  'jvm.memory.usage.after.gc': { name: 'GC 后内存使用率', desc: '垃圾回收后各内存区域的使用率', category: 'JVM 内存' },
  
  // JVM GC
  'jvm.gc.pause': { name: 'GC 暂停时间', desc: '垃圾回收导致的应用暂停时间', category: 'JVM GC' },
  'jvm.gc.live.data.size': { name: 'GC 存活数据大小', desc: 'GC 后老年代存活数据大小', category: 'JVM GC' },
  'jvm.gc.max.data.size': { name: 'GC 最大数据大小', desc: '老年代最大可用空间', category: 'JVM GC' },
  'jvm.gc.memory.allocated': { name: 'GC 内存分配', desc: '年轻代内存分配总量', category: 'JVM GC' },
  'jvm.gc.memory.promoted': { name: 'GC 内存晋升', desc: '从年轻代晋升到老年代的内存量', category: 'JVM GC' },
  'jvm.gc.overhead': { name: 'GC 开销', desc: 'GC 占用的 CPU 时间比例', category: 'JVM GC' },
  'jvm.gc.concurrent.phase.time': { name: '并发 GC 阶段时间', desc: '并发 GC 阶段的执行时间', category: 'JVM GC' },
  
  // JVM 线程
  'jvm.threads.live': { name: 'JVM 活跃线程数', desc: '当前 JVM 活跃的线程总数', category: 'JVM 线程' },
  'jvm.threads.daemon': { name: 'JVM 守护线程数', desc: '当前 JVM 守护线程数量', category: 'JVM 线程' },
  'jvm.threads.peak': { name: 'JVM 线程峰值', desc: 'JVM 启动以来的线程数峰值', category: 'JVM 线程' },
  'jvm.threads.started': { name: 'JVM 启动线程数', desc: 'JVM 启动以来创建的线程总数', category: 'JVM 线程' },
  'jvm.threads.states': { name: 'JVM 线程状态', desc: '各状态（阻塞、等待等）的线程数量', category: 'JVM 线程' },
  
  // JVM 类加载
  'jvm.classes.loaded': { name: '已加载类数量', desc: '当前 JVM 已加载的类数量', category: 'JVM 类加载' },
  'jvm.classes.unloaded': { name: '已卸载类数量', desc: 'JVM 启动以来卸载的类数量', category: 'JVM 类加载' },
  'jvm.compilation.time': { name: 'JIT 编译时间', desc: 'JIT 编译器累计编译时间', category: 'JVM 类加载' },
  'jvm.buffer.count': { name: '缓冲区数量', desc: 'NIO 缓冲区数量', category: 'JVM 类加载' },
  'jvm.buffer.memory.used': { name: '缓冲区内存使用', desc: 'NIO 缓冲区使用的内存量', category: 'JVM 类加载' },
  'jvm.buffer.total.capacity': { name: '缓冲区总容量', desc: 'NIO 缓冲区的总容量', category: 'JVM 类加载' },
  'jvm.info': { name: 'JVM 信息', desc: 'JVM 版本和运行时信息', category: 'JVM 类加载' },
  
  // 数据库连接池 (HikariCP)
  'hikaricp.connections': { name: '连接池总连接数', desc: 'HikariCP 连接池的连接总数', category: '数据库连接池' },
  'hikaricp.connections.active': { name: '活跃连接数', desc: '当前正在使用的数据库连接数', category: '数据库连接池' },
  'hikaricp.connections.idle': { name: '空闲连接数', desc: '当前空闲的数据库连接数', category: '数据库连接池' },
  'hikaricp.connections.max': { name: '最大连接数', desc: '连接池配置的最大连接数', category: '数据库连接池' },
  'hikaricp.connections.min': { name: '最小连接数', desc: '连接池配置的最小连接数', category: '数据库连接池' },
  'hikaricp.connections.pending': { name: '等待连接数', desc: '正在等待获取连接的线程数', category: '数据库连接池' },
  'hikaricp.connections.timeout': { name: '连接超时次数', desc: '获取连接超时的累计次数', category: '数据库连接池' },
  'hikaricp.connections.usage': { name: '连接池使用率', desc: '连接池当前使用率', category: '数据库连接池' },
  'hikaricp.connections.acquire': { name: '连接获取时间', desc: '获取数据库连接的耗时', category: '数据库连接池' },
  'hikaricp.connections.creation': { name: '连接创建时间', desc: '创建新数据库连接的耗时', category: '数据库连接池' },
  
  // JDBC
  'jdbc.connections.active': { name: 'JDBC 活跃连接', desc: '通过 JDBC 获取的活跃连接数', category: '数据库连接池' },
  'jdbc.connections.idle': { name: 'JDBC 空闲连接', desc: 'JDBC 空闲连接数', category: '数据库连接池' },
  'jdbc.connections.max': { name: 'JDBC 最大连接数', desc: 'JDBC 配置的最大连接数', category: '数据库连接池' },
  'jdbc.connections.min': { name: 'JDBC 最小连接数', desc: 'JDBC 配置的最小连接数', category: '数据库连接池' },
  
  // HTTP 请求
  'http.server.requests': { name: 'HTTP 请求数', desc: 'HTTP 请求总数和响应时间', category: 'HTTP 请求' },
  'http.server.requests.active': { name: '活跃 HTTP 请求', desc: '当前正在处理的 HTTP 请求数', category: 'HTTP 请求' },
  
  // Spring Security
  'spring.security.authorizations': { name: '授权请求总数', desc: 'Spring Security 授权检查次数', category: 'Spring Security' },
  'spring.security.authorizations.active': { name: '活跃授权检查', desc: '当前正在进行的授权检查数', category: 'Spring Security' },
  'spring.security.filterchains': { name: '过滤器链指标', desc: '各安全过滤器的执行统计', category: 'Spring Security' },
  'spring.security.http.secured.requests': { name: '安全请求统计', desc: '经过安全处理的 HTTP 请求统计', category: 'Spring Security' },
  'spring.security.http.secured.requests.active': { name: '活跃安全请求', desc: '当前正在处理的安全请求数', category: 'Spring Security' },
  
  // 线程池
  'executor.active': { name: '线程池活跃线程', desc: '线程池中正在执行任务的线程数', category: '线程池' },
  'executor.completed': { name: '已完成任务数', desc: '线程池已完成的任务总数', category: '线程池' },
  'executor.pool.core': { name: '核心线程数', desc: '线程池核心线程数配置', category: '线程池' },
  'executor.pool.max': { name: '最大线程数', desc: '线程池最大线程数配置', category: '线程池' },
  'executor.pool.size': { name: '当前线程数', desc: '线程池当前线程数', category: '线程池' },
  'executor.queue.remaining': { name: '队列剩余容量', desc: '任务队列剩余容量', category: '线程池' },
  'executor.queued': { name: '队列任务数', desc: '任务队列中等待的任务数', category: '线程池' },
  
  // Tomcat Session
  'tomcat.sessions.active.current': { name: '当前活跃会话', desc: 'Tomcat 当前活跃的会话数', category: 'Tomcat' },
  'tomcat.sessions.active.max': { name: '最大活跃会话', desc: 'Tomcat 启动以来的最大活跃会话数', category: 'Tomcat' },
  'tomcat.sessions.created': { name: '创建会话数', desc: 'Tomcat 创建的会话总数', category: 'Tomcat' },
  'tomcat.sessions.expired': { name: '过期会话数', desc: 'Tomcat 过期的会话总数', category: 'Tomcat' },
  'tomcat.sessions.rejected': { name: '拒绝会话数', desc: '因超过最大限制被拒绝的会话数', category: 'Tomcat' },
  'tomcat.sessions.alive.max': { name: '会话最大存活时间', desc: '会话最大存活时间（秒）', category: 'Tomcat' },
  
  // 磁盘
  'disk.free': { name: '磁盘可用空间', desc: '磁盘剩余可用空间', category: '系统资源' },
  'disk.total': { name: '磁盘总空间', desc: '磁盘总容量', category: '系统资源' },
  
  // 进程
  'process.cpu.time': { name: '进程 CPU 时间', desc: 'JVM 进程使用的 CPU 时间', category: '进程' },
  'process.cpu.usage': { name: '进程 CPU 使用率', desc: 'JVM 进程的 CPU 使用率', category: '进程' },
  'process.files.max': { name: '最大文件描述符', desc: '进程可打开的最大文件描述符数', category: '进程' },
  'process.files.open': { name: '已打开文件数', desc: '进程当前打开的文件描述符数', category: '进程' },
  'process.start.time': { name: '进程启动时间', desc: 'JVM 进程启动时间（Unix 时间戳）', category: '进程' },
  'process.uptime': { name: '进程运行时间', desc: 'JVM 进程运行时长（秒）', category: '进程' },
  
  // 系统
  'system.cpu.count': { name: 'CPU 核心数', desc: '系统可用的 CPU 核心数', category: '系统' },
  'system.cpu.usage': { name: '系统 CPU 使用率', desc: '系统整体 CPU 使用率', category: '系统' },
  'system.load.average.1m': { name: '系统负载', desc: '系统 1 分钟平均负载', category: '系统' },
  
  // 应用启动
  'application.ready.time': { name: '应用就绪时间', desc: '应用从启动到就绪的时间', category: '应用启动' },
  'application.started.time': { name: '应用启动时间', desc: '应用启动耗时', category: '应用启动' },
  
  // 日志
  'logback.events': { name: '日志事件数', desc: '按级别统计的日志事件数量', category: '日志' },
}

// 指标列表对话框相关
const showMetricsListDialog = ref(false)
const metricsList = ref<string[]>([])
const metricsListFilter = ref('')
const metricsListLoading = ref(false)

const filteredMetricsList = computed(() => {
  let list = metricsList.value.map(key => {
    const info = METRIC_DESCRIPTIONS[key]
    return {
      key,  // 原始指标 Key (如 "application.ready.time")
      name: info?.name || key,  // 中文名称
      desc: info?.desc || '暂无描述',
      category: info?.category || '其他'
    }
  })
  
  if (metricsListFilter.value) {
    const filter = metricsListFilter.value.toLowerCase()
    list = list.filter(m => 
      m.key.toLowerCase().includes(filter) ||
      m.name.toLowerCase().includes(filter) ||
      m.desc.toLowerCase().includes(filter)
    )
  }
  
  // 按类别分组
  const grouped: Record<string, typeof list> = {}
  for (const metric of list) {
    if (!grouped[metric.category]) {
      grouped[metric.category] = []
    }
    grouped[metric.category].push(metric)
  }
  
  return grouped
})

async function openMetricsListDialog() {
  showMetricsListDialog.value = true
  if (metricsList.value.length === 0) {
    metricsListLoading.value = true
    try {
      const response = await fetch('/actuator/metrics')
      const data = await response.json()
      metricsList.value = data.names || []
    } catch (error) {
      console.error('Failed to fetch metrics list:', error)
    } finally {
      metricsListLoading.value = false
    }
  }
}

const totalRequests = computed(() => {
  if (!businessMetrics.value) return 0
  return businessMetrics.value.apiRequests.reduce((sum, r) => sum + r.count, 0)
})

const avgLatency = computed(() => {
  if (!businessMetrics.value || businessMetrics.value.apiLatency.length === 0) return 0
  const data = businessMetrics.value.apiLatency
  return data.reduce((sum, l) => sum + l.avg, 0) / data.length
})

const memoryUsagePercent = computed(() => {
  if (!businessMetrics.value) return 0
  const { used, max } = businessMetrics.value.jvmMemory
  return max > 0 ? (used / max) * 100 : 0
})

const memoryColor = computed(() => {
  const percent = memoryUsagePercent.value
  if (percent < 50) return '#10b981'
  if (percent < 80) return '#f59e0b'
  return '#ef4444'
})

const dbActive = computed(() => businessMetrics.value?.dbConnections.active || 0)
const dbTotal = computed(() => businessMetrics.value?.dbConnections.max || 0)

function getAlertTagType(level: string) {
  switch (level) {
    case 'CRITICAL':
      return 'danger'
    case 'WARNING':
      return 'warning'
    case 'INFO':
      return 'info'
    default:
      return 'info'
  }
}

function showAlertDetail(alert: AlertRecord) {
  const levelColors = {
    CRITICAL: '#ef4444',
    WARNING: '#f59e0b',
    INFO: '#3b82f6',
  }

  ElMessageBox.alert(
    `
    <div style="font-size: 14px; line-height: 2;">
      <div style="margin-bottom: 16px;">
        <strong style="color: ${levelColors[alert.level]}; font-size: 16px;">
          ${alert.level === 'CRITICAL' ? '🔴 严重' : alert.level === 'WARNING' ? '🟡 警告' : '🔵 信息'}
        </strong>
      </div>
      <div style="background: #f5f7fa; padding: 12px; border-radius: 6px; margin-bottom: 12px;">
        <div style="margin-bottom: 8px;"><strong>告警消息：</strong>${alert.message}</div>
        <div style="color: #909399; font-size: 12px;"><strong>发生时间：</strong>${alert.time}</div>
      </div>
      <div style="margin-bottom: 16px;">
        <div style="font-weight: 600; margin-bottom: 8px;">📋 详细信息</div>
        <table style="width: 100%; border-collapse: collapse;">
          <tr style="background: #f5f7fa;">
            <td style="padding: 8px; border: 1px solid #e4e7ed; width: 100px;">监控指标</td>
            <td style="padding: 8px; border: 1px solid #e4e7ed;"><code style="background: #f5f7fa; padding: 2px 6px;">${alert.detail.metric}</code></td>
          </tr>
          <tr>
            <td style="padding: 8px; border: 1px solid #e4e7ed;">告警阈值</td>
            <td style="padding: 8px; border: 1px solid #e4e7ed;">${alert.detail.threshold}</td>
          </tr>
          <tr style="background: #f5f7fa;">
            <td style="padding: 8px; border: 1px solid #e4e7ed;">当前值</td>
            <td style="padding: 8px; border: 1px solid #e4e7ed; color: ${levelColors[alert.level]}; font-weight: 600;">${alert.detail.currentValue}</td>
          </tr>
          <tr>
            <td style="padding: 8px; border: 1px solid #e4e7ed;">持续时间</td>
            <td style="padding: 8px; border: 1px solid #e4e7ed;">${alert.detail.duration}</td>
          </tr>
        </table>
      </div>
      <div style="background: #ecfdf5; padding: 12px; border-radius: 6px; border-left: 4px solid #10b981;">
        <div style="font-weight: 600; margin-bottom: 4px; color: #10b981;">💡 处理建议</div>
        <div style="color: #047857;">${alert.detail.suggestion}</div>
      </div>
    </div>
    `,
    '告警详情',
    {
      dangerouslyUseHTMLString: true,
      confirmButtonText: '我知道了',
      customClass: 'alert-detail-dialog',
    }
  )
}

function formatBytes(bytes: number): string {
  if (bytes === 0) return '0 B'
  const k = 1024
  const sizes = ['B', 'KB', 'MB', 'GB']
  const i = Math.floor(Math.log(bytes) / Math.log(k))
  return parseFloat((bytes / Math.pow(k, i)).toFixed(2)) + ' ' + sizes[i]
}

function renderCharts() {
  renderRequestPieChart()
  renderRequestBarChart()
  renderLatencyChart()
  renderConnectionChart()
}

function renderRequestPieChart() {
  const container = document.querySelector('#request-pie-chart') as HTMLElement
  if (!container) return

  if (requestPieChart) {
    requestPieChart.dispose()
  }
  requestPieChart = window.echarts.init(container)

  const data = businessMetrics.value?.apiRequests || []

  requestPieChart.setOption({
    tooltip: {
      trigger: 'item',
      formatter: '{b}: {c} ({d}%)',
    },
    legend: {
      orient: 'vertical',
      right: 10,
      top: 'center',
    },
    series: [
      {
        name: '请求数',
        type: 'pie',
        radius: ['40%', '70%'],
        center: ['40%', '50%'],
        avoidLabelOverlap: false,
        itemStyle: {
          borderRadius: 10,
          borderColor: '#fff',
          borderWidth: 2,
        },
        label: {
          show: true,
          formatter: '{b}\n{c}',
        },
        data: data.map(r => ({
          name: r.endpoint,
          value: r.count,
        })),
      },
    ],
  })
}

function renderRequestBarChart() {
  const container = document.querySelector('#request-bar-chart') as HTMLElement
  if (!container) return

  if (requestBarChart) {
    requestBarChart.dispose()
  }
  requestBarChart = window.echarts.init(container)

  const data = businessMetrics.value?.apiRequests || []
  const methods = ['GET', 'POST', 'DELETE']

  requestBarChart.setOption({
    tooltip: {
      trigger: 'axis',
      axisPointer: { type: 'shadow' },
    },
    legend: {
      data: methods,
      top: 0,
    },
    grid: {
      left: '3%',
      right: '4%',
      bottom: '3%',
      containLabel: true,
    },
    xAxis: {
      type: 'category',
      data: [...new Set(data.map(r => r.endpoint))],
    },
    yAxis: {
      type: 'value',
      name: '请求数',
    },
    series: methods.map(method => ({
      name: method,
      type: 'bar',
      data: [...new Set(data.map(r => r.endpoint))].map(endpoint => {
        const item = data.find(r => r.endpoint === endpoint && r.method === method)
        return item ? item.count : 0
      }),
    })),
  })
}

function renderLatencyChart() {
  const container = document.querySelector('#latency-chart') as HTMLElement
  if (!container) return

  if (latencyChart) {
    latencyChart.dispose()
  }
  latencyChart = window.echarts.init(container)

  const data = businessMetrics.value?.apiLatency || []

  latencyChart.setOption({
    tooltip: {
      trigger: 'axis',
      axisPointer: { type: 'shadow' },
    },
    legend: {
      data: ['P50', 'P95', 'P99'],
      top: 0,
    },
    grid: {
      left: '3%',
      right: '4%',
      bottom: '3%',
      containLabel: true,
    },
    xAxis: {
      type: 'category',
      data: data.map(l => l.endpoint),
    },
    yAxis: {
      type: 'value',
      name: '延迟 (ms)',
      axisLabel: {
        formatter: '{value}ms',
      },
    },
    series: [
      {
        name: 'P50',
        type: 'bar',
        data: data.map(l => l.p50.toFixed(2)),
        itemStyle: { color: '#10b981' },
      },
      {
        name: 'P95',
        type: 'bar',
        data: data.map(l => l.p95.toFixed(2)),
        itemStyle: { color: '#f59e0b' },
      },
      {
        name: 'P99',
        type: 'bar',
        data: data.map(l => l.p99.toFixed(2)),
        itemStyle: { color: '#ef4444' },
      },
    ],
  })
}

function renderConnectionChart() {
  const container = document.querySelector('#connection-chart') as HTMLElement
  if (!container) return

  if (connectionChart) {
    connectionChart.dispose()
  }
  connectionChart = window.echarts.init(container)

  const db = businessMetrics.value?.dbConnections || { active: 0, idle: 0, max: 20 }

  connectionChart.setOption({
    tooltip: {
      trigger: 'item',
    },
    series: [
      {
        name: '连接数',
        type: 'gauge',
        center: ['50%', '55%'],
        radius: '70%',
        max: db.max,
        splitNumber: 4,
        axisLine: {
          lineStyle: {
            width: 20,
            color: [
              [0.3, '#10b981'],
              [0.7, '#f59e0b'],
              [1, '#ef4444'],
            ],
          },
        },
        pointer: {
          itemStyle: {
            color: 'auto',
          },
        },
        axisTick: { show: false },
        splitLine: { length: 12, lineStyle: { width: 2, color: '#999' } },
        axisLabel: { distance: 20, fontSize: 11 },
        detail: {
          valueAnimation: true,
          formatter: '{value}',
          fontSize: 18,
          offsetCenter: [0, '70%'],
        },
        data: [{ value: db.active, name: '活跃连接' }],
      },
    ],
  })
}

function handleResize() {
  requestPieChart?.resize()
  requestBarChart?.resize()
  latencyChart?.resize()
  connectionChart?.resize()
}

async function refreshMetrics() {
  loading.value = true
  try {
    const metrics = await fetchPrometheusMetrics()
    businessMetrics.value = extractBusinessMetrics(metrics)
    lastUpdate.value = new Date().toLocaleTimeString()
    await nextTick()
    renderCharts()
  } catch (error) {
    console.error('Failed to fetch metrics:', error)
  } finally {
    loading.value = false
  }
}

function openUrl(path: string) {
  window.open(baseUrl + path, '_blank')
}

watch(autoRefresh, (enabled) => {
  if (enabled) {
    refreshTimer = setInterval(refreshMetrics, 5000)
  } else if (refreshTimer) {
    clearInterval(refreshTimer)
    refreshTimer = null
  }
})

onMounted(() => {
  setTimeout(() => {
    refreshMetrics()
  }, 100)
  window.addEventListener('resize', handleResize)
})

onUnmounted(() => {
  window.removeEventListener('resize', handleResize)
  if (refreshTimer) {
    clearInterval(refreshTimer)
  }
  requestPieChart?.dispose()
  requestBarChart?.dispose()
  latencyChart?.dispose()
  connectionChart?.dispose()
})
</script>

<style scoped>
.monitoring-page {
  max-width: 1400px;
  margin: 0 auto;
}

.links-card {
  margin-bottom: 20px;
}

.link-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 16px;
}

.link-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  padding: 20px;
  background: #f8fafc;
  border-radius: 8px;
  cursor: pointer;
  transition: all 0.3s;
}

.link-item:hover {
  background: #e2e8f0;
  transform: translateY(-2px);
}

.link-item span {
  font-size: 14px;
  color: #475569;
}

.alert-card {
  margin-bottom: 20px;
}

.alert-badge {
  margin-left: 8px;
}

.alert-list {
  max-height: 200px;
  overflow-y: auto;
}

.alert-item {
  display: flex;
  align-items: center;
  padding: 12px 16px;
  background: #f8fafc;
  border-radius: 8px;
  margin-bottom: 8px;
  cursor: pointer;
  transition: all 0.3s;
}

.alert-item:last-child {
  margin-bottom: 0;
}

.alert-item:hover {
  background: #e2e8f0;
  transform: translateX(4px);
}

.alert-level {
  flex-shrink: 0;
  margin-right: 12px;
}

.alert-content {
  flex: 1;
  min-width: 0;
}

.alert-message {
  font-size: 14px;
  color: #1e293b;
  font-weight: 500;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.alert-time {
  font-size: 12px;
  color: #909399;
  margin-top: 4px;
}

.alert-arrow {
  color: #94a3b8;
  flex-shrink: 0;
}

.action-bar {
  margin-bottom: 20px;
}

.action-content {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.action-content .left {
  display: flex;
  align-items: center;
}

.last-update {
  margin-left: 16px;
  color: #909399;
  font-size: 13px;
}

.metric-cards {
  margin-bottom: 20px;
}

.metric-card {
  display: flex;
  align-items: center;
  padding: 10px;
}

.metric-card :deep(.el-card__body) {
  display: flex;
  align-items: center;
  width: 100%;
  padding: 20px;
}

.metric-icon {
  width: 56px;
  height: 56px;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: white;
  margin-right: 16px;
}

.metric-icon.requests {
  background: linear-gradient(135deg, #3b82f6, #1d4ed8);
}

.metric-icon.latency {
  background: linear-gradient(135deg, #10b981, #059669);
}

.metric-icon.slow {
  background: linear-gradient(135deg, #f59e0b, #d97706);
}

.metric-icon.memory {
  background: linear-gradient(135deg, #8b5cf6, #6d28d9);
}

.metric-info {
  flex: 1;
}

.metric-value {
  font-size: 28px;
  font-weight: 700;
  color: #1e293b;
  line-height: 1.2;
}

.metric-label {
  font-size: 13px;
  color: #64748b;
  margin-top: 4px;
}

.chart-card {
  height: 400px;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-weight: 600;
}

.chart-container {
  width: 100%;
  height: 280px;
}

.connection-detail {
  padding: 16px;
  border-top: 1px solid #e4e7ed;
}

.connection-stat {
  text-align: center;
}

.connection-stat .stat-label {
  font-size: 12px;
  color: #909399;
  margin-bottom: 4px;
}

.connection-stat .stat-value {
  font-size: 24px;
  font-weight: 700;
}

.connection-stat .stat-value.active {
  color: #f59e0b;
}

.connection-stat .stat-value.idle {
  color: #10b981;
}

.connection-stat .stat-value.max {
  color: #3b82f6;
}

.memory-info {
  padding: 40px 20px;
}

.memory-detail {
  display: flex;
  justify-content: space-between;
  margin-top: 16px;
  font-size: 13px;
  color: #64748b;
}

.slow-query-info {
  padding: 20px;
  text-align: center;
}

.slow-query-value {
  margin-bottom: 8px;
}

.slow-query-value .value {
  font-size: 48px;
  font-weight: 700;
  color: #ef4444;
}

.slow-query-value .unit {
  font-size: 16px;
  color: #64748b;
  margin-left: 4px;
}

.slow-query-desc {
  font-size: 14px;
  color: #64748b;
}

.slow-query-tip {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  font-size: 12px;
  color: #909399;
}

.metric-tooltip {
  max-width: 320px;
}

.tooltip-title {
  font-weight: 600;
  font-size: 14px;
  margin-bottom: 8px;
  padding-bottom: 8px;
  border-bottom: 1px solid #e4e7ed;
}

.tooltip-item {
  font-size: 12px;
  margin-bottom: 8px;
  line-height: 1.6;
}

.tooltip-item:last-child {
  margin-bottom: 0;
}

.tooltip-item code {
  background: #f5f7fa;
  padding: 2px 6px;
  border-radius: 4px;
  font-family: monospace;
  font-size: 11px;
}

.tooltip-item ul {
  margin: 4px 0;
  padding-left: 16px;
}

.tooltip-item li {
  margin-bottom: 2px;
}

/* 原始数据对话框样式 */
.raw-dialog-content {
  display: flex;
  flex-direction: column;
  height: 70vh;
}

.raw-filter {
  margin-bottom: 16px;
}

.filter-hint {
  margin-top: 8px;
  font-size: 12px;
  color: #909399;
}

.filter-tag {
  margin-right: 8px;
  cursor: pointer;
}

.filter-tag:hover {
  opacity: 0.8;
}

.raw-metrics-container {
  flex: 1;
  overflow: auto;
  background: #f5f7fa;
  border-radius: 8px;
  border: 1px solid #dcdfe6;
}

.raw-metrics-text {
  margin: 0;
  padding: 16px;
  font-family: 'Monaco', 'Menlo', 'Ubuntu Mono', monospace;
  font-size: 12px;
  line-height: 1.5;
  white-space: pre-wrap;
  word-break: break-all;
}

/* 可点击的指标卡片样式 */
.metric-card.clickable {
  cursor: pointer;
  transition: transform 0.2s, box-shadow 0.2s;
}

.metric-card.clickable:hover {
  transform: translateY(-2px);
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.15);
}

/* 指标列表对话框样式 */
.metrics-list-content {
  height: 70vh;
  display: flex;
  flex-direction: column;
}

.metrics-filter {
  margin-bottom: 16px;
}

.metrics-loading {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 200px;
  color: #909399;
  gap: 12px;
}

.metrics-groups {
  flex: 1;
  overflow: auto;
}

.metrics-group {
  margin-bottom: 20px;
}

.group-header {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 8px;
}

.group-count {
  font-size: 12px;
  color: #909399;
}

.metric-cn-name {
  font-weight: 500;
  color: #303133;
}

.metric-key {
  font-family: 'Monaco', 'Menlo', monospace;
  font-size: 11px;
  background: #f5f7fa;
  padding: 2px 6px;
  border-radius: 4px;
  color: #606266;
}

.metric-desc {
  font-size: 13px;
  color: #606266;
}
</style>
