/**
 * assetStore.ts — Pinia 全局状态管理
 *
 * 管理：
 * - 素材列表数据和加载状态
 * - 当前素材详情
 * - 查询状态（过滤/排序/分页/稀疏字段）
 */

import { defineStore } from 'pinia'
import { ref, reactive } from 'vue'
import { ElMessage } from 'element-plus'
import type { AssetSparse, PaginationState, SortState, FilterState } from '@/types/asset'
import type { PagedResponse } from '@/types/asset'
import {
  listAssets,
  getAssetById,
  ApiError,
  uploadExcel as uploadExcelApi,
  deleteAsset as deleteAssetApi,
  deleteBatch as deleteBatchApi,
  type UploadResult,
  type DeleteResult,
} from '@/services/assetService'
import {
  buildQueryParams,
  buildDetailParams,
  createDefaultQueryState,
} from '@/utils/queryBuilder'

export const useAssetStore = defineStore('asset', () => {
  // ------------------- 素材列表状态 -------------------

  /** 当前页的素材列表 */
  const assets = ref<AssetSparse[]>([])

  /** 总记录数（用于分页） */
  const total = ref(0)

  /** 列表加载中标志 */
  const listLoading = ref(false)

  // ------------------- 素材详情状态 -------------------

  /** 当前查看的素材详情 */
  const currentAsset = ref<AssetSparse | null>(null)

  /** 详情加载中标志 */
  const detailLoading = ref(false)

  /** 上传加载中标志 */
  const uploadLoading = ref(false)

  /** 删除加载中标志 */
  const deleteLoading = ref(false)

  // ------------------- 查询状态 -------------------

  const queryState = reactive(createDefaultQueryState())

  // ------------------- 选中的展示字段 -------------------

  /**
   * 列表页展示的字段列表（空数组 = 返回所有字段）
   * 初始值包含常用字段
   */
  const selectedFields = ref<string[]>([
    'id',
    'title',
    'uploader',
    'uploadedAt',
    'fileSizeBytes',
    'status',
    'tags',
    'platform',
  ])

  // ------------------- Actions -------------------

  /**
   * 加载素材列表
   * 使用当前 queryState 构建查询参数
   */
  async function fetchAssets() {
    listLoading.value = true
    try {
      // 把 selectedFields 并入 queryState 的 fields
      const stateWithFields = {
        ...queryState,
        fields: selectedFields.value,
      }
      const params = buildQueryParams(stateWithFields)
      const result: PagedResponse<AssetSparse> = await listAssets(params)
      assets.value = result.items
      total.value = result.total
      // 同步分页状态（后端可能调整过 page）
      queryState.pagination.page = result.page
      queryState.pagination.pageSize = result.pageSize
    } catch (error) {
      handleError(error, '加载素材列表失败')
      assets.value = []
      total.value = 0
    } finally {
      listLoading.value = false
    }
  }

  /**
   * 加载素材详情
   *
   * @param id - 素材 UUID
   * @param fields - 稀疏字段集（空则返回全部）
   */
  async function fetchAssetById(id: string, fields: string[] = []) {
    detailLoading.value = true
    currentAsset.value = null
    try {
      const params = buildDetailParams(fields)
      currentAsset.value = await getAssetById(id, params)
    } catch (error) {
      handleError(error, '加载素材详情失败')
    } finally {
      detailLoading.value = false
    }
  }

  /**
   * 更新过滤条件，重置到第一页并重新加载
   */
  function applyFilters(filters: FilterState) {
    Object.assign(queryState.filters, filters)
    queryState.pagination.page = 1
    fetchAssets()
  }

  /**
   * 更新排序状态并重新加载
   */
  function applySort(sort: SortState | null) {
    queryState.sort = sort
    queryState.pagination.page = 1
    fetchAssets()
  }

  /**
   * 更新分页并重新加载
   */
  function applyPagination(pagination: Partial<PaginationState>) {
    Object.assign(queryState.pagination, pagination)
    fetchAssets()
  }

  /**
   * 更新展示字段并重新加载
   */
  function applyFields(fields: string[]) {
    selectedFields.value = fields
    fetchAssets()
  }

  /**
   * 重置所有过滤条件
   */
  function resetFilters() {
    const defaults = createDefaultQueryState()
    Object.assign(queryState.filters, defaults.filters)
    queryState.pagination.page = 1
    fetchAssets()
  }

  /**
   * 上传 Excel 文件导入素材
   */
  async function uploadExcel(file: File, dataset?: number): Promise<UploadResult> {
    uploadLoading.value = true
    try {
      const result = await uploadExcelApi(file, dataset)
      ElMessage.success(`导入成功：新增 ${result.inserted} 条，更新 ${result.updated} 条`)
      fetchAssets()
      return result
    } catch (error) {
      if (error instanceof ApiError && error.message.includes('Unable to detect dataset format')) {
        throw error
      }
      handleError(error, '上传失败')
      throw error
    } finally {
      uploadLoading.value = false
    }
  }

  /**
   * 删除单条素材
   */
  async function deleteAsset(id: string): Promise<DeleteResult> {
    deleteLoading.value = true
    try {
      const result = await deleteAssetApi(id)
      ElMessage.success(`删除成功：${result.deleted} 条`)
      fetchAssets()
      return result
    } catch (error) {
      handleError(error, '删除失败')
      throw error
    } finally {
      deleteLoading.value = false
    }
  }

  /**
   * 批量删除素材
   */
  async function deleteBatch(ids: string[]): Promise<DeleteResult> {
    deleteLoading.value = true
    try {
      const result = await deleteBatchApi(ids)
      ElMessage.success(`删除成功：${result.deleted} 条`)
      fetchAssets()
      return result
    } catch (error) {
      handleError(error, '批量删除失败')
      throw error
    } finally {
      deleteLoading.value = false
    }
  }

  // ------------------- 错误处理 -------------------

  function handleError(error: unknown, defaultMsg: string) {
    if (error instanceof ApiError) {
      ElMessage.error(`${defaultMsg}：${error.message}`)
    } else {
      ElMessage.error(defaultMsg)
      console.error(error)
    }
  }

  return {
    // 状态
    assets,
    total,
    listLoading,
    currentAsset,
    detailLoading,
    uploadLoading,
    deleteLoading,
    queryState,
    selectedFields,
    // Actions
    fetchAssets,
    fetchAssetById,
    applyFilters,
    applySort,
    applyPagination,
    applyFields,
    resetFilters,
    uploadExcel,
    deleteAsset,
    deleteBatch,
  }
})
