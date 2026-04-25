package com.homework.asset.service;

import com.homework.asset.api.dto.DeleteResult;
import com.homework.asset.api.dto.UploadResult;
import com.homework.asset.api.dto.UploadResult.RejectedRecord;
import com.homework.asset.api.exception.ApiException;
import com.homework.asset.domain.entity.Asset;
import com.homework.asset.ingest.IngestBatchService;
import com.homework.asset.ingest.adapter.Dataset1Adapter;
import com.homework.asset.ingest.adapter.Dataset2Adapter;
import com.homework.asset.ingest.adapter.Dataset3Adapter;
import com.homework.asset.ingest.adapter.DatasetAdapter;
import com.homework.asset.ingest.excel.ExcelReader;
import com.homework.asset.mapper.AssetMapper;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * 素材写入服务。
 * 
 * 职责：
 * - Excel 文件上传导入
 * - 单条 / 批量 / 条件删除
 * - 自动检测数据集格式或指定适配器
 * - 统计插入和更新计数
 */
@Service
public class AssetCommandService {

  private static final Logger log = LoggerFactory.getLogger(AssetCommandService.class);

  private final AssetMapper assetMapper;
  private final IngestBatchService ingestBatchService;

  public AssetCommandService(AssetMapper assetMapper, IngestBatchService ingestBatchService) {
    this.assetMapper = assetMapper;
    this.ingestBatchService = ingestBatchService;
  }

  @Transactional
  public UploadResult importFromExcel(MultipartFile file, Integer dataset) {
    try {
      List<Map<String, Object>> rows = ExcelReader.read(file.getInputStream(), file.getOriginalFilename());

      if (rows.isEmpty()) {
        return new UploadResult(0, 0, 0, 0, List.of());
      }

      DatasetAdapter adapter = (dataset != null)
          ? getAdapterByDatasetNumber(dataset)
          : detectAdapter(rows.get(0));

      List<Asset> validAssets = new ArrayList<>();
      List<RejectedRecord> rejectedRecords = new ArrayList<>();

      for (int i = 0; i < rows.size(); i++) {
        Map<String, Object> rawRow = rows.get(i);
        try {
          Asset asset = adapter.convert(rawRow);
          if (isValidAsset(asset)) {
            validAssets.add(asset);
          } else {
            rejectedRecords.add(new RejectedRecord(
                i + 2,
                asset.getSourceId(),
                "Missing required field: title, uploader, or status"));
          }
        } catch (Exception e) {
          rejectedRecords.add(new RejectedRecord(
              i + 2,
              String.valueOf(rawRow.getOrDefault("素材编号", rawRow.getOrDefault("asset_id", rawRow.getOrDefault("素材id", "unknown")))),
              e.getMessage()));
        }
      }

      if (!validAssets.isEmpty()) {
        // 统计实际插入和更新的数量
        int[] counts = ingestBatchService.upsertBatchWithStats(adapter.datasetNumber(), validAssets);
        int inserted = counts[0];
        int updated = counts[1];

        log.info("Excel import completed: {} rows read, {} inserted, {} updated, {} rejected",
            rows.size(), inserted, updated, rejectedRecords.size());

        return UploadResult.of(rows.size(), inserted, updated, rejectedRecords);
      }

      log.info("Excel import completed: {} rows read, 0 valid, {} rejected",
          rows.size(), rejectedRecords.size());

      return UploadResult.of(rows.size(), 0, 0, rejectedRecords);

    } catch (IOException e) {
      throw new ApiException(500, "Failed to read Excel file: " + e.getMessage());
    }
  }

  @Transactional
  public DeleteResult deleteById(String id) {
    validateUuid(id);

    int deleted = assetMapper.deleteById(UUID.fromString(id));
    if (deleted == 0) {
      return DeleteResult.of(0, List.of(id));
    }
    return DeleteResult.of(1, List.of());
  }

