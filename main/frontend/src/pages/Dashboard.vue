<template>
  <div class="dashboard">
    <div class="page-title">
      <h2>数据概览</h2>
      <el-text type="info" size="small">素材库统计分析（三条指定查询）</el-text>
    </div>

    <el-row :gutter="20">
      <!-- Q1：各上传人平均文件大小 - 柱状图 -->
      <el-col :span="24" style="margin-bottom: 20px">
        <el-card shadow="never">
          <template #header>
            <div class="card-header">
              <span class="card-title">Q1 - 各上传人平均文件大小（已通过素材 Top 10）</span>
              <el-button
                size="small"
                :loading="q1Loading"
                @click="loadQ1"
              >
                <el-icon><Refresh /></el-icon>
                刷新
              </el-button>
            </div>
          </template>

          <div v-if="q1Loading" class="chart-loading">
            <el-skeleton animated>
              <template #template>
                <el-skeleton-item variant="rect" style="height: 320px" />
              </template>
            </el-skeleton>
          </div>
          <div v-else-if="q1Error" class="chart-error">
            <el-empty :description="q1Error" />
          </div>
          <div v-else id="q1-chart" class="chart-container" style="height: 340px" />
        </el-card>
      </el-col>

      <!-- Q2：标签 Top 5 - 饼图 -->
      <el-col :xs="24" :sm="24" :md="12" style="margin-bottom: 20px">
        <el-card shadow="never">
          <template #header>
            <div class="card-header">
              <span class="card-title">Q2 - 标签 Top 5 分布</span>
              <el-button size="small" :loading="q2Loading" @click="loadQ2">
                <el-icon><Refresh /></el-icon>
                刷新
              </el-button>
            </div>
          </template>

          <div v-if="q2Loading" class="chart-loading">
            <el-skeleton animated>
              <template #template>
                <el-skeleton-item variant="rect" style="height: 320px" />
              </template>
            </el-skeleton>
          </div>
          <div v-else-if="q2Error" class="chart-error">
            <el-empty :description="q2Error" />
          </div>
          <div v-else id="q2-chart" class="chart-container" style="height: 340px" />
        </el-card>
      </el-col>

      <!-- Q3：各平台审核通过率 - 分组柱状图 -->
      <el-col :xs="24" :sm="24" :md="12" style="margin-bottom: 20px">
        <el-card shadow="never">
          <template #header>
            <div class="card-header">
              <span class="card-title">Q3 - 各平台审核通过率</span>
              <el-button size="small" :loading="q3Loading" @click="loadQ3">
                <el-icon><Refresh /></el-icon>
                刷新
              </el-button>
            </div>
          </template>

          <div v-if="q3Loading" class="chart-loading">
            <el-skeleton animated>
              <template #template>
                <el-skeleton-item variant="rect" style="height: 320px" />
              </template>
            </el-skeleton>
          </div>
          <div v-else-if="q3Error" class="chart-error">
            <el-empty :description="q3Error" />
          </div>
          <div v-else id="q3-chart" class="chart-container" style="height: 340px" />
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onUnmounted, nextTick } from 'vue'
import type { ECharts } from 'echarts'
import { fetchUploaderAvgSize, fetchTopTags, fetchPlatformApproval, ApiError } from '@/services/assetService'
import type { UploaderAvgSize, TopTag, PlatformApproval } from '@/types/asset'
import { formatFileSize } from '@/utils/formatters'

declare global {
  interface Window {
    echarts: typeof import('echarts')
  }
}

// Q1 state
const q1ChartRef = ref<HTMLDivElement | null>(null)
const q1Loading = ref(false)
const q1Error = ref('')
let q1Chart: ECharts | null = null

