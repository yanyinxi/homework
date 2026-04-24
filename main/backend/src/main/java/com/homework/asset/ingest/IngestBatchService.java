package com.homework.asset.ingest;

import com.homework.asset.domain.entity.Asset;
import com.homework.asset.mapper.AssetMapper;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * ETL 批次写入服务。
 *
 * 独立 Spring Bean 是为了让 @Transactional 通过代理生效——
 * IngestRunner 调用自身方法时 Spring 代理不介入，事务不起作用。
 */
@Service
public class IngestBatchService {

  private static final Logger log = LoggerFactory.getLogger(IngestBatchService.class);

  private final AssetMapper assetMapper;

  public IngestBatchService(AssetMapper assetMapper) {
    this.assetMapper = assetMapper;
  }

  /** 批次级事务：任意行 upsert 失败则整批回滚。 */
  @Transactional
  public int upsertBatch(int datasetNum, List<Asset> assets) {
    for (Asset asset : assets) {
      assetMapper.upsert(asset);
    }
    log.info("数据集 {} 批次提交，共 upsert {} 条", datasetNum, assets.size());
    return assets.size();
  }
}
