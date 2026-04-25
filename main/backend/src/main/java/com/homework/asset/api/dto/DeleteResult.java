package com.homework.asset.api.dto;

import java.util.List;

public record DeleteResult(
    int deleted,
    int notFound,
    List<String> notFoundIds) {

  public static DeleteResult of(int deleted, List<String> notFoundIds) {
    return new DeleteResult(deleted, notFoundIds.size(), notFoundIds);
  }

  public static DeleteResult empty() {
    return new DeleteResult(0, 0, List.of());
  }
}
