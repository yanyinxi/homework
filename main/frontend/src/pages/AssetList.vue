<template>
  <div class="asset-list">
    <!-- 页面标题 + 总数统计 -->
    <div class="page-title">
      <h2>素材列表</h2>
      <el-text type="info" size="small">共 {{ store.total }} 条记录</el-text>
    </div>

    <!-- 过滤条件栏：支持多字段过滤（status、uploader、file_size、tags 等） -->
    <FilterBar
      v-model="filterState"
      @apply="handleFilterApply"
      @reset="handleFilterReset"
    />

    <!-- 工具栏：排序控件 + 字段选择 + 统计信息 -->
    <div class="toolbar">
      <div class="toolbar-left">
        <el-text v-if="!store.listLoading" size="small" type="info">
          已加载 {{ store.assets.length }} / {{ store.total }} 条
        </el-text>
      </div>
      <div class="toolbar-right">
        <!-- 上传按钮 -->
        <el-button type="primary" size="small" @click="uploadDialogVisible = true">
          <el-icon><Upload /></el-icon>
          上传
        </el-button>
        <!-- 独立排序控件（从 AssetList 抽取的 SortControl 组件） -->
        <SortControl
          v-model="sortState"
          @sort-change="handleSortChange"
        />
        <FieldSelector
          v-model="selectedFields"
          @change="handleFieldChange"
        />
        <el-button size="small" @click="handleRefresh">
          <el-icon><Refresh /></el-icon>
          刷新
        </el-button>
      </div>
    </div>

    <!-- 素材数据表格 -->
    <el-card shadow="never">
      <el-table
        v-loading="store.listLoading"
        :data="store.assets"
        stripe
        border
        row-key="id"
        highlight-current-row
        style="width: 100%"
        @row-click="handleRowClick"
      >
        <!-- 动态列：根据 selectedFields 决定显示哪些列 -->

        <el-table-column
          v-if="isFieldVisible('id')"
          prop="id"
          label="ID"
          width="260"
          show-overflow-tooltip
        />

        <el-table-column
          v-if="isFieldVisible('title')"
          prop="title"
          label="标题"
          min-width="180"
          show-overflow-tooltip
        />

        <el-table-column
          v-if="isFieldVisible('uploader')"
          prop="uploader"
          label="上传人"
          width="120"
        />

        <el-table-column
          v-if="isFieldVisible('uploadedAt')"
          label="上传时间"
          width="160"
          prop="uploadedAt"
        >
          <template #default="{ row }">
            {{ formatDateTime(row.uploadedAt) }}
          </template>
        </el-table-column>

        <el-table-column
          v-if="isFieldVisible('fileSizeBytes')"
          label="文件大小"
          width="120"
          prop="fileSizeBytes"
        >
          <template #default="{ row }">
            {{ formatFileSize(row.fileSizeBytes) }}
          </template>
        </el-table-column>

        <el-table-column
          v-if="isFieldVisible('status')"
          label="状态"
          width="100"
          prop="status"
        >
          <template #default="{ row }">
            <el-tag
              v-if="row.status"
              :type="STATUS_TAG_TYPES[row.status as AssetStatus] || ''"
              size="small"
            >
              {{ STATUS_LABELS[row.status as AssetStatus] || row.status }}
            </el-tag>
            <span v-else>-</span>
          </template>
        </el-table-column>

        <el-table-column
          v-if="isFieldVisible('tags')"
          label="标签"
          min-width="160"
          prop="tags"
        >
          <template #default="{ row }">
            <div class="tag-list">
              <el-tag
                v-for="tag in (row.tags || [])"
                :key="tag"
                size="small"
                type="info"
                effect="plain"
                style="margin: 2px"
              >
                {{ tag }}
              </el-tag>
              <span v-if="!row.tags || row.tags.length === 0">-</span>
            </div>
          </template>
        </el-table-column>

        <el-table-column
          v-if="isFieldVisible('platform')"
          prop="platform"
          label="平台"
          width="120"
        />

        <el-table-column
          v-if="isFieldVisible('city')"
          prop="city"
          label="城市"
          width="100"
        />

        <el-table-column
          v-if="isFieldVisible('resolution')"
          prop="resolution"
          label="分辨率"
          width="120"
        />

        <el-table-column
          v-if="isFieldVisible('durationSec')"
          label="时长"
          width="90"
          prop="durationSec"
        >
          <template #default="{ row }">
            {{ formatDuration(row.durationSec) }}
          </template>
        </el-table-column>

        <el-table-column
          v-if="isFieldVisible('reviewer')"
          prop="reviewer"
          label="审核人"
          width="100"
        />

        <el-table-column
          v-if="isFieldVisible('sourceId')"
          prop="sourceId"
          label="原始ID"
          width="120"
          show-overflow-tooltip
        />

        <el-table-column
          v-if="isFieldVisible('sourceDataset')"
          label="数据集"
          width="90"
          prop="sourceDataset"
        >
          <template #default="{ row }">
            数据集 {{ row.sourceDataset }}
          </template>
        </el-table-column>

        <el-table-column
          v-if="isFieldVisible('ingestedAt')"
          label="入库时间"
          width="160"
          prop="ingestedAt"
        >
          <template #default="{ row }">
            {{ formatDateTime(row.ingestedAt) }}
          </template>
        </el-table-column>

        <!-- 操作列 -->
        <el-table-column label="操作" width="120" fixed="right">
          <template #default="{ row }">
            <el-button
              type="primary"
              link
              size="small"
              @click.stop="handleViewDetail(row.id)"
            >
              详情
            </el-button>
            <el-button
              type="danger"
              link
              size="small"
              @click.stop="handleDelete(row.id)"
            >
              删除
            </el-button>
          </template>
        </el-table-column>

        <!-- 空数据提示 -->
        <template #empty>
          <el-empty description="暂无数据，请调整过滤条件" />
        </template>
      </el-table>

      <!-- 分页器 -->
      <div class="pagination-wrapper">
        <el-pagination
          v-model:current-page="currentPage"
          v-model:page-size="pageSize"
          :total="store.total"
          :page-sizes="[10, 20, 50, 100]"
          layout="total, sizes, prev, pager, next, jumper"
          background
          @size-change="handlePageSizeChange"
          @current-change="handlePageChange"
        />
      </div>
    </el-card>

    <!-- 上传对话框 -->
    <el-dialog
      v-model="uploadDialogVisible"
      title="上传素材"
      width="450px"
      :close-on-click-modal="false"
    >
      <el-form label-width="60px">
        <el-form-item label="文件">
          <el-upload
            ref="uploadRef"
            :auto-upload="false"
            :limit="1"
            accept=".xls,.xlsx"
            :on-change="handleFileChange"
            :on-remove="handleFileRemove"
            drag
          >
            <el-icon size="48" color="#c0c4cc"><UploadFilled /></el-icon>
            <div class="el-upload__text">拖拽文件到此处，或 <em>点击选择</em></div>
            <template #tip>
              <div class="el-upload__tip">支持 .xls / .xlsx 格式，系统将自动识别数据集格式</div>
            </template>
          </el-upload>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="uploadDialogVisible = false">取消</el-button>
        <el-button
          type="primary"
          :loading="store.uploadLoading"
          :disabled="!uploadFile"
          @click="handleUpload"
        >
          确认上传
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, watch } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import FilterBar from '@/components/FilterBar.vue'
import FieldSelector from '@/components/FieldSelector.vue'
import SortControl from '@/components/SortControl.vue'
import { useAssetStore } from '@/stores/assetStore'
import type { FilterState, AssetStatus, SortState } from '@/types/asset'
import { STATUS_LABELS, STATUS_TAG_TYPES } from '@/types/asset'
import { formatFileSize, formatDateTime, formatDuration } from '@/utils/formatters'
import { Upload, UploadFilled } from '@element-plus/icons-vue'
import { ApiError } from '@/services/assetService'