async function loadQ1() {
  // 刷新时容器会因 v-if 重建，必须销毁旧实例避免绑定到失效 DOM
  if (q1Chart) {
    q1Chart.dispose()
    q1Chart = null
  }
  q1Loading.value = true
  q1Error.value = ''
  try {
    const data: UploaderAvgSize[] = await fetchUploaderAvgSize()
    console.log('[Q1] Raw data:', data)
    const sorted = [...data].sort((a, b) => {
      const aVal = Number(a.avgSizeBytes ?? 0)
      const bVal = Number(b.avgSizeBytes ?? 0)
      return bVal - aVal
    }).slice(0, 10)
    console.log('[Q1] Sorted data:', sorted)
    q1Loading.value = false
    await nextTick()
    renderQ1Chart(sorted)
  } catch (error) {
    console.error('[Q1] Error:', error)
    q1Error.value = error instanceof ApiError ? error.message : '加载失败'
  } finally {
    if (q1Loading.value) q1Loading.value = false
  }
}

function renderQ1Chart(data: UploaderAvgSize[]) {
  console.log('[Q1] renderQ1Chart called with data:', data)
  
  // Use querySelector instead of getElementById
  const container = document.querySelector('#q1-chart') as HTMLElement | null
  console.log('[Q1] container:', container)
  
  if (!container) {
    console.log('[Q1] container not found, returning')
    return
  }

  try {
    if (!q1Chart) {
      console.log('[Q1] Creating new echarts instance')
      q1Chart = window.echarts.init(container)
      console.log('[Q1] echarts instance created:', q1Chart)
    }

    const uploaders = data.map((d) => String(d.uploader ?? '未知'))
    const sizes = data.map((d) => Number(d.avgSizeBytes ?? 0))
    
    console.log('[Q1] Setting option with:', { uploaders, sizes })
    
    q1Chart.setOption({
      title: { text: '各上传人平均文件大小', left: 'center' },
      tooltip: { trigger: 'axis' },
      xAxis: { type: 'category', data: uploaders },
      yAxis: { type: 'value' },
      series: [{ data: sizes, type: 'bar' }]
    })
    
    console.log('[Q1] setOption called successfully')
    console.log('[Q1] container now has canvas:', container.querySelector('canvas') !== null)
  } catch (e) {
    console.error('[Q1] Error:', e)
  }
}

// Q2 state
const q2ChartRef = ref<HTMLDivElement | null>(null)
const q2Loading = ref(false)
const q2Error = ref('')
let q2Chart: ECharts | null = null

async function loadQ2() {
  if (q2Chart) {
    q2Chart.dispose()
    q2Chart = null
  }
  q2Loading.value = true
  q2Error.value = ''
  try {
    const data: TopTag[] = await fetchTopTags(5)
    console.log('[Q2] Raw data:', data)
    q2Loading.value = false
    await nextTick()
    renderQ2Chart(data)
  } catch (error) {
    console.error('[Q2] Error:', error)
    q2Error.value = error instanceof ApiError ? error.message : '加载失败'
  } finally {
    if (q2Loading.value) q2Loading.value = false
  }
}

function renderQ2Chart(data: TopTag[]) {
  console.log('[Q2] renderQ2Chart called with:', data)
  const container = document.querySelector('#q2-chart') as HTMLElement | null
  if (!container) return

  if (!q2Chart) {
    console.log('[Q2] Creating new echarts instance')
    q2Chart = window.echarts.init(container)
  }

  const pieData = data.map((d) => ({
    name: String(d.tag ?? '未知'),
    value: Number(d.count ?? 0),
  }))

  q2Chart.setOption({
    tooltip: {
      trigger: 'item',
      formatter: '{b}: {c} ({d}%)',
    },
    legend: {
      orient: 'vertical',
      right: 20,
      top: 'center',
    },
    series: [
      {
        name: '标签分布',
        type: 'pie',
        radius: ['40%', '70%'],
        center: ['40%', '50%'],
        avoidLabelOverlap: false,
        itemStyle: { borderRadius: 6, borderColor: '#fff', borderWidth: 2 },
        label: {
          show: true,
          formatter: '{b}\n{d}%',
          fontSize: 12,
        },
        emphasis: {
          label: { show: true, fontSize: 14, fontWeight: 'bold' },
        },
        data: pieData,
      },
    ],
    color: ['#1890ff', '#52c41a', '#faad14', '#f5222d', '#722ed1'],
  })
}

// Q3

const q3ChartRef = ref<HTMLDivElement | null>(null)
const q3Loading = ref(false)
const q3Error = ref('')
let q3Chart: ECharts | null = null

