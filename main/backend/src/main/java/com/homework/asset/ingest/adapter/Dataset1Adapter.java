package com.homework.asset.ingest.adapter;

import com.homework.asset.domain.entity.Asset;
import com.homework.asset.ingest.normalizer.EtlNormalizers;
import java.util.Map;

/** 数据集1（中文字段，文件大小单位 MB，上传日期为 Excel 序列号）。 */
public class Dataset1Adapter implements DatasetAdapter {

  @Override
  public int datasetNumber() {
    return 1;
  }

  @Override
  public Asset convert(Map<String, Object> rawRow) {
    Asset asset = new Asset();
    asset.setSourceDataset(1);
    asset.setSourceId(str(rawRow, "素材编号"));
    asset.setTitle(str(rawRow, "标题"));
    asset.setUploader(str(rawRow, "上传人"));

    // Excel 日期序列号（整数）→ Instant
    Object dateVal = rawRow.get("上传日期");
    if (dateVal instanceof Number n) {
      asset.setUploadedAt(EtlNormalizers.normalizeDate(n.doubleValue()));
    }

    // "63.76MB" 字符串 → bytes
    String sizeStr = str(rawRow, "文件大小(MB)");
    if (sizeStr != null) {
      asset.setFileSizeBytes(EtlNormalizers.sizeFromString(sizeStr));
    }

    asset.setStatus(EtlNormalizers.normalizeStatus(str(rawRow, "审核状态")));
    asset.setTags(EtlNormalizers.normalizeTags(str(rawRow, "标签")));
    asset.setCity(str(rawRow, "所在城市"));
    asset.setReviewer(str(rawRow, "审核人"));
    asset.setRemark(str(rawRow, "备注"));
    asset.setPlatform(null);
    asset.setExtra(Map.of());
    asset.setRawRecord(rawRow);
    return asset;
  }
}
