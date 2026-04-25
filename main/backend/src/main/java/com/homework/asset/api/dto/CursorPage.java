package com.homework.asset.api.dto;

import java.util.List;
import java.util.Map;

/** Keyset/Cursor 分页响应。避免 OFFSET 在大数据集上的性能问题。 */
public record CursorPage<T>(
    List<T> items,
    String nextCursor,
    boolean hasMore,
    int pageSize) {

  public static CursorPage<Map<String, Object>> of(
      List<Map<String, Object>> items, String nextCursor, int pageSize) {
    boolean hasMore = nextCursor != null && !nextCursor.isEmpty();
    return new CursorPage<>(items, nextCursor, hasMore, pageSize);
  }
}