const router = useRouter()
const route = useRoute()
const store = useAssetStore()

// ------------------- URL Query 双向绑定 -------------------

/**
 * 从 URL query params 读取初始状态并同步到 store。
 * 刷新后状态不丢失。
 */
function restoreFromQuery() {
  const q = route.query

  // 过滤状态
  const filters: Partial<FilterState> = {}
  if (typeof q.status === 'string' && q.status) {
    filters.status = q.status as FilterState['status']
  }
  if (typeof q.uploader === 'string') {
    filters.uploader = q.uploader
  }
  if (typeof q.tag === 'string') {
    filters.tag = q.tag
  }
  if (typeof q.fileSizeMin === 'string' && q.fileSizeMin) {
    filters.fileSizeMin = Number(q.fileSizeMin)
  }
  if (typeof q.fileSizeMax === 'string' && q.fileSizeMax) {
    filters.fileSizeMax = Number(q.fileSizeMax)
  }
  if (typeof q.platform === 'string') {
    filters.platform = q.platform
  }
  if (Object.keys(filters).length > 0) {
    Object.assign(store.queryState.filters, filters)
    Object.assign(filterState.value, store.queryState.filters)
  }

  // 排序
  if (typeof q.sort === 'string' && q.sort) {
    const [field, direction] = q.sort.split(':')
    if (field) {
      const sort: SortState = {
        field,
        direction: direction === 'asc' ? 'asc' : 'desc',
      }
      store.queryState.sort = sort
      sortState.value = sort
    }
  }

  // 分页
  if (typeof q.page === 'string' && q.page) {
    store.queryState.pagination.page = Math.max(1, Number(q.page))
  }
  if (typeof q.pageSize === 'string' && q.pageSize) {
    store.queryState.pagination.pageSize = Math.min(200, Math.max(10, Number(q.pageSize)))
  }
}

/**
 * 将当前过滤/排序/分页状态同步写入 URL query params。
 * 使用 replace 避免每次过滤变化产生大量历史记录。
 */
