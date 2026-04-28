package com.homework.asset.ingest;

import com.homework.asset.domain.entity.Asset;
import com.homework.asset.mapper.AssetMapper;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * ETL 批次写入服务。
 *
 * 职责：
 * - 批量 Upsert Asset 实体（INSERT ON CONFLICT DO UPDATE）
 * - 统计插入 vs 更新的条数
 * - 事务管理（整批提交或回滚）
 */
@Service
public class IngestBatchService {

  private static final Logger log = LoggerFactory.getLogger(IngestBatchService.class);
  private static final int CHUNK_SIZE = 500;

  private final AssetMapper assetMapper;
  private final JdbcTemplate jdbcTemplate;

  public IngestBatchService(AssetMapper assetMapper, JdbcTemplate jdbcTemplate) {
    this.assetMapper = assetMapper;
    this.jdbcTemplate = jdbcTemplate;
  }

  @Transactional
  public int upsertBatch(int datasetNum, List<Asset> assets) {
    for (int i = 0; i < assets.size(); i += CHUNK_SIZE) {
      int end = Math.min(i + CHUNK_SIZE, assets.size());
      List<Asset> chunk = assets.subList(i, end);
      for (Asset asset : chunk) {
        assetMapper.upsert(asset);
      }
    }
    log.info("数据集 {} 批次提交，共 upsert {} 条", datasetNum, assets.size());
    return assets.size();
  }

  @Transactional
  public int[] upsertBatchWithStats(int datasetNum, List<Asset> assets) {
    Set<String> existingKeys = batchCheckExists(assets);

    int inserted = 0;
    int updated = 0;
    for (int i = 0; i < assets.size(); i += CHUNK_SIZE) {
      int end = Math.min(i + CHUNK_SIZE, assets.size());
      for (Asset asset : assets.subList(i, end)) {
        String key = asset.getSourceDataset() + ":" + asset.getSourceId();
        if (existingKeys.contains(key)) {
          updated++;
        } else {
          inserted++;
        }
        assetMapper.upsert(asset);
      }
    }

    log.info("数据集 {} 批次提交：新增 {} 条，更新 {} 条", datasetNum, inserted, updated);
    return new int[]{inserted, updated};
  }

  private Set<String> batchCheckExists(List<Asset> assets) {
    if (assets.isEmpty()) {
      return Set.of();
    }

    Set<String> allKeys = new HashSet<>();
    for (int i = 0; i < assets.size(); i += CHUNK_SIZE) {
      int end = Math.min(i + CHUNK_SIZE, assets.size());
      List<Asset> chunk = assets.subList(i, end);

      StringBuilder sql = new StringBuilder(
          "SELECT source_dataset, source_id FROM assets WHERE (source_dataset, source_id) IN (");
      List<Object> params = new ArrayList<>();
      for (int j = 0; j < chunk.size(); j++) {
        if (j > 0) {
          sql.append(", ");
        }
        sql.append("(?, ?)");
        Asset a = chunk.get(j);
        params.add(a.getSourceDataset());
        params.add(a.getSourceId());
      }
      sql.append(")");

      jdbcTemplate.query(sql.toString(), rs -> {
        while (rs.next()) {
          allKeys.add(rs.getInt("source_dataset") + ":" + rs.getString("source_id"));
        }
        return null;
      }, params.toArray());
    }
    return allKeys;
  }
}
