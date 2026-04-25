package com.homework.asset.ingest.mapping;

import com.homework.asset.domain.entity.Asset;
import com.homework.asset.ingest.adapter.DatasetAdapter;
import com.homework.asset.ingest.normalizer.EtlNormalizers;
import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 动态数据集适配器。
 * 根据配置文件定义的字段映射规则，将原始数据转换为 Asset 实体。
 */
public class DynamicDatasetAdapter implements DatasetAdapter {

  private static final Logger log = LoggerFactory.getLogger(DynamicDatasetAdapter.class);

  private final DatasetMappingConfig.DatasetDefinition definition;

  public DynamicDatasetAdapter(DatasetMappingConfig.DatasetDefinition definition) {
    this.definition = definition;
  }

  @Override
  public int datasetNumber() {
    return extractDatasetNumber(definition.getId());
  }

  @Override
  public Asset convert(Map<String, Object> rawRow) {
    Asset asset = new Asset();
    asset.setSourceDataset(datasetNumber());
    asset.setRawRecord(rawRow);
    asset.setExtra(Map.of());

    for (Map.Entry<String, DatasetMappingConfig.FieldMapping> entry : definition.getFieldMappings().entrySet()) {
      String sourceField = entry.getKey();
      DatasetMappingConfig.FieldMapping mapping = entry.getValue();
      Object rawValue = rawRow.get(sourceField);

      applyMapping(asset, mapping, rawValue);
    }

    applyDefaults(asset);

    return asset;
  }

  private void applyMapping(Asset asset, DatasetMappingConfig.FieldMapping mapping, Object rawValue) {
    if (rawValue == null) {
      return;
    }

    String target = mapping.getTarget();
    String type = mapping.getType();

    switch (target) {
      case "source_id" -> asset.setSourceId(toStringValue(rawValue));
      case "title" -> asset.setTitle(toStringValue(rawValue));
      case "uploader" -> asset.setUploader(toStringValue(rawValue));
      case "uploaded_at" -> asset.setUploadedAt(parseDate(rawValue, type));
      case "file_size_bytes" -> asset.setFileSizeBytes(parseSize(rawValue, type));
      case "status" -> asset.setStatus(parseStatus(rawValue));
      case "tags" -> asset.setTags(parseTags(rawValue, type, mapping.getSeparator()));
      case "city" -> asset.setCity(toStringValue(rawValue));
      case "platform" -> asset.setPlatform(parsePlatform(rawValue));
      case "reviewer" -> asset.setReviewer(toStringValue(rawValue));
      case "remark" -> asset.setRemark(toStringValue(rawValue));
      case "resolution" -> asset.setResolution(toStringValue(rawValue));
      case "duration_sec" -> asset.setDurationSec(toIntegerValue(rawValue));
      default -> log.debug("Unknown target field: {}", target);
    }
  }

  private void applyDefaults(Asset asset) {
    if (definition.getDefaults() != null) {
      Map<String, Object> defaults = definition.getDefaults();
      if (defaults.containsKey("platform") && asset.getPlatform() == null) {
        asset.setPlatform((String) defaults.get("platform"));
      }
    }
  }

  private String toStringValue(Object value) {
    if (value == null) return null;
    String s = value.toString().strip();
    return s.isEmpty() ? null : s;
  }

  private Integer toIntegerValue(Object value) {
    if (value == null) return null;
    if (value instanceof Number n) return n.intValue();
    try {
      return Integer.parseInt(value.toString().strip());
    } catch (NumberFormatException e) {
      return null;
    }
  }

  private java.time.Instant parseDate(Object value, String type) {
    if (value == null) return null;

    return switch (type) {
      case "excel_date" -> {
        if (value instanceof Number n) {
          yield EtlNormalizers.normalizeDate(n.doubleValue());
        }
        yield null;
      }
      case "unix_timestamp" -> {
        if (value instanceof Number n) {
          yield EtlNormalizers.dateFromUnixSeconds(n.doubleValue());
        }
        yield null;
      }
      default -> null;
    };
  }

  private Long parseSize(Object value, String type) {
    if (value == null) return null;

    return switch (type) {
      case "bytes" -> {
        if (value instanceof Number n) {
          yield n.longValue();
        }
        yield null;
      }
      case "size_with_unit" -> {
        String sizeStr = toStringValue(value);
        if (sizeStr != null) {
          yield EtlNormalizers.sizeFromString(sizeStr);
        }
        yield null;
      }
      default -> null;
    };
  }

  private String parseStatus(Object value) {
    String rawStatus = toStringValue(value);
    return rawStatus != null ? EtlNormalizers.normalizeStatus(rawStatus) : null;
  }

  private List<String> parseTags(Object value, String type, String separator) {
    String rawTags = toStringValue(value);
    if (rawTags == null) return Collections.emptyList();

    return switch (type) {
      case "tags" -> {
        String sep = separator != null ? separator : ",";
        yield EtlNormalizers.normalizeTags(rawTags, sep);
      }
      case "tags_python_list" -> EtlNormalizers.normalizeTagsPythonList(rawTags);
      default -> Collections.emptyList();
    };
  }

  private String parsePlatform(Object value) {
    String rawPlatform = toStringValue(value);
    return rawPlatform != null ? EtlNormalizers.normalizePlatform(rawPlatform) : null;
  }

  private int extractDatasetNumber(String id) {
    if (id == null) return 0;
    String num = id.replaceAll("[^0-9]", "");
    return num.isEmpty() ? 0 : Integer.parseInt(num);
  }

  public String getDatasetId() {
    return definition.getId();
  }

  public String getDatasetName() {
    return definition.getName();
  }
}