function syncToQuery() {
  const q: Record<string, string> = {}
  const { filters, sort, pagination } = store.queryState

  if (filters.status) q.status = filters.status
  if (filters.uploader) q.uploader = filters.uploader
  if (filters.tag) q.tag = filters.tag
  if (filters.fileSizeMin != null && filters.fileSizeMin > 0) {
    q.fileSizeMin = String(filters.fileSizeMin)
  }
  if (filters.fileSizeMax != null && filters.fileSizeMax > 0) {
    q.fileSizeMax = String(filters.fileSizeMax)
  }
  if (filters.platform) q.platform = filters.platform
  if (sort) q.sort = `${sort.field}:${sort.direction}`
  if (pagination.page > 1) q.page = String(pagination.page)
  if (pagination.pageSize !== 20) q.pageSize = String(pagination.pageSize)

  router.replace({ query: q })
}

// ------------------- 过滤状态 -------------------

const filterState = ref<FilterState>({ ...store.queryState.filters })

function handleFilterApply(filters: FilterState) {
  store.applyFilters(filters)
  syncToQuery()
}

function handleFilterReset() {
  store.resetFilters()
  filterState.value = { ...store.queryState.filters }
  syncToQuery()
}

// ------------------- 排序状态（使用 SortControl 组件） -------------------

const sortState = ref<SortState | null>(store.queryState.sort)

function handleSortChange(sort: SortState | null) {
  sortState.value = sort
  store.applySort(sort)
  syncToQuery()
}

// ------------------- 字段选择 -------------------

const selectedFields = ref<string[]>([...store.selectedFields])

function isFieldVisible(field: string): boolean {
  if (selectedFields.value.length === 0) return true
  return selectedFields.value.includes(field)
}

function handleFieldChange(fields: string[]) {
  selectedFields.value = fields
  store.applyFields(fields)
}

// ------------------- 分页 -------------------

const currentPage = computed({
  get: () => store.queryState.pagination.page,
  set: (val) => {
    store.applyPagination({ page: val })
    syncToQuery()
  },
})

const pageSize = computed({
  get: () => store.queryState.pagination.pageSize,
  set: (val) => {
    store.applyPagination({ pageSize: val, page: 1 })
    syncToQuery()
  },
})

function handlePageChange(page: number) {
  store.applyPagination({ page })
  syncToQuery()
}

function handlePageSizeChange(size: number) {
  store.applyPagination({ pageSize: size, page: 1 })
  syncToQuery()
}

// ------------------- 行操作 -------------------

function handleRowClick(row: { id: string }) {
  router.push({ name: 'AssetDetail', params: { id: row.id } })
}

function handleViewDetail(id: string) {
  router.push({ name: 'AssetDetail', params: { id } })
}

function handleRefresh() {
  store.fetchAssets()
}

// ------------------- 上传功能 -------------------

const uploadDialogVisible = ref(false)
const uploadFile = ref<File | null>(null)

function handleFileChange(file: { raw: File }) {
  uploadFile.value = file.raw
}

function handleFileRemove() {
  uploadFile.value = null
}

async function handleUpload() {
  if (!uploadFile.value) return
  try {
    await store.uploadExcel(uploadFile.value)
    uploadDialogVisible.value = false
    uploadFile.value = null
  } catch (error) {
    if (error instanceof ApiError && error.message.includes('Unable to detect dataset format')) {
      ElMessage.warning({
        message: '无法识别文件格式，请联系系统管理员进行数据映射处理',
        duration: 5000,
      })
    }
  }
}

// ------------------- 删除功能 -------------------

async function handleDelete(id: string) {
  try {
    await ElMessageBox.confirm('确定要删除该素材吗？此操作不可恢复。', '删除确认', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning',
    })
    await store.deleteAsset(id)
  } catch {
    // 用户取消或错误已在 store 中处理
  }
}

// ------------------- 初始化（从 URL 恢复状态） -------------------

onMounted(() => {
  // 先从 URL query 恢复状态，再加载数据
  restoreFromQuery()
  store.fetchAssets()
})

// 同步外部过滤状态变化（store 内部更新时同步到本地 ref）
watch(
  () => store.queryState.filters,
  (val) => {
    Object.assign(filterState.value, val)
  },
  { deep: true },
)

// 同步排序状态（store 更新时同步 SortControl）
watch(
  () => store.queryState.sort,
  (val) => {
    sortState.value = val
  },
)
</script>

<style scoped>
.asset-list {
  max-width: 1600px;
}

.page-title {
  display: flex;
  align-items: baseline;
  gap: 12px;
  margin-bottom: 16px;
}

.page-title h2 {
  font-size: 20px;
  font-weight: 600;
  color: #333;
}

.toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
}

.toolbar-right {
  display: flex;
  gap: 8px;
  align-items: center;
}

.tag-list {
  display: flex;
  flex-wrap: wrap;
  gap: 2px;
}

.pagination-wrapper {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
  padding-top: 16px;
  border-top: 1px solid #f0f0f0;
}

/* 行点击高亮样式 */
:deep(.el-table__row) {
  cursor: pointer;
}

:deep(.el-table__row:hover td) {
  background-color: #f0f7ff !important;
}

:deep(.el-upload-dragger) {
  width: 100%;
}
</style>
