<template>
  <!-- 过滤条件栏组件 -->
  <el-card class="filter-bar" shadow="never">
    <el-form ref="formRef" :model="localFilters" :rules="filterRules" inline label-position="top" size="small">
      <!-- 审核状态 -->
      <el-form-item label="审核状态">
        <el-select
          v-model="localFilters.status"
          placeholder="全部"
          clearable
          style="width: 120px"
        >
          <el-option label="待审核" value="pending" />
          <el-option label="已通过" value="approved" />
          <el-option label="已拒绝" value="rejected" />
        </el-select>
      </el-form-item>

      <!-- 上传人模糊搜索 -->
      <el-form-item label="上传人" prop="uploader">
        <el-input
          v-model="localFilters.uploader"
          placeholder="模糊搜索"
          clearable
          style="width: 150px"
          @keyup.enter="handleApply"
        />
      </el-form-item>

      <!-- 标签包含 -->
      <el-form-item label="标签">
        <el-input
          v-model="localFilters.tag"
          placeholder="标签包含"
          clearable
          style="width: 120px"
          @keyup.enter="handleApply"
        />
      </el-form-item>

      <!-- 投放平台 -->
      <el-form-item label="平台">
        <el-input
          v-model="localFilters.platform"
          placeholder="平台名称"
          clearable
          style="width: 120px"
          @keyup.enter="handleApply"
        />
      </el-form-item>

      <!-- 文件大小范围 -->
      <el-form-item label="文件大小（字节）">
        <div class="size-range">
          <el-form-item prop="fileSizeMin" class="size-field-item">
            <el-input-number
              v-model="localFilters.fileSizeMin"
              :min="0"
              placeholder="最小"
              controls-position="right"
              style="width: 130px"
            />
          </el-form-item>
          <span class="range-sep">~</span>
          <el-form-item prop="fileSizeMax" class="size-field-item">
            <el-input-number
              v-model="localFilters.fileSizeMax"
              :min="0"
              placeholder="最大"
              controls-position="right"
              style="width: 130px"
            />
          </el-form-item>
        </div>
      </el-form-item>

      <!-- 操作按钮 -->
      <el-form-item label=" ">
        <div class="filter-actions">
          <el-button type="primary" @click="handleApply">
            <el-icon><Search /></el-icon>
            查询
          </el-button>
          <el-button @click="handleReset">
            <el-icon><Refresh /></el-icon>
            重置
          </el-button>
        </div>
      </el-form-item>
    </el-form>
  </el-card>
</template>

<script setup lang="ts">
import { reactive, ref, watch } from 'vue'
import type { FilterState } from '@/types/asset'
import type { FormInstance, FormRules } from 'element-plus'

const props = defineProps<{
  modelValue: FilterState
}>()

const emit = defineEmits<{
  (e: 'update:modelValue', value: FilterState): void
  (e: 'apply', value: FilterState): void
  (e: 'reset'): void
}>()

// 本地副本，避免直接修改 props
const localFilters = reactive<FilterState>({ ...props.modelValue })

// 表单引用和验证规则
const formRef = ref<FormInstance>()

const validatePositiveNumber = (_rule: unknown, value: unknown, callback: (error?: Error) => void) => {
  if (value === undefined || value === null || value === '') return callback()
  if (typeof value === 'number' && value >= 0) return callback()
  callback(new Error('请输入大于等于 0 的数字'))
}

const validateFileSizeRange = (_rule: unknown, value: unknown, callback: (error?: Error) => void) => {
  if (value === undefined || value === null || value === '') return callback()
  const min = localFilters.fileSizeMin
  if (min !== null && min !== undefined && (value as number) < min) {
    return callback(new Error(`最大值不能小于最小值 ${min}`))
  }
  callback()
}

const filterRules: FormRules = {
  fileSizeMin: [{ validator: validatePositiveNumber, trigger: 'blur' }],
  fileSizeMax: [
    { validator: validatePositiveNumber, trigger: 'blur' },
    { validator: validateFileSizeRange, trigger: 'blur' },
  ],
  uploader: [{ max: 100, message: '上传人不能超过 100 个字符', trigger: 'blur' }],
  title: [{ max: 200, message: '标题不能超过 200 个字符', trigger: 'blur' }],
}

// 当外部 modelValue 变化时同步本地状态
watch(
  () => props.modelValue,
  (newVal) => {
    Object.assign(localFilters, newVal)
  },
  { deep: true },
)

async function handleApply() {
  if (!formRef.value) return
  try {
    await formRef.value.validate()
  } catch {
    // 验证失败，不触发查询
    return
  }
  emit('update:modelValue', { ...localFilters })
  emit('apply', { ...localFilters })
}

function handleReset() {
  // 重置为默认空值
  localFilters.status = ''
  localFilters.uploader = ''
  localFilters.tag = ''
  localFilters.fileSizeMin = null
  localFilters.fileSizeMax = null
  localFilters.platform = ''
  emit('update:modelValue', { ...localFilters })
  emit('reset')
}
</script>

<style scoped>
.filter-bar {
  margin-bottom: 16px;
}

.filter-bar :deep(.el-card__body) {
  padding: 16px 16px 0;
}

.filter-bar :deep(.el-form--inline .el-form-item) {
  margin-right: 16px;
  margin-bottom: 16px;
}

.size-range {
  display: flex;
  align-items: center;
  gap: 8px;
}

.range-sep {
  color: #666;
  flex-shrink: 0;
}

.filter-actions {
  display: flex;
  gap: 8px;
}

.size-field-item {
  margin-bottom: 0;
}

.size-field-item :deep(.el-form-item__label) {
  display: none;
}
</style>
