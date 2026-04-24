package com.homework.asset.api.dto;

import java.util.List;

/** 分页响应体。 */
public record PagedResponse<T>(List<T> items, long total, int page, int pageSize) {

  public static <T> PagedResponse<T> of(List<T> items, long total, int page, int pageSize) {
    return new PagedResponse<>(items, total, page, pageSize);
  }
}
