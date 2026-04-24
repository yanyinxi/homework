package com.homework.asset.ingest;

import com.homework.asset.domain.entity.Asset;
import com.homework.asset.ingest.adapter.Dataset1Adapter;
import com.homework.asset.ingest.adapter.Dataset2Adapter;
import com.homework.asset.ingest.adapter.Dataset3Adapter;
import com.homework.asset.ingest.adapter.DatasetAdapter;
import com.homework.asset.ingest.excel.ExcelReader;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/**
 * ETL 导入入口。
 *
 * <p>启动时若检测到 --ingest 参数则执行导入，否则跳过（不影响正常 API 服务）。
 *
 * <p>事务策略：每个 dataset 作为一个批次，通过 {@link IngestBatchService#upsertBatch} 使用独立事务。
 * 归一化阶段的脏行会被记录并跳过；写库阶段若任意 upsert 失败则整批回滚，不影响其他 dataset 批次。
 *
 * <p>用法：
 *
 * <pre>
 *   --ingest=all         # 导入全部三份数据集
 *   --ingest=1           # 只导入数据集1
 *   --ingest=2,3         # 导入数据集2和3
 *   --dry-run            # 仅打印行数，不实际写入
 * </pre>
 */
@Component
public class IngestRunner implements ApplicationRunner {

  private static final Logger log = LoggerFactory.getLogger(IngestRunner.class);

  /** xls 文件放在 classpath:samples/ 或通过绝对路径指定 */
  private static final Map<Integer, String> DATASET_PATHS =
      Map.of(
          1, "samples/素材数据集1.xls",
          2, "samples/素材数据集2.xls",
          3, "samples/素材数据集3.xls");

  private static final Map<Integer, DatasetAdapter> ADAPTERS =
      Map.of(
          1, new Dataset1Adapter(),
          2, new Dataset2Adapter(),
          3, new Dataset3Adapter());

  private final IngestBatchService batchService;
  private final IngestAuditService auditService;

  public IngestRunner(IngestBatchService batchService, IngestAuditService auditService) {
    this.batchService = batchService;
    this.auditService = auditService;
  }

  @Override
  public void run(ApplicationArguments args) throws Exception {
    if (!args.containsOption("ingest")) {
      return; // 未指定 --ingest，正常启动不导入
    }

    boolean dryRun = args.containsOption("dry-run");
    List<String> ingestValues = args.getOptionValues("ingest");
    List<Integer> datasets = resolveDatasets(ingestValues);
    UUID runId = auditService.startRun(datasets, dryRun);

    log.info("ETL 导入开始，runId: {}，数据集: {}，dry-run: {}", runId, datasets, dryRun);

    int totalRows = 0;
    int totalNormalized = 0;
    int totalUpserted = 0;
    int totalRejected = 0;
    int failedDatasets = 0;
    List<Map<String, Object>> batchStats = new ArrayList<>();

    try {
      for (int dsNum : datasets) {
        // 各 dataset 批次独立处理，一个批次失败不影响其他批次
        try {
          BatchOutcome outcome = processBatch(runId, dsNum, dryRun);
          totalRows += outcome.totalRows();
          totalNormalized += outcome.normalizedRows();
          totalUpserted += outcome.upsertedRows();
          totalRejected += outcome.rejectedRows();
          if (outcome.batchFailed()) {
            failedDatasets++;
          }
          batchStats.add(outcome.toBatchStat(dsNum));
        } catch (Exception e) {
          failedDatasets++;
          Map<String, Object> failedBatch = new HashMap<>();
          failedBatch.put("dataset", dsNum);
          failedBatch.put("status", "failed");
          failedBatch.put("errorMessage", e.getMessage());
          batchStats.add(failedBatch);
          log.error("数据集 {} 批次导入失败，已整批回滚，跳过继续下一批次: {}", dsNum, e.getMessage(), e);
        }
      }

      String finalStatus = resolveRunStatus(datasets.size(), failedDatasets, totalRejected);
      auditService.finishRun(
          runId,
          finalStatus,
          totalRows,
          totalNormalized,
          totalUpserted,
          totalRejected,
          datasets.size(),
          failedDatasets,
          batchStats,
          null);
      log.info(
          "ETL 导入完成，runId: {}，status: {}，总行数: {}，成功 upsert: {}，失败行: {}",
          runId,
          finalStatus,
          totalRows,
          totalUpserted,
          totalRejected);
    } catch (Exception ex) {
      auditService.finishRun(
          runId,
          "failed",
          totalRows,
          totalNormalized,
          totalUpserted,
          totalRejected,
          datasets.size(),
          datasets.size(),
          batchStats,
          ex.getMessage());
      throw ex;
    }
  }

