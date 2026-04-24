/**
 * formatters.ts — 展示格式化工具函数
 *
 * 文件大小格式化：bytes → KB/MB/GB 人类可读
 * 时间格式化：ISO8601 → 本地时区可读字符串
 */

/**
 * 把 bytes 转换为人类可读格式
 *
 * 示例：
 *   1024 → "1.00 KB"
 *   1048576 → "1.00 MB"
 *   1073741824 → "1.00 GB"
 */
export function formatFileSize(bytes: number | null | undefined): string {
  if (bytes == null) return '-'
  if (bytes === 0) return '0 B'

  const units = ['B', 'KB', 'MB', 'GB', 'TB']
  const i = Math.floor(Math.log(bytes) / Math.log(1024))
  const value = bytes / Math.pow(1024, i)

  // 小于 1 KB 显示整数字节
  if (i === 0) return `${bytes} B`

  return `${value.toFixed(2)} ${units[i]}`
}

/**
 * 格式化 ISO8601 时间字符串为本地时区可读格式
 *
 * 示例：
 *   "2024-01-15T08:30:00Z" → "2024-01-15 16:30:00"（东八区）
 */
export function formatDateTime(isoStr: string | null | undefined): string {
  if (!isoStr) return '-'
  try {
    const date = new Date(isoStr)
    if (isNaN(date.getTime())) return isoStr

    return date.toLocaleString('zh-CN', {
      year: 'numeric',
      month: '2-digit',
      day: '2-digit',
      hour: '2-digit',
      minute: '2-digit',
      second: '2-digit',
      hour12: false,
    }).replace(/\//g, '-')
  } catch {
    return isoStr
  }
}

/**
 * 格式化时长（秒 → 分:秒 或 时:分:秒）
 *
 * 示例：
 *   90 → "01:30"
 *   3661 → "01:01:01"
 */
export function formatDuration(seconds: number | null | undefined): string {
  if (seconds == null) return '-'
  const h = Math.floor(seconds / 3600)
  const m = Math.floor((seconds % 3600) / 60)
  const s = seconds % 60

  const pad = (n: number) => String(n).padStart(2, '0')

  if (h > 0) return `${pad(h)}:${pad(m)}:${pad(s)}`
  return `${pad(m)}:${pad(s)}`
}

/**
 * 格式化通过率为百分比字符串
 *
 * 示例：
 *   0.8543 → "85.43%"
 */
export function formatPercent(rate: number | null | undefined): string {
  if (rate == null) return '-'
  return `${(rate * 100).toFixed(2)}%`
}

/**
 * 把文件大小的人类可读描述转换回 bytes（用于过滤器显示）
 *
 * 常用预设值
 */
export const FILE_SIZE_PRESETS = [
  { label: '1 MB', value: 1024 * 1024 },
  { label: '10 MB', value: 10 * 1024 * 1024 },
  { label: '50 MB', value: 50 * 1024 * 1024 },
  { label: '100 MB', value: 100 * 1024 * 1024 },
  { label: '500 MB', value: 500 * 1024 * 1024 },
  { label: '1 GB', value: 1024 * 1024 * 1024 },
]
