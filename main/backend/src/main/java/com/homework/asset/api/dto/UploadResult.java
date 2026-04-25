package com.homework.asset.api.dto;

import java.util.List;

public record UploadResult(
    int totalRows,
    int inserted,
    int updated,
    int rejected,
    List<RejectedRecord> rejectedRecords) {

  public static UploadResult of(int totalRows, int inserted, int updated, List<RejectedRecord> rejectedRecords) {
    return new UploadResult(totalRows, inserted, updated, rejectedRecords.size(), rejectedRecords);
  }

  public record RejectedRecord(int rowNum, String rawId, String reason) {}
}
