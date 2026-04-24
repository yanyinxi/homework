package com.homework.asset.api.dto;

import com.homework.asset.domain.entity.Asset;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 素材 API 响应 DTO。
 *
 * <p>对应 Asset 实体的全部字段，但排除 raw_record（原始数据不对外暴露）。
 * 使用 record 保持不可变性。
 */
public record AssetDTO(
    /** 全局唯一主键 */
    UUID id,

    /** 数据来源：1=数据集1 / 2=数据集2 / 3=数据集3 */
    Integer sourceDataset,

    /** 原始数据集中的 ID（A0001 / asset_001 / vid0001） */
    String sourceId,

    /** 入库时间 */
    Instant ingestedAt,

    /** 素材标题 */
    String title,

    /** 上传人 */
    String uploader,

    /** 上传时间（归一化为 UTC） */
    Instant uploadedAt,

    /** 文件大小（字节） */
    Long fileSizeBytes,

    /** 审核状态：pending / approved / rejected */
    String status,

    /** 标签数组 */
    List<String> tags,

    /** 所在城市 */
    String city,

    /** 投放平台 canonical code（如 qianchuan），仅数据集2/3 */
    String platform,

    /** 审核人（仅数据集1） */
    String reviewer,

    /** 备注（仅数据集1） */
    String remark,

    /** 分辨率（如 720x1280），仅数据集2 */
    String resolution,

    /** 视频时长（秒），仅数据集3 */
    Integer durationSec,

    /** 其他稀疏字段（JSONB open schema） */
    Object extra) {

  /**
   * 从 Asset 实体转换为 DTO（排除 rawRecord）。
   *
   * @param asset 实体对象
   * @return AssetDTO 响应对象
   */
  public static AssetDTO from(Asset asset) {
    if (asset == null) {
      return null;
    }
    return new AssetDTO(
        asset.getId(),
        asset.getSourceDataset(),
        asset.getSourceId(),
        asset.getIngestedAt(),
        asset.getTitle(),
        asset.getUploader(),
        asset.getUploadedAt(),
        asset.getFileSizeBytes(),
        asset.getStatus(),
        asset.getTags(),
        asset.getCity(),
        asset.getPlatform(),
        asset.getReviewer(),
        asset.getRemark(),
        asset.getResolution(),
        asset.getDurationSec(),
        asset.getExtra());
  }

  /**
   * 从 Map（动态稀疏字段查询结果）转换为 DTO。
   * 仅填充 Map 中存在的字段，其余保持 null。
   *
   * @param map 动态查询结果 Map（key 为 snake_case 列名）
   * @return AssetDTO 响应对象
   */
  @SuppressWarnings("unchecked")
  public static AssetDTO fromMap(Map<String, Object> map) {
    if (map == null) {
      return null;
    }
    return new AssetDTO(
        parseUuid(map.get("id")),
        (Integer) map.get("source_dataset"),
        (String) map.get("source_id"),
        parseInstant(map.get("ingested_at")),
        (String) map.get("title"),
        (String) map.get("uploader"),
        parseInstant(map.get("uploaded_at")),
        parseLong(map.get("file_size_bytes")),
        (String) map.get("status"),
        (List<String>) map.get("tags"),
        (String) map.get("city"),
        (String) map.get("platform"),
        (String) map.get("reviewer"),
        (String) map.get("remark"),
        (String) map.get("resolution"),
        parseInteger(map.get("duration_sec")),
        map.get("extra"));
  }

  // ---- 私有类型转换辅助 ----

  private static UUID parseUuid(Object v) {
    if (v == null) return null;
    if (v instanceof UUID u) return u;
    return UUID.fromString(v.toString());
  }

  private static Instant parseInstant(Object v) {
    if (v == null) return null;
    if (v instanceof Instant i) return i;
    return Instant.parse(v.toString());
  }

  private static Long parseLong(Object v) {
    if (v == null) return null;
    if (v instanceof Long l) return l;
    if (v instanceof Number n) return n.longValue();
    return Long.parseLong(v.toString());
  }

  private static Integer parseInteger(Object v) {
    if (v == null) return null;
    if (v instanceof Integer i) return i;
    if (v instanceof Number n) return n.intValue();
    return Integer.parseInt(v.toString());
  }
}
