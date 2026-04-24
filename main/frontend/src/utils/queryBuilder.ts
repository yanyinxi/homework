/**
 * queryBuilder.ts — UI 状态 → 后端 DSL 查询参数序列化工具
 *
 * 这是前后端契约的唯一来源。
 * 后端 QueryDslParser 支持的语法：
 *   field=value                 → 等于（eq）
 *   field[eq]=value             → 等于
 *   field[ne]=value             → 不等于
 *   field[gt/gte/lt/lte]=value  → 范围比较
 *   field[in]=a,b,c             → IN 枚举
 *   field[like]=value           → ILIKE 模糊匹配
 *   tags[has]=tag               → 数组包含
 *   sort=field:dir,field2:dir   → 排序（多字段逗号分隔）
 *   fields=a,b,c                → 稀疏字段集
 *   page=N&page_size=N          → 分页
 */

import type { FilterState, SortState, PaginationState } from '@/types/asset'

/** 完整的查询 UI 状态 */
export interface QueryState {
  filters: FilterState
  sort: SortState | null
  fields: string[]          // 空数组表示返回所有字段
  pagination: PaginationState
}

/**
 * 把 UI 状态序列化成 URLSearchParams（可直接传给 axios params）
 *
 * 示例：
 *   { status: 'approved', fileSizeMax: 524288000 }
 *   → { status: 'approved', 'file_size_bytes[lte]': '524288000' }
 */
export function buildQueryParams(state: QueryState): Record<string, string> {
  const params: Record<string, string> = {}

  // --- 过滤条件 ---
  const { filters } = state

  // status 等值过滤
  if (filters.status) {
    params['status'] = filters.status
  }

  // uploader 模糊匹配
  if (filters.uploader.trim()) {
    params['uploader[like]'] = filters.uploader.trim()
  }

  // 标签数组包含过滤
  if (filters.tag.trim()) {
    params['tags[has]'] = filters.tag.trim()
  }

  // 文件大小范围（bytes）
  if (filters.fileSizeMin !== null && filters.fileSizeMin > 0) {
    params['file_size_bytes[gte]'] = String(filters.fileSizeMin)
  }
  if (filters.fileSizeMax !== null && filters.fileSizeMax > 0) {
    params['file_size_bytes[lte]'] = String(filters.fileSizeMax)
  }

  // 平台等值过滤
  if (filters.platform.trim()) {
    params['platform'] = filters.platform.trim()
  }

  // --- 排序 ---
  if (state.sort) {
    params['sort'] = `${camelToSnake(state.sort.field)}:${state.sort.direction}`
  }

  // --- 稀疏字段集 ---
  // 字段名需要转换为后端 snake_case 格式
  if (state.fields.length > 0) {
    params['fields'] = state.fields.map(camelToSnake).join(',')
  }

  // --- 分页 ---
  params['page'] = String(state.pagination.page)
  params['page_size'] = String(state.pagination.pageSize)

  return params
}

/**
 * 为详情页构建稀疏字段集查询参数
 *
 * 示例：
 *   ['title', 'status', 'uploader'] → { fields: 'title,status,uploader' }
 */
export function buildDetailParams(fields: string[]): Record<string, string> {
  if (fields.length === 0) return {}
  return { fields: fields.map(camelToSnake).join(',') }
}

/**
 * 将 camelCase 字段名转换为 snake_case（前端 → 后端字段名映射）
 *
 * 示例：
 *   fileSizeBytes → file_size_bytes
 *   uploadedAt   → uploaded_at
 *   sourceId     → source_id
 */
export function camelToSnake(str: string): string {
  return str.replace(/[A-Z]/g, (letter) => `_${letter.toLowerCase()}`)
}

/**
 * 将 snake_case 字段名转换为 camelCase（后端 → 前端字段名映射）
 *
 * 注意：后端返回的 Map<String, Object> key 已经是 snake_case，
 * 在展示时可能需要此转换。
 */
export function snakeToCamel(str: string): string {
  return str.replace(/_([a-z])/g, (_, letter) => letter.toUpperCase())
}

/** 默认的初始查询状态 */
export function createDefaultQueryState(): QueryState {
  return {
    filters: {
      status: '',
      uploader: '',
      tag: '',
      fileSizeMin: null,
      fileSizeMax: null,
      platform: '',
    },
    sort: { field: 'uploadedAt', direction: 'desc' },
    fields: [],
    pagination: { page: 1, pageSize: 20 },
  }
}
