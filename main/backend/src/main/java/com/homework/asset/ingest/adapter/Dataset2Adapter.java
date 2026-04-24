package com.homework.asset.ingest.adapter;

import com.homework.asset.domain.entity.Asset;
import com.homework.asset.ingest.normalizer.EtlNormalizers;
import java.util.Map;

/** 数据集2（英文字段，文件大小已是字节，上传日期为 Excel 序列号含小数）。 */
public class Dataset2Adapter implements DatasetAdapter {

  @Override
  public int datasetNumber() {
    return 2;
  }

  @Override
  public Asset convert(Map<String, Object> rawRow) {
    Asset asset = new Asset();
    asset.setSourceDataset(2);
    asset.setSourceId(str(rawRow, "asset_id"));
    asset.setTitle(str(rawRow, "title"));
    asset.setUploader(str(rawRow, "uploader"));

    // Excel 序列号含小数部分（时间） → Instant
    Object dateVal = rawRow.get("uploaded_at");
    if (dateVal instanceof Number n) {
      asset.setUploadedAt(EtlNormalizers.normalizeDate(n.doubleValue()));
    }

    // 已是字节整数
    Object sizeVal = rawRow.get("file_size_bytes");
    if (sizeVal instanceof Number n) {
      asset.setFileSizeBytes(EtlNormalizers.sizeFromBytes(n.doubleValue()));
    }

    asset.setStatus(EtlNormalizers.normalizeStatus(str(rawRow, "status")));
    asset.setTags(EtlNormalizers.normalizeTags(str(rawRow, "tags")));
    asset.setCity(str(rawRow, "city"));
    asset.setPlatform(EtlNormalizers.normalizePlatform(str(rawRow, "platform")));

    // resolution 字段存在 "NULL"/"N/A" 占位符
    String res = str(rawRow, "resolution");
    if ("NULL".equalsIgnoreCase(res) || "N/A".equalsIgnoreCase(res)) res = null;
    asset.setResolution(res);

    // spend 字段非 N/A 时放入 extra
    String spend = str(rawRow, "spend");
    asset.setExtra(spend != null && !"N/A".equalsIgnoreCase(spend)
        ? Map.of("spend", spend) : Map.of());

    asset.setRawRecord(rawRow);
    return asset;
  }
}