async function loadQ3() {
  if (q3Chart) {
    q3Chart.dispose()
    q3Chart = null
  }
  q3Loading.value = true
  q3Error.value = ''
  try {
    const data: PlatformApproval[] = await fetchPlatformApproval()
    console.log('[Q3] Raw data:', data)
    q3Loading.value = false
    await nextTick()
    renderQ3Chart(data)
  } catch (error) {
    console.error('[Q3] Error:', error)
    q3Error.value = error instanceof ApiError ? error.message : '加载失败'
  } finally {
    if (q3Loading.value) q3Loading.value = false
  }
}

function renderQ3Chart(data: PlatformApproval[]) {
  console.log('[Q3] renderQ3Chart called with:', data)
  const container = document.querySelector('#q3-chart') as HTMLElement | null
  if (!container) return

  if (!q3Chart) {
    console.log('[Q3] Creating new echarts instance')
    q3Chart = window.echarts.init(container)
  }

  const platforms = data.map((d) => String(d.platform ?? '未知'))
  const totals = data.map((d) => Number(d.total ?? 0))
  const approveds = data.map((d) => Number(d.approved ?? 0))
  const rates = data.map((d) => parseFloat(Number(d.approvalRate ?? 0).toFixed(2)))

  q3Chart.setOption({
    tooltip: {
      trigger: 'axis',
      axisPointer: { type: 'shadow' },
      formatter: (params: unknown[]) => {
        const ps = params as { seriesName: string; value: number; name: string }[]
        const name = ps[0]?.name || ''
        const lines = ps.map((p) => {
          if (p.seriesName === '通过率') return `${p.seriesName}：${p.value}%`
          return `${p.seriesName}：${p.value}`
        })
        return [name, ...lines].join('<br/>')
      },
    },
    legend: { top: 8 },
    grid: { left: '3%', right: '60px', bottom: '15%', containLabel: true },
    xAxis: {
      type: 'category',
      data: platforms,
      axisLabel: { rotate: 20, interval: 0, fontSize: 12 },
    },
    yAxis: [
      {
        type: 'value',
        name: '素材数量',
        position: 'left',
      },
      {
        type: 'value',
        name: '通过率(%)',
        position: 'right',
        max: 100,
        axisLabel: { formatter: '{value}%' },
      },
    ],
    series: [
      {
        name: '总数',
        type: 'bar',
        data: totals,
        itemStyle: { color: '#69c0ff', borderRadius: [4, 4, 0, 0] },
        barMaxWidth: 40,
      },
      {
        name: '通过数',
        type: 'bar',
        data: approveds,
        itemStyle: { color: '#52c41a', borderRadius: [4, 4, 0, 0] },
        barMaxWidth: 40,
      },
      {
        name: '通过率',
        type: 'line',
        yAxisIndex: 1,
        data: rates,
        smooth: true,
        lineStyle: { color: '#f5222d', width: 2 },
        itemStyle: { color: '#f5222d' },
        label: {
          show: true,
          formatter: '{c}%',
          fontSize: 11,
          color: '#f5222d',
        },
      },
    ],
  })
}

function handleResize() {
  q1Chart?.resize()
  q2Chart?.resize()
  q3Chart?.resize()
}

onMounted(() => {
  console.log('[onMounted] Waiting for DOM...')
  setTimeout(() => {
    setTimeout(() => {
      console.log('[onMounted] Double wait done, loading charts...')
      loadQ1()
      loadQ2()
      loadQ3()
    }, 200)
  }, 100)
  window.addEventListener('resize', handleResize)
})

onUnmounted(() => {
  window.removeEventListener('resize', handleResize)
  q1Chart?.dispose()
  q2Chart?.dispose()
  q3Chart?.dispose()
})
</script>

<style scoped>
.dashboard {
  max-width: 1400px;
}

.page-title {
  display: flex;
  align-items: baseline;
  gap: 12px;
  margin-bottom: 20px;
}

.page-title h2 {
  font-size: 20px;
  font-weight: 600;
  color: #333;
}

.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.card-title {
  font-weight: 600;
  color: #333;
}

.chart-loading,
.chart-error {
  height: 340px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.chart-container {
  width: 100%;
}
</style>
