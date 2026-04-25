<template>
  <div class="monitoring-page">
    <el-row :gutter="20">
      <!-- 系统状态 -->
      <el-col :span="24">
        <el-card class="section-card">
          <template #header>
            <div class="card-header">
              <el-icon><Monitor /></el-icon>
              <span>系统状态</span>
            </div>
          </template>
          <div class="link-grid">
            <div class="link-item" @click="openUrl('/actuator/health')">
              <div class="link-icon health">
                <el-icon size="32"><CircleCheck /></el-icon>
              </div>
              <div class="link-info">
                <h3>健康检查</h3>
                <p>查看服务健康状态</p>
              </div>
              <el-icon class="link-arrow"><ArrowRight /></el-icon>
            </div>
            
            <div class="link-item" @click="openUrl('/actuator/info')">
              <div class="link-icon info">
                <el-icon size="32"><InfoFilled /></el-icon>
              </div>
              <div class="link-info">
                <h3>应用信息</h3>
                <p>查看应用基本信息</p>
              </div>
              <el-icon class="link-arrow"><ArrowRight /></el-icon>
            </div>
          </div>
        </el-card>
      </el-col>

      <!-- Prometheus 监控 -->
      <el-col :span="24">
        <el-card class="section-card">
          <template #header>
            <div class="card-header">
              <el-icon><TrendCharts /></el-icon>
              <span>Prometheus 监控</span>
            </div>
          </template>
          <div class="link-grid">
            <div class="link-item" @click="openUrl('/actuator/prometheus')">
              <div class="link-icon prometheus">
                <el-icon size="32"><DataAnalysis /></el-icon>
              </div>
              <div class="link-info">
                <h3>指标端点</h3>
                <p>http://localhost:8080/actuator/prometheus</p>
              </div>
              <el-icon class="link-arrow"><ArrowRight /></el-icon>
            </div>
            
            <div class="link-item" @click="openUrl('/actuator/metrics')">
              <div class="link-icon metrics">
                <el-icon size="32"><Histogram /></el-icon>
              </div>
              <div class="link-info">
                <h3>业务指标</h3>
                <p>查看可用指标列表</p>
              </div>
              <el-icon class="link-arrow"><ArrowRight /></el-icon>
            </div>
          </div>
        </el-card>
      </el-col>

      <!-- API 文档 -->
      <el-col :span="24">
        <el-card class="section-card">
          <template #header>
            <div class="card-header">
              <el-icon><Document /></el-icon>
              <span>API 文档</span>
            </div>
          </template>
          <div class="link-grid">
            <div class="link-item" @click="openUrl('/swagger-ui.html')">
              <div class="link-icon swagger">
                <el-icon size="32"><Document /></el-icon>
              </div>
              <div class="link-info">
                <h3>Swagger UI</h3>
                <p>http://localhost:8080/swagger-ui.html</p>
              </div>
              <el-icon class="link-arrow"><ArrowRight /></el-icon>
            </div>
            
            <div class="link-item" @click="openUrl('/api-docs')">
              <div class="link-icon api-docs">
                <el-icon size="32"><Tickets /></el-icon>
              </div>
              <div class="link-info">
                <h3>OpenAPI JSON</h3>
                <p>http://localhost:8080/api-docs</p>
              </div>
              <el-icon class="link-arrow"><ArrowRight /></el-icon>
            </div>
          </div>
        </el-card>
      </el-col>

      <!-- 常用指标 -->
      <el-col :span="24">
        <el-card class="section-card">
          <template #header>
            <div class="card-header">
              <el-icon><SetUp /></el-icon>
              <span>常用指标查询</span>
            </div>
          </template>
          <div class="metrics-list">
            <div class="metric-item" v-for="metric in commonMetrics" :key="metric.name">
              <div class="metric-name">{{ metric.name }}</div>
              <div class="metric-desc">{{ metric.desc }}</div>
              <el-button size="small" @click="openMetric(metric.path)">查看</el-button>
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'

const baseUrl = 'http://localhost:8080'

const commonMetrics = ref([
  { name: 'API 请求计数', desc: 'asset_api_requests_total', path: '/actuator/prometheus' },
  { name: 'API 延迟分布', desc: 'asset_api_duration_seconds', path: '/actuator/prometheus' },
  { name: 'JVM 内存使用', desc: 'jvm_memory_used_bytes', path: '/actuator/prometheus' },
  { name: 'HTTP 请求统计', desc: 'http_server_requests_seconds', path: '/actuator/prometheus' },
  { name: '数据库连接池', desc: 'hikaricp_connections', path: '/actuator/prometheus' },
])

const openUrl = (path: string) => {
  window.open(baseUrl + path, '_blank')
}

const openMetric = (path: string) => {
  window.open(baseUrl + path, '_blank')
}
</script>

<style scoped>
.monitoring-page {
  max-width: 1200px;
  margin: 0 auto;
}

.section-card {
  margin-bottom: 20px;
}

.card-header {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 16px;
  font-weight: 600;
}

.link-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(400px, 1fr));
  gap: 16px;
}

.link-item {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 20px;
  background: #f8fafc;
  border-radius: 8px;
  cursor: pointer;
  transition: all 0.3s;
}

.link-item:hover {
  background: #e2e8f0;
  transform: translateY(-2px);
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.1);
}

.link-icon {
  width: 64px;
  height: 64px;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: white;
  flex-shrink: 0;
}

.link-icon.health {
  background: linear-gradient(135deg, #10b981, #059669);
}

.link-icon.info {
  background: linear-gradient(135deg, #3b82f6, #1d4ed8);
}

.link-icon.prometheus {
  background: linear-gradient(135deg, #e6522c, #c4411a);
}

.link-icon.metrics {
  background: linear-gradient(135deg, #8b5cf6, #6d28d9);
}

.link-icon.swagger {
  background: linear-gradient(135deg, #85ea2d, #49a32b);
}

.link-icon.api-docs {
  background: linear-gradient(135deg, #f59e0b, #d97706);
}

.link-info {
  flex: 1;
}

.link-info h3 {
  margin: 0 0 4px 0;
  font-size: 16px;
  color: #1e293b;
}

.link-info p {
  margin: 0;
  font-size: 13px;
  color: #64748b;
  font-family: monospace;
}

.link-arrow {
  color: #94a3b8;
  font-size: 20px;
}

.metrics-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.metric-item {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 12px 16px;
  background: #f8fafc;
  border-radius: 6px;
}

.metric-name {
  font-weight: 600;
  color: #1e293b;
  min-width: 140px;
}

.metric-desc {
  flex: 1;
  font-family: monospace;
  font-size: 13px;
  color: #64748b;
}
</style>
