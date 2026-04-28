<template>
  <div class="asset-detail">
    <!-- 面包屑导航 -->
    <el-breadcrumb separator="/" class="breadcrumb">
      <el-breadcrumb-item :to="{ path: '/' }">首页</el-breadcrumb-item>
      <el-breadcrumb-item :to="{ path: '/assets' }">素材列表</el-breadcrumb-item>
      <el-breadcrumb-item>素材详情</el-breadcrumb-item>
    </el-breadcrumb>

    <!-- 顶部操作栏 -->
    <div class="detail-toolbar">
      <div class="toolbar-left">
        <el-button @click="$router.back()">
          <el-icon><ArrowLeft /></el-icon>
          返回列表
        </el-button>
      </div>
      <div class="toolbar-right">
        <!-- 精简/完整视图切换（通过 ?fields= 开关） -->
        <el-switch
          v-model="compactMode"
          active-text="精简视图"
          inactive-text="完整视图"
          @change="handleModeChange"
        />
      </div>
    </div>

    <!-- 加载骨架屏 -->
    <el-card v-if="store.detailLoading" shadow="never">
      <el-skeleton :rows="8" animated />
    </el-card>

    <!-- 素材详情内容 -->
    <template v-else-if="store.currentAsset">
      <!-- 标题卡片 -->
      <el-card shadow="never" class="title-card">
        <div class="asset-header">
          <div class="asset-main-info">
            <h2 class="asset-title">{{ asset.title || '未命名素材' }}</h2>
            <div class="asset-meta">
              <el-tag
                v-if="asset.status"
                :type="STATUS_TAG_TYPES[asset.status as AssetStatus] || ''"
                size="default"
                effect="dark"
              >
                {{ STATUS_LABELS[asset.status as AssetStatus] || asset.status }}
              </el-tag>
              <el-text type="info" size="small">ID: {{ asset.id }}</el-text>
            </div>
          </div>
          <div class="asset-quick-stats">
            <div class="quick-stat">
              <span class="stat-label">文件大小</span>
              <span class="stat-value">{{ formatFileSize(asset.fileSizeBytes) }}</span>
            </div>
            <div class="quick-stat">
              <span class="stat-label">上传人</span>
              <span class="stat-value">{{ asset.uploader || '-' }}</span>
            </div>
            <div class="quick-stat">
              <span class="stat-label">上传时间</span>
              <span class="stat-value">{{ formatDateTime(asset.uploadedAt) }}</span>
            </div>
          </div>
        </div>
      </el-card>

      <!-- 基本信息 -->
      <el-row :gutter="16" style="margin-top: 16px">
        <el-col :xs="24" :sm="24" :md="16">
          <el-card shadow="never" class="info-card">
            <template #header>
              <span class="section-title">基本信息</span>
            </template>
            <el-descriptions :column="2" border size="small">
              <el-descriptions-item label="标题">
                {{ asset.title || '-' }}
              </el-descriptions-item>
              <el-descriptions-item label="审核状态">
                <el-tag
                  v-if="asset.status"
                  :type="STATUS_TAG_TYPES[asset.status as AssetStatus] || ''"
                  size="small"
                >
                  {{ STATUS_LABELS[asset.status as AssetStatus] || asset.status }}
                </el-tag>
                <span v-else>-</span>
              </el-descriptions-item>
              <el-descriptions-item label="上传人">{{ asset.uploader || '-' }}</el-descriptions-item>
              <el-descriptions-item label="审核人">{{ asset.reviewer || '-' }}</el-descriptions-item>
              <el-descriptions-item label="上传时间">
                {{ formatDateTime(asset.uploadedAt) }}
              </el-descriptions-item>
              <el-descriptions-item label="入库时间">
                {{ formatDateTime(asset.ingestedAt) }}
              </el-descriptions-item>
              <el-descriptions-item label="投放平台">{{ asset.platform || '-' }}</el-descriptions-item>
              <el-descriptions-item label="城市">{{ asset.city || '-' }}</el-descriptions-item>
              <el-descriptions-item label="标签" :span="2">
                <div class="tag-list">
                  <el-tag
                    v-for="tag in (asset.tags || [])"
                    :key="tag"
                    size="small"
                    type="info"
                    effect="plain"
                  >
                    {{ tag }}
                  </el-tag>
                  <span v-if="!asset.tags || asset.tags.length === 0">-</span>
                </div>
              </el-descriptions-item>
              <el-descriptions-item v-if="asset.remark" label="备注" :span="2">
                {{ asset.remark }}
              </el-descriptions-item>
            </el-descriptions>
          </el-card>
        </el-col>

        <el-col :xs="24" :sm="24" :md="8">
          <el-card shadow="never" class="info-card">
            <template #header>
              <span class="section-title">技术参数</span>
            </template>
            <el-descriptions :column="1" border size="small">
              <el-descriptions-item label="文件大小">
                <el-tooltip :content="`${asset.fileSizeBytes ?? 0} bytes`" placement="top">
                  <span>{{ formatFileSize(asset.fileSizeBytes) }}</span>
                </el-tooltip>
              </el-descriptions-item>
              <el-descriptions-item label="分辨率">{{ asset.resolution || '-' }}</el-descriptions-item>
              <el-descriptions-item label="时长">
                {{ formatDuration(asset.durationSec) }}
              </el-descriptions-item>
            </el-descriptions>
          </el-card>

          <!-- 数据溯源（精简模式下隐藏） -->
          <el-card v-if="!compactMode" shadow="never" class="info-card" style="margin-top: 16px">
            <template #header>
              <span class="section-title">数据溯源</span>
            </template>
            <el-descriptions :column="1" border size="small">
              <el-descriptions-item label="数据集">
                数据集 {{ asset.sourceDataset ?? '-' }}
              </el-descriptions-item>
              <el-descriptions-item label="原始ID">
                {{ asset.sourceId ?? '-' }}
              </el-descriptions-item>
            </el-descriptions>
          </el-card>
        </el-col>
      </el-row>

      <!-- 扩展数据（精简模式下隐藏） -->
      <template v-if="!compactMode">
        <el-card
          v-if="asset.extra"
          shadow="never"
          class="info-card"
          style="margin-top: 16px"
        >
          <template #header>
            <span class="section-title">扩展数据（extra）</span>
          </template>
          <el-input
            :model-value="formatJson(asset.extra)"
            type="textarea"
            :rows="6"
            readonly
            resize="none"
          />
        </el-card>
      </template>
    </template>

    <!-- 加载失败 / 未找到 -->
    <el-card v-else shadow="never">
      <el-empty description="素材不存在或加载失败">
        <el-button type="primary" @click="$router.push('/assets')">返回列表</el-button>
      </el-empty>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAssetStore } from '@/stores/assetStore'
