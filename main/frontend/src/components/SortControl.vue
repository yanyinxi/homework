<template>
  <div class="sort-control">
    <el-select
      v-model="currentField"
      placeholder="排序字段"
      size="small"
      style="width: 130px"
      clearable
      @change="handleChange"
    >
      <el-option
        v-for="opt in SORT_OPTIONS"
        :key="opt.value"
        :label="opt.label"
        :value="opt.value"
      />
    </el-select>

    <el-button-group v-if="currentField" size="small">
      <el-button
        :type="currentDirection === 'asc' ? 'primary' : 'default'"
        @click="handleDirectionChange('asc')"
      >
        <el-icon><SortUp /></el-icon>
        升序
      </el-button>
      <el-button
        :type="currentDirection === 'desc' ? 'primary' : 'default'"
        @click="handleDirectionChange('desc')"
      >
        <el-icon><SortDown /></el-icon>
        降序
      </el-button>
    </el-button-group>

    <el-button
      v-if="currentField"
      size="small"
      plain
      @click="handleClear"
    >
      清除排序
    </el-button>
  </div>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue'
import { SortUp, SortDown } from '@element-plus/icons-vue'
import type { SortState } from '@/types/asset'

// ---- Props & Emits ----

interface Props {
  /** 当前排序状态，null 表示无排序 */
  modelValue: SortState | null
}

const props = withDefaults(defineProps<Props>(), {
  modelValue: null,
})

const emit = defineEmits<{
  /** 排序状态变更事件，null 表示清除排序 */
  'sort-change': [sort: SortState | null]
  'update:modelValue': [sort: SortState | null]
}>()

// ---- 排序字段选项 ----

const SORT_OPTIONS = [
  { label: '上传时间', value: 'uploadedAt' },
  { label: '文件大小', value: 'fileSizeBytes' },
  { label: '上传人', value: 'uploader' },
  { label: '状态', value: 'status' },
  { label: '入库时间', value: 'ingestedAt' },
] as const

// ---- 内部状态：从 props 初始化 ----

const currentField = ref<string>(props.modelValue?.field ?? '')
const currentDirection = ref<'asc' | 'desc'>(props.modelValue?.direction ?? 'desc')

// 当外部 prop 变化时同步内部状态（支持 URL 恢复场景）
watch(
  () => props.modelValue,
  (val) => {
    currentField.value = val?.field ?? ''
    currentDirection.value = val?.direction ?? 'desc'
  },
)

// ---- 事件处理 ----

function handleChange(field: string) {
  if (!field) {
    emitSort(null)
    return
  }
  emitSort({ field, direction: currentDirection.value })
}

function handleDirectionChange(dir: 'asc' | 'desc') {
  currentDirection.value = dir
  if (currentField.value) {
    emitSort({ field: currentField.value, direction: dir })
  }
}

function handleClear() {
  currentField.value = ''
  emitSort(null)
}

function emitSort(sort: SortState | null) {
  emit('sort-change', sort)
  emit('update:modelValue', sort)
}
</script>

<style scoped>
.sort-control {
  display: flex;
  align-items: center;
  gap: 8px;
}
</style>
