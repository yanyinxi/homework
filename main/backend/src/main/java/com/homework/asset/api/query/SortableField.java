package com.homework.asset.api.query;

import java.util.Optional;

/**
 * 允许排序的字段白名单。
 *
 * <p>字段名通过此枚举映射到 DB 列名后才能放入 ORDER BY，防止字段名注入。
 */
public enum SortableField {
  UPLOADED_AT("uploaded_at", "uploaded_at"),
  FILE_SIZE_BYTES("file_size_bytes", "file_size_bytes"),
  TITLE("title", "title"),
  UPLOADER("uploader", "uploader"),
  STATUS("status", "status"),
  CITY("city", "city"),
  INGESTED_AT("ingested_at", "ingested_at");

  private final String paramName;
  private final String columnName;

  SortableField(String paramName, String columnName) {
    this.paramName = paramName;
    this.columnName = columnName;
  }

  public String getColumnName() {
    return columnName;
  }

  public static Optional<SortableField> fromParamName(String name) {
    for (SortableField f : values()) {
      if (f.paramName.equalsIgnoreCase(name)) return Optional.of(f);
    }
    return Optional.empty();
  }
}
