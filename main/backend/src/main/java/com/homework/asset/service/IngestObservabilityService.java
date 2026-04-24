package com.homework.asset.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.homework.asset.api.dto.PagedResponse;
import com.homework.asset.api.exception.ApiException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/** ETL 可观测查询服务：只读暴露 ingest_runs / ingest_rejects。 */
@Service
public class IngestObservabilityService {

  private final JdbcTemplate jdbcTemplate;
  private final ObjectMapper objectMapper;

  public IngestObservabilityService(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
    this.jdbcTemplate = jdbcTemplate;
    this.objectMapper = objectMapper;
  }

  public PagedResponse<Map<String, Object>> listRuns(int page, int pageSize) {
    validatePage(page, pageSize);
    int offset = (page - 1) * pageSize;

    List<Map<String, Object>> items =
        jdbcTemplate.query(
            """
            SELECT run_id, datasets, dry_run, status, total_rows, normalized_rows, upserted_rows,
                   rejected_rows, dataset_count, failed_datasets, batch_stats::text AS batch_stats,
                   error_message, started_at, finished_at
            FROM ingest_runs
            ORDER BY started_at DESC
            LIMIT ? OFFSET ?
            """,
            (rs, rowNum) -> mapRunRow(rs),
            pageSize,
            offset);

    Long total = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM ingest_runs", Long.class);
    return PagedResponse.of(items, total == null ? 0 : total, page, pageSize);
  }

  public PagedResponse<Map<String, Object>> listRejects(
      String runId, Integer sourceDataset, String stage, int page, int pageSize) {
    validatePage(page, pageSize);
    if (stage != null && !stage.isBlank() && !"normalize".equals(stage) && !"upsert".equals(stage)) {
      throw new ApiException(400, "Invalid stage: must be normalize or upsert");
    }
    UUID runUuid = null;
    if (runId != null && !runId.isBlank()) {
      try {
        runUuid = UUID.fromString(runId);
      } catch (IllegalArgumentException ex) {
        throw new ApiException(400, "Invalid run_id UUID: " + runId);
      }
    }

    StringBuilder where = new StringBuilder(" WHERE 1=1");
    List<Object> args = new ArrayList<>();
    if (runUuid != null) {
      where.append(" AND run_id = ?");
      args.add(runUuid);
    }
    if (sourceDataset != null) {
      where.append(" AND source_dataset = ?");
      args.add(sourceDataset);
    }
    if (stage != null && !stage.isBlank()) {
      where.append(" AND stage = ?");
      args.add(stage);
    }

    int offset = (page - 1) * pageSize;
    String querySql =
        """
        SELECT id, run_id, source_dataset, row_num, stage, source_id, reason, raw_record::text AS raw_record, created_at
        FROM ingest_rejects
        """
            + where
            + " ORDER BY created_at DESC LIMIT ? OFFSET ?";
    List<Object> queryArgs = new ArrayList<>(args);
    queryArgs.add(pageSize);
    queryArgs.add(offset);

    List<Map<String, Object>> items =
        jdbcTemplate.query(querySql, (rs, rowNum) -> mapRejectRow(rs), queryArgs.toArray());

    String countSql = "SELECT COUNT(*) FROM ingest_rejects" + where;
    Long total = jdbcTemplate.queryForObject(countSql, Long.class, args.toArray());
    return PagedResponse.of(items, total == null ? 0 : total, page, pageSize);
  }

  private void validatePage(int page, int pageSize) {
    if (page < 1) {
      throw new ApiException(400, "Invalid page: must be >= 1");
    }
    if (pageSize < 1 || pageSize > 200) {
      throw new ApiException(400, "Invalid page_size: must be between 1 and 200");
    }
  }

  private Map<String, Object> mapRunRow(ResultSet rs) throws SQLException {
    Map<String, Object> item = new HashMap<>();
    item.put("runId", rs.getObject("run_id"));
    item.put("datasets", rs.getString("datasets"));
    item.put("dryRun", rs.getBoolean("dry_run"));
    item.put("status", rs.getString("status"));
    item.put("totalRows", rs.getInt("total_rows"));
    item.put("normalizedRows", rs.getInt("normalized_rows"));
    item.put("upsertedRows", rs.getInt("upserted_rows"));
    item.put("rejectedRows", rs.getInt("rejected_rows"));
    item.put("datasetCount", rs.getInt("dataset_count"));
    item.put("failedDatasets", rs.getInt("failed_datasets"));
    item.put("batchStats", fromJson(rs.getString("batch_stats")));
    item.put("errorMessage", rs.getString("error_message"));
    item.put("startedAt", rs.getTimestamp("started_at"));
    item.put("finishedAt", rs.getTimestamp("finished_at"));
    return item;
  }

  private Map<String, Object> mapRejectRow(ResultSet rs) throws SQLException {
    Map<String, Object> item = new HashMap<>();
    item.put("id", rs.getLong("id"));
    item.put("runId", rs.getObject("run_id"));
    item.put("sourceDataset", rs.getInt("source_dataset"));
    item.put("rowNum", rs.getObject("row_num"));
    item.put("stage", rs.getString("stage"));
    item.put("sourceId", rs.getString("source_id"));
    item.put("reason", rs.getString("reason"));
    item.put("rawRecord", fromJson(rs.getString("raw_record")));
    item.put("createdAt", rs.getTimestamp("created_at"));
    return item;
  }

  private Object fromJson(String json) {
    if (json == null || json.isBlank()) {
      return null;
    }
    try {
      return objectMapper.readValue(json, Object.class);
    } catch (JsonProcessingException e) {
      return json;
    }
  }
}
