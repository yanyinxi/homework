/**
 * 素材管理后台 TypeScript 类型定义
 * 与后端 Asset 实体和 API 响应结构严格对应
 */

// ------------------- 基础类型 -------------------

/** 审核状态枚举 */
export type AssetStatus = 'pending' | 'approved' | 'rejected'

/** 素材完整字段类型（对应 Asset.java entity） */
export interface Asset {
  id: string
  sourceDataset: number
  sourceId: string
  ingestedAt: string      // ISO8601
  title: string
  uploader: string
  uploadedAt: string      // ISO8601
  fileSizeBytes: number
  status: AssetStatus
  tags: string[]
  city: string | null
  platform: string | null
  reviewer: string | null
  remark: string | null
  resolution: string | null
  durationSec: number | null
  extra: Record<string, unknown> | null
  rawRecord: Record<string, unknown> | null
}

/** 后端返回的稀疏字段素材（字段可能缺失） */
export type AssetSparse = Partial<Asset> & { id: string }

// ------------------- API 响应类型 -------------------

/** 统一响应包装：{ code: 0, message: "ok", data: T } */
export interface ApiEnvelope<T> {
  code: number
  message: string
  data: T
}

/** 分页响应 */
export interface PagedResponse<T> {
  items: T[]
  total: number
  page: number
  pageSize: number
}

// ------------------- 统计查询类型（Dashboard） -------------------

/** Q1: 各上传人平均文件大小 */
export interface UploaderAvgSize {
  uploader: string
  avgSizeBytes: number
  avgSizeHuman: string
  approvedCount?: number
}

/** Q2: 标签 Top N 分布 */
export interface TopTag {
  tag: string
  count: number
}

/** Q3: 各平台审核通过率 */
export interface PlatformApproval {
  platform: string
  total: number
  approved: number
  approvalRate: number
}

// ------------------- UI 状态类型 -------------------

/** 过滤器 UI 状态 */
export interface FilterState {
  status: AssetStatus | ''
  uploader: string
  tag: string
  fileSizeMin: number | null    // bytes
  fileSizeMax: number | null    // bytes
  platform: string
}

/** 排序状态：字段名和方向 */
export interface SortState {
  field: string
  direction: 'asc' | 'desc'
}

/** 分页状态 */
export interface PaginationState {
  page: number
  pageSize: number
}

/** 所有可选字段（稀疏字段集） */
export const ALL_ASSET_FIELDS = [
  'id',
  'sourceDataset',
  'sourceId',
  'title',
  'uploader',
  'uploadedAt',
  'fileSizeBytes',
  'status',
  'tags',
  'city',
  'platform',
  'reviewer',
  'remark',
  'resolution',
  'durationSec',
  'ingestedAt',
] as const

export type AssetFieldKey = typeof ALL_ASSET_FIELDS[number]

/** 字段显示标签映射 */
export const FIELD_LABELS: Record<AssetFieldKey, string> = {
  id: 'ID',
  sourceDataset: '数据集',
  sourceId: '原始ID',
  title: '标题',
  uploader: '上传人',
  uploadedAt: '上传时间',
  fileSizeBytes: '文件大小',
  status: '状态',
  tags: '标签',
  city: '城市',
  platform: '平台',
  reviewer: '审核人',
  remark: '备注',
  resolution: '分辨率',
  durationSec: '时长(秒)',
  ingestedAt: '入库时间',
}

/** 审核状态标签映射 */
export const STATUS_LABELS: Record<AssetStatus, string> = {
  pending: '待审核',
  approved: '已通过',
  rejected: '已拒绝',
}

/** 审核状态颜色映射（Element Plus tag type） */
export const STATUS_TAG_TYPES: Record<AssetStatus, string> = {
  pending: 'warning',
  approved: 'success',
  rejected: 'danger',
}