import type { AssetStatus, AssetSparse } from '@/types/asset'
import { STATUS_LABELS, STATUS_TAG_TYPES } from '@/types/asset'
import { formatFileSize, formatDateTime, formatDuration } from '@/utils/formatters'

const route = useRoute()
const router = useRouter()
const store = useAssetStore()

// 当前素材 ID（来自路由参数）
const assetId = computed(() => String(route.params.id))

// 精简/完整视图模式（对应 ?fields= 开关）
const compactMode = ref(route.query.compact === '1')

// 精简模式展示的字段（不包含溯源/extra）
const COMPACT_FIELDS = [
  'id', 'title', 'uploader', 'status', 'uploadedAt',
  'fileSizeBytes', 'tags', 'platform', 'city',
]

// 便捷访问当前素材
const asset = computed<AssetSparse>(() => {
  return store.currentAsset ?? { id: '' }
})

async function loadAsset() {
  const fields = compactMode.value ? COMPACT_FIELDS : []
  await store.fetchAssetById(assetId.value, fields)
}

function handleModeChange(compact: boolean) {
  // 更新 URL query 参数，保持可分享
  router.replace({
    query: { ...route.query, compact: compact ? '1' : undefined },
  })
  loadAsset()
}

function formatJson(obj: unknown): string {
  try {
    return JSON.stringify(obj, null, 2)
  } catch {
    return String(obj)
  }
}

onMounted(() => {
  loadAsset()
})

// 路由 id 变化时重新加载
watch(assetId, () => {
  loadAsset()
})
</script>

<style scoped>
.asset-detail {
  max-width: 1200px;
}

.breadcrumb {
  margin-bottom: 16px;
}

.detail-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16px;
}

.toolbar-left,
.toolbar-right {
  display: flex;
  align-items: center;
  gap: 12px;
}

/* 标题卡片 */
.title-card {
  margin-bottom: 0;
}

.asset-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 24px;
  flex-wrap: wrap;
}

.asset-main-info {
  flex: 1;
  min-width: 280px;
}

.asset-title {
  font-size: 22px;
  font-weight: 600;
  color: #222;
  margin-bottom: 10px;
  line-height: 1.3;
}

.asset-meta {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}

.asset-quick-stats {
  display: flex;
  gap: 32px;
  flex-wrap: wrap;
}

.quick-stat {
  display: flex;
  flex-direction: column;
  gap: 4px;
  text-align: right;
}

.stat-label {
  font-size: 12px;
  color: #999;
}

.stat-value {
  font-size: 14px;
  font-weight: 500;
  color: #333;
}

/* 信息卡片 */
.info-card {
  height: fit-content;
}

.section-title {
  font-size: 14px;
  font-weight: 600;
  color: #333;
}

/* 标签列表 */
.tag-list {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
}
</style>