  /**
   * 读取并处理单个 dataset 批次。
   *
   * <p>先读取 Excel（IO 操作，不持有事务），再归一化（纯内存），最后调用 batchService 做批次级事务写入。
   * 这样避免了持有事务期间进行 IO 操作。
   */
  private BatchOutcome processBatch(UUID runId, int datasetNum, boolean dryRun) throws Exception {
    String path = DATASET_PATHS.get(datasetNum);
    DatasetAdapter adapter = ADAPTERS.get(datasetNum);

    log.info("正在读取数据集 {}，文件: {}", datasetNum, path);

    List<Map<String, Object>> rows;
    try (InputStream stream = new ClassPathResource(path).getInputStream()) {
      rows = ExcelReader.read(stream, path);
    }

    log.info("数据集 {} 读取 {} 行，开始归一化", datasetNum, rows.size());

    // 先做归一化（纯内存操作，不需要事务）
    // 归一化失败的行落表并跳过，不计入 upsert 批次。
    List<NormalizedRow> normalizedRows = new ArrayList<>(rows.size());
    int rejected = 0;
    int rowNum = 0;
    for (Map<String, Object> row : rows) {
      rowNum++;
      try {
        Asset asset = adapter.convert(row);
        if (asset.getSourceId() == null
            || asset.getTitle() == null
            || asset.getUploader() == null
            || asset.getStatus() == null) {
          throw new IllegalArgumentException("required fields are empty");
        }
        normalizedRows.add(new NormalizedRow(rowNum, row, asset));
        if (dryRun) {
          log.debug(
              "  [dry-run] sourceId={}, title={}, status={}",
              asset.getSourceId(),
              asset.getTitle(),
              asset.getStatus());
        }
      } catch (Exception e) {
        rejected++;
        auditService.recordReject(
            runId, datasetNum, rowNum, "normalize", extractSourceId(row), e.getMessage(), row);
        log.warn("数据集 {} 行归一化失败，跳过: {}，原因: {}", datasetNum, row, e.getMessage());
      }
    }

    log.info(
        "数据集 {} 归一化完成，有效记录 {} 条，失败 {} 条",
        datasetNum,
        normalizedRows.size(),
        rejected);

    if (dryRun) {
      return new BatchOutcome(rows.size(), normalizedRows.size(), normalizedRows.size(), rejected, false, null);
    }

    List<Asset> assets = normalizedRows.stream().map(NormalizedRow::asset).toList();
    log.info("数据集 {} 开始批次事务写入，待写入 {} 条", datasetNum, assets.size());

    // 批次级事务写入：全部成功或全部回滚
    try {
      int upserted = batchService.upsertBatch(datasetNum, assets);
      return new BatchOutcome(rows.size(), normalizedRows.size(), upserted, rejected, false, null);
    } catch (Exception e) {
      for (NormalizedRow normalizedRow : normalizedRows) {
        auditService.recordReject(
            runId,
            datasetNum,
            normalizedRow.rowNum(),
            "upsert",
            normalizedRow.asset().getSourceId(),
            "batch upsert failed: " + e.getMessage(),
            normalizedRow.rawRow());
      }
      log.error("数据集 {} 批次写库失败，已记录失败行 {} 条", datasetNum, normalizedRows.size(), e);
      return new BatchOutcome(
          rows.size(),
          normalizedRows.size(),
          0,
          rejected + normalizedRows.size(),
          true,
          e.getMessage());
    }
  }

  private String resolveRunStatus(int datasetCount, int failedDatasets, int rejectedRows) {
    if (failedDatasets >= datasetCount) {
      return "failed";
    }
    if (failedDatasets > 0 || rejectedRows > 0) {
      return "partial_success";
    }
    return "success";
  }

  private String extractSourceId(Map<String, Object> row) {
    String[] keys = {"id", "asset_id", "source_id", "素材ID", "素材id", "素材编号", "视频ID", "video_id"};
    for (String key : keys) {
      Object value = row.get(key);
      if (value != null && !String.valueOf(value).isBlank()) {
        return String.valueOf(value);
      }
    }
    return null;
  }

  private List<Integer> resolveDatasets(List<String> values) {
    if (values == null || values.isEmpty() || "all".equalsIgnoreCase(values.get(0))) {
      return List.of(1, 2, 3);
    }
    return values.stream()
        .flatMap(v -> List.of(v.split(",")).stream())
        .map(String::strip)
        .filter(s -> !s.isEmpty())
        .map(this::parseDatasetNumber)
        .toList();
  }

  private int parseDatasetNumber(String s) {
    try {
      int n = Integer.parseInt(s);
      if (n < 1 || n > 3) {
        throw new IllegalArgumentException("Dataset number must be 1, 2, or 3");
      }
      return n;
    } catch (NumberFormatException e) {
      throw new IllegalArgumentException(
          "Invalid dataset: '" + s + "'. Use 1, 2, 3 or 'all'");
    }
  }

  private record NormalizedRow(int rowNum, Map<String, Object> rawRow, Asset asset) {}

  private record BatchOutcome(
      int totalRows,
      int normalizedRows,
      int upsertedRows,
      int rejectedRows,
      boolean batchFailed,
      String errorMessage) {

    private Map<String, Object> toBatchStat(int datasetNum) {
      Map<String, Object> item = new HashMap<>();
      item.put("dataset", datasetNum);
      item.put("totalRows", totalRows);
      item.put("normalizedRows", normalizedRows);
      item.put("upsertedRows", upsertedRows);
      item.put("rejectedRows", rejectedRows);
      item.put("status", batchFailed ? "failed" : "success");
      item.put("errorMessage", errorMessage);
      return item;
    }
  }
}
