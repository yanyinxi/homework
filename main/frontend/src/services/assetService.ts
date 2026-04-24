/**
 * assetService.ts — Axios 封装的后端 API 客户端
 *
 * 统一处理：
 * - 响应拦截器：code !== 0 时抛出业务异常
 * - 基础路径：/api/v1
 * - 开发期 proxy 在 vite.config.ts 中配置（/api → http://localhost:8080）
 * - 生产期 proxy 在 nginx.conf 中配置（/api → backend:8080）
 */

import axios, { type AxiosInstance, type AxiosResponse } from 'axios'
import type {
  ApiEnvelope,
  PagedResponse,
  AssetSparse,
  UploaderAvgSize,
  TopTag,
  PlatformApproval,
} from '@/types/asset'

// ------------------- Axios 实例配置 -------------------

const http: AxiosInstance = axios.create({
  baseURL: '/api/v1',
  timeout: 15000,
  headers: {
    'Content-Type': 'application/json',
  },
})

// ------------------- 响应拦截器 -------------------

/** 业务异常（code !== 0）*/
export class ApiError extends Error {
  constructor(
    public readonly code: number,
    message: string,
  ) {
    super(message)
    this.name = 'ApiError'
  }
}

http.interceptors.response.use(
  (response: AxiosResponse<ApiEnvelope<unknown>>) => {
    const envelope = response.data
    // 后端统一 code=0 表示成功，其余表示业务错误
    if (envelope.code !== 0) {
      throw new ApiError(envelope.code, envelope.message || '请求失败')
    }
    return response
  },
  (error) => {
    // 网络错误、超时、4xx/5xx HTTP 错误
    if (error.response) {
      const msg = error.response.data?.message || `HTTP ${error.response.status}`
      throw new ApiError(error.response.status, msg)
    }
    if (error.request) {
      throw new ApiError(0, '网络连接失败，请检查网络或后端服务是否启动')
    }
    throw error
  },
)

// ------------------- 辅助函数 -------------------

/** 从 ApiEnvelope 中取出 data */
function unwrap<T>(response: AxiosResponse<ApiEnvelope<T>>): T {
  return response.data.data
}

// ------------------- 素材 API -------------------

/**
 * 列出素材（支持完整的 DSL 过滤/排序/分页/稀疏字段）
 *
 * @param params - queryBuilder.ts 生成的查询参数对象
 */
export async function listAssets(
  params: Record<string, string>,
): Promise<PagedResponse<AssetSparse>> {
  const response = await http.get<ApiEnvelope<PagedResponse<AssetSparse>>>('/assets', { params })
  return unwrap(response)
}

/**
 * 获取单条素材详情
 *
 * @param id - 素材 UUID
 * @param fields - 稀疏字段集（可选，空则返回全部字段）
 */
export async function getAssetById(
  id: string,
  params?: Record<string, string>,
): Promise<AssetSparse> {
  const response = await http.get<ApiEnvelope<AssetSparse>>(`/assets/${id}`, { params })
  return unwrap(response)
}

// ------------------- 统计 API -------------------

/**
 * Q1：各上传人平均文件大小（已通过素材）
 */
export async function fetchUploaderAvgSize(): Promise<UploaderAvgSize[]> {
  // 后端返回 List<Map<String,Object>>，字段名是 snake_case
  const response = await http.get<ApiEnvelope<UploaderAvgSize[]>>('/stats/uploader-avg-size')
  return unwrap(response)
}

/**
 * Q2：标签 Top N 分布
 *
 * @param limit - 返回数量，默认 5，最大 100
 */
export async function fetchTopTags(limit = 5): Promise<TopTag[]> {
  const response = await http.get<ApiEnvelope<TopTag[]>>('/stats/top-tags', {
    params: { limit },
  })
  return unwrap(response)
}

/**
 * Q3：各平台审核通过率
 */
export async function fetchPlatformApproval(): Promise<PlatformApproval[]> {
  const response = await http.get<ApiEnvelope<PlatformApproval[]>>('/stats/platform-approval')
  return unwrap(response)
}
