<template>
  <!-- 稀疏字段选择器组件 -->
  <el-popover
    placement="bottom-end"
    :width="320"
    trigger="click"
    popper-class="field-selector-popover"
  >
    <template #reference>
      <el-button size="small">
        <el-icon><Setting /></el-icon>
        字段选择 ({{ localSelected.length }})
      </el-button>
    </template>

    <div class="field-selector-content">
      <div class="field-selector-header">
        <span class="field-selector-title">选择展示字段</span>
        <div class="field-selector-actions">
          <el-button text size="small" @click="selectAll">全选</el-button>
          <el-button text size="small" @click="selectDefault">默认</el-button>
          <el-button text size="small" @click="clearAll">清空</el-button>
        </div>
      </div>

      <el-divider style="margin: 8px 0" />

      <div class="field-list">
        <el-checkbox-group v-model="localSelected" @change="handleChange">
          <el-checkbox
            v-for="field in allFields"
            :key="field"
            :label="field"
            :value="field"
            class="field-checkbox"
          >
            {{ fieldLabels[field as AssetFieldKey] || field }}
          </el-checkbox>
        </el-checkbox-group>
      </div>

      <el-divider style="margin: 8px 0" />

      <div class="field-selector-footer">
        <el-text size="small" type="info">
          已选 {{ localSelected.length }} / {{ allFields.length }} 个字段
        </el-text>
        <el-button type="primary" size="small" @click="handleApply">
          应用
        </el-button>
      </div>
    </div>
  </el-popover>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue'
import { ALL_ASSET_FIELDS, FIELD_LABELS, type AssetFieldKey } from '@/types/asset'

const props = defineProps<{
  modelValue: string[]
}>()

const emit = defineEmits<{
  (e: 'update:modelValue', value: string[]): void
  (e: 'change', value: string[]): void
}>()

// 所有可选字段
const allFields = ALL_ASSET_FIELDS as readonly string[]
const fieldLabels = FIELD_LABELS

// 默认展示字段
const DEFAULT_FIELDS = ['id', 'title', 'uploader', 'uploadedAt', 'fileSizeBytes', 'status', 'tags', 'platform']

// 本地选中状态
const localSelected = ref<string[]>([...props.modelValue])

watch(
  () => props.modelValue,
  (val) => {
    localSelected.value = [...val]
  },
)

function handleChange() {
  // 不立即触发，等待"应用"按钮
}

function handleApply() {
  emit('update:modelValue', [...localSelected.value])
  emit('change', [...localSelected.value])
}

function selectAll() {
  localSelected.value = [...allFields]
}

function selectDefault() {
  localSelected.value = [...DEFAULT_FIELDS]
}

function clearAll() {
  localSelected.value = []
}

// 初始化：如果 modelValue 为空，使用默认字段
if (props.modelValue.length === 0) {
  localSelected.value = [...DEFAULT_FIELDS]
}
</script>

<style scoped>
.field-selector-content {
  padding: 4px 0;
}

.field-selector-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 4px;
}

.field-selector-title {
  font-weight: 600;
  font-size: 14px;
  color: #333;
}

.field-selector-actions {
  display: flex;
  gap: 2px;
}

.field-list {
  max-height: 300px;
  overflow-y: auto;
  padding: 0 4px;
}

.field-list :deep(.el-checkbox-group) {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 4px;
}

.field-checkbox {
  margin-right: 0 !important;
  padding: 4px 0;
}

.field-selector-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 4px;
}
</style>
