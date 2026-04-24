package com.homework.asset.api.query;

import java.util.Optional;

/**
 * 允许过滤的字段白名单枚举。
 *
 * <p>所有参与过滤的字段必须在此枚举中声明，未声明的字段直接 400，防止 SQL 注入和敏感字段暴露。
 */
public enum FilterableField {
  ID("id", "id"),
  STATUS("status", "status"),
  UPLOADER("uploader", "uploader"),
  CITY("city", "city"),
  PLATFORM("platform", "platform"),
  TITLE("title", "title"),
  FILE_SIZE_BYTES("file_size_bytes", "fileSizeBytes"),
  UPLOADED_AT("uploaded_at", "uploadedAt"),
  TAGS("tags", "tags"),
  REVIEWER("reviewer", "reviewer"),
  REMARK("remark", "remark"),
  RESOLUTION("resolution", "resolution"),
  DURATION_SEC("duration_sec", "durationSec"),
  SOURCE_DATASET("source_dataset", "sourceDataset"),
  SOURCE_ID("source_id", "sourceId"),
  INGESTED_AT("ingested_at", "ingestedAt");

  /** URL 参数名（如 file_size_bytes） */
  private final String paramName;

  /** Java params Map key 前缀（如 fileSizeBytes，后面加 Op 后缀如 fileSizeBytesLte） */
  private final String mapKeyBase;

  FilterableField(String paramName, String mapKeyBase) {
    this.paramName = paramName;
    this.mapKeyBase = mapKeyBase;
  }

  public String getParamName() {
    return paramName;
  }

  public String getMapKeyBase() {
    return mapKeyBase;
  }

  /** 根据 URL 参数名查找，如 "file_size_bytes" → FILE_SIZE_BYTES。 */
  public static Optional<FilterableField> fromParamName(String name) {
    for (FilterableField f : values()) {
      if (f.paramName.equalsIgnoreCase(name)) return Optional.of(f);
    }
    return Optional.empty();
  }
}