  @Transactional
  public DeleteResult deleteBatch(List<String> ids) {
    if (ids == null || ids.isEmpty()) {
      return DeleteResult.empty();
    }

    if (ids.size() > 1000) {
      throw new ApiException(400, "Batch delete limit exceeded: max 1000 IDs per request");
    }

    List<String> validIds = new ArrayList<>();
    List<String> invalidIds = new ArrayList<>();

    for (String id : ids) {
      try {
        UUID.fromString(id);
        validIds.add(id);
      } catch (IllegalArgumentException e) {
        invalidIds.add(id);
      }
    }

    if (!invalidIds.isEmpty()) {
      throw new ApiException(400, "Invalid UUID format: " + String.join(", ", invalidIds.subList(0, Math.min(5, invalidIds.size()))));
    }

    Set<String> uniqueIds = new HashSet<>(validIds);
    int deleted = assetMapper.deleteBatchByIds(uniqueIds.stream().map(UUID::fromString).toList());

    List<String> notFoundIds = new ArrayList<>();
    if (deleted < uniqueIds.size()) {
      for (String id : uniqueIds) {
        if (assetMapper.selectById(UUID.fromString(id)) == null) {
          notFoundIds.add(id);
        }
      }
    }

    log.info("Batch delete completed: {} deleted, {} not found", deleted, notFoundIds.size());
    return DeleteResult.of(deleted, notFoundIds);
  }

  @Transactional
  public DeleteResult deleteByQuery(String status, String uploader, Integer sourceDataset) {
    Map<String, Object> params = new java.util.HashMap<>();

    if (status != null && !status.isBlank()) {
      if (!Set.of("pending", "approved", "rejected").contains(status)) {
        throw new ApiException(400, "Invalid status: " + status);
      }
      params.put("status", status);
    }

    if (uploader != null && !uploader.isBlank()) {
      params.put("uploader", uploader);
    }

    if (sourceDataset != null) {
      if (sourceDataset < 1 || sourceDataset > 3) {
        throw new ApiException(400, "Invalid sourceDataset: " + sourceDataset);
      }
      params.put("sourceDataset", sourceDataset);
    }

    if (params.isEmpty()) {
      throw new ApiException(400, "At least one filter condition is required for deleteByQuery");
    }

    int deleted = assetMapper.deleteByParams(params);
    log.info("Delete by query completed: {} deleted (status={}, uploader={}, sourceDataset={})",
        deleted, status, uploader, sourceDataset);

    return DeleteResult.of(deleted, List.of());
  }

  private DatasetAdapter getAdapterByDatasetNumber(int dataset) {
    return switch (dataset) {
      case 1 -> new Dataset1Adapter();
      case 2 -> new Dataset2Adapter();
      case 3 -> new Dataset3Adapter();
      default -> throw new ApiException(400, "Unknown dataset number: " + dataset);
    };
  }

  private DatasetAdapter detectAdapter(Map<String, Object> sampleRow) {
    Set<String> headers = sampleRow.keySet();

    // Dataset1 特征：中文列名（素材编号、上传日期）
    if (headers.contains("素材编号") || headers.contains("上传日期")) {
      return new Dataset1Adapter();
    }
    
    // Dataset3 特征：混合中英字段（ID、素材title、上传者、timestamp）
    if (headers.contains("ID") || headers.contains("素材title") || headers.contains("上传者") || headers.contains("timestamp")) {
      return new Dataset3Adapter();
    }
    
    // Dataset2 特征：纯英文字段（asset_id、uploaded_at、file_size_bytes）
    if (headers.contains("asset_id") || headers.contains("uploaded_at") || headers.contains("file_size_bytes")) {
      return new Dataset2Adapter();
    }
    
    // 兼容旧逻辑：检查素材id（Dataset3 另一种格式）
    if (headers.contains("素材id") || headers.contains("平台")) {
      return new Dataset3Adapter();
    }

    throw new ApiException(400, "Unable to detect dataset format. Please contact administrator for data mapping.");
  }

  private boolean isValidAsset(Asset asset) {
    return asset.getTitle() != null && !asset.getTitle().isBlank()
        && asset.getUploader() != null && !asset.getUploader().isBlank()
        && asset.getStatus() != null && !asset.getStatus().isBlank()
        && asset.getSourceId() != null && !asset.getSourceId().isBlank();
  }

  private void validateUuid(String id) {
    try {
      UUID.fromString(id);
    } catch (IllegalArgumentException e) {
      throw new ApiException(400, "Invalid UUID: " + id);
    }
  }
}
