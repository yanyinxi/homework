package com.homework.asset.ingest;

import com.homework.asset.domain.entity.Asset;
import com.homework.asset.mapper.AssetMapper;
import java.util.List;
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

  private final AssetMapper assetMapper;
  private final JdbcTemplate jdbcTemplate;

  public IngestBatchService(AssetMapper assetMapper, JdbcTemplate jdbcTemplate) {
    this.assetMapper = assetMapper;
    this.jdbcTemplate = jdbcTemplate;
  }

  @Transactional
  public int upsertBatch(int datasetNum, List<Asset> assets) {
    for (Asset asset : assets) {
      assetMapper.upsert(asset);
    }
    log.info("数据集 {} 批次提交，共 upsert {} 条", datasetNum, assets.size());
    return assets.size();
  }

  @Transactional
  public int[] upsertBatchWithStats(int datasetNum, List<Asset> assets) {
    int inserted = 0;
    int updated = 0;

    for (Asset asset : assets) {
      boolean exists = checkExists(asset.getSourceDataset(), asset.getSourceId());
      assetMapper.upsert(asset);
      if (exists) {
        updated++;
      } else {
        inserted++;
      }
    }

    log.info("数据集 {} 批次提交：新增 {} 条，更新 {} 条", datasetNum, inserted, updated);
    return new int[]{inserted, updated};
  }

  private boolean checkExists(int sourceDataset, String sourceId) {
    Integer count = jdbcTemplate.queryForObject(
        "SELECT COUNT(*) FROM assets WHERE source_dataset = ? AND source_id = ?",
        Integer.class, sourceDataset, sourceId);
    return count != null && count > 0;
  }
}
