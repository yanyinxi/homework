package com.homework.asset.ingest;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.homework.asset.api.exception.ApiException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/** ETL 审计服务：记录导入运行、批次统计与失败行。 */
@Service
public class IngestAuditService {

  private final JdbcTemplate jdbcTemplate;
  private final ObjectMapper objectMapper;

  public IngestAuditService(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
    this.jdbcTemplate = jdbcTemplate;
    this.objectMapper = objectMapper;
  }

  public UUID startRun(List<Integer> datasets, boolean dryRun) {
    UUID runId = UUID.randomUUID();
    jdbcTemplate.update(
        """
        INSERT INTO ingest_runs
        (run_id, datasets, dry_run, status, started_at, updated_at)
        VALUES (?, ?, ?, 'running', ?, ?)
        """,
        runId,
        datasets.stream().map(String::valueOf).reduce((left, right) -> left + "," + right).orElse(""),
        dryRun,
        Timestamp.from(Instant.now()),
        Timestamp.from(Instant.now()));
    return runId;
  }

  public void recordReject(
      UUID runId,
      int sourceDataset,
      Integer rowNum,
      String stage,
      String sourceId,
      String reason,
      Map<String, Object> rawRecord) {
    jdbcTemplate.update(
        """
        INSERT INTO ingest_rejects
        (run_id, source_dataset, row_num, stage, source_id, reason, raw_record, created_at)
        VALUES (?, ?, ?, ?, ?, ?, ?::jsonb, ?)
        """,
        runId,
        sourceDataset,
        rowNum,
        stage,
        sourceId,
        reason,
        toJson(rawRecord),
        Timestamp.from(Instant.now()));
  }

  public void finishRun(
      UUID runId,
      String status,
      int totalRows,
      int normalizedRows,
      int upsertedRows,
      int rejectedRows,
      int datasetCount,
      int failedDatasets,
      List<Map<String, Object>> batchStats,
      String errorMessage) {
    jdbcTemplate.update(
        """
        UPDATE ingest_runs
        SET status = ?,
            total_rows = ?,
            normalized_rows = ?,
            upserted_rows = ?,
            rejected_rows = ?,
            dataset_count = ?,
            failed_datasets = ?,
            batch_stats = ?::jsonb,
            error_message = ?,
            finished_at = ?,
            updated_at = ?
        WHERE run_id = ?
        """,
        status,
        totalRows,
        normalizedRows,
        upsertedRows,
        rejectedRows,
        datasetCount,
        failedDatasets,
        toJson(batchStats),
        errorMessage,
        Timestamp.from(Instant.now()),
        Timestamp.from(Instant.now()),
        runId);
  }

  private String toJson(Object value) {
    try {
      return objectMapper.writeValueAsString(value);
    } catch (JsonProcessingException e) {
      throw new ApiException(500, "Failed to serialize ingest audit payload");
    }
  }
}
