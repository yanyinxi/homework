package com.homework.asset.ingest.adapter;

import com.homework.asset.domain.entity.Asset;
import com.homework.asset.ingest.normalizer.EtlNormalizers;
import java.util.Map;

/** 数据集3（混合中英字段，Unix 秒时间戳，size+size_unit 双列，tags 为 Python list 字符串）。 */
public class Dataset3Adapter implements DatasetAdapter {

  @Override
  public int datasetNumber() {
    return 3;
  }

  @Override
  public Asset convert(Map<String, Object> rawRow) {
    Asset asset = new Asset();
    asset.setSourceDataset(3);
    asset.setSourceId(str(rawRow, "ID"));
    asset.setTitle(str(rawRow, "素材title"));
    asset.setUploader(str(rawRow, "上传者"));

    // Unix 秒时间戳（值 >= 1e9）或 Excel 序列号，由 EtlNormalizers.normalizeDate 自动区分
    Object tsVal = rawRow.get("timestamp");
    if (tsVal instanceof Number n) {
      asset.setUploadedAt(EtlNormalizers.normalizeDate(n.doubleValue()));
    }

    // size + size_unit 双列 → bytes
    Object sizeVal = rawRow.get("size");
    String sizeUnit = str(rawRow, "size_unit");
    if (sizeVal instanceof Number n && sizeUnit != null) {
      asset.setFileSizeBytes(EtlNormalizers.sizeFromValueAndUnit(n.doubleValue(), sizeUnit));
    }

    asset.setStatus(EtlNormalizers.normalizeStatus(str(rawRow, "review_status")));
    asset.setTags(EtlNormalizers.normalizeTags(str(rawRow, "tags")));
    asset.setCity(str(rawRow, "城市"));
    asset.setPlatform(EtlNormalizers.normalizePlatform(str(rawRow, "投放平台")));

    Object durVal = rawRow.get("duration_sec");
    if (durVal instanceof Number n) {
      asset.setDurationSec(n.intValue());
    }

    asset.setExtra(Map.of());
    asset.setRawRecord(rawRow);
    return asset;
  }
}
