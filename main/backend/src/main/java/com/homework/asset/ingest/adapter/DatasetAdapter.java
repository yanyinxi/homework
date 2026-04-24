package com.homework.asset.ingest.adapter;

import com.homework.asset.domain.entity.Asset;
import java.util.Map;

/** ETL 适配器：把原始 Excel 行 Map 转换为归一化的 Asset 实体。 */
public interface DatasetAdapter {

  Asset convert(Map<String, Object> rawRow);

  int datasetNumber();

  /** 取 row[key]，去首尾空白；null / 空串 → null。 */
  default String str(Map<String, Object> row, String key) {
    Object v = row.get(key);
    if (v == null) return null;
    String s = v.toString().strip();
    return s.isEmpty() ? null : s;
  }
}
