package com.homework.asset.service;

import com.homework.asset.api.exception.ApiException;
import com.homework.asset.mapper.AssetMapper;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

/** 素材统计聚合服务：封装三条指定查询，与 Mapper 解耦，便于测试。 */
@Service
public class AssetStatsService {

  private final AssetMapper assetMapper;

  public AssetStatsService(AssetMapper assetMapper) {
    this.assetMapper = assetMapper;
  }

  /** Q1：审核已通过素材中，各上传人的平均文件大小。 */
  public List<Map<String, Object>> uploaderAvgSize() {
    return assetMapper.selectUploaderAvgSize();
  }

  /** Q2：按标签统计素材数量，列出数量最多的前 N 个标签。 */
  public List<Map<String, Object>> topTags(int limit) {
    if (limit < 1 || limit > 50) {
      throw new ApiException(400, "Invalid topN parameter: must be between 1 and 50");
    }
    return assetMapper.selectTopTags(limit);
  }

  /** Q3：各投放平台的审核通过率。 */
  public List<Map<String, Object>> platformApprovalRate() {
    return assetMapper.selectPlatformApprovalRate();
  }
}
