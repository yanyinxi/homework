package com.homework.asset.ingest.mapping;

import java.util.Map;

/**
 * 数据集映射配置。
 * 
 * <p>从 dataset-mappings.json 加载，定义源字段到目标字段的映射规则。
 */
public class DatasetMappingConfig {

  private DatasetDefinition[] datasets;

  public DatasetDefinition[] getDatasets() {
    return datasets;
  }

  public void setDatasets(DatasetDefinition[] datasets) {
    this.datasets = datasets;
  }

  /** 单个数据集定义 */
  public static class DatasetDefinition {
    private String id;
    private String name;
    private String description;
    private String filePattern;
    private Map<String, FieldMapping> fieldMappings;
    private Map<String, Object> defaults;

    public String getId() {
      return id;
    }

    public void setId(String id) {
      this.id = id;
    }

    public String getName() {
      return name;
    }

    public void setName(String name) {
      this.name = name;
    }

    public String getDescription() {
      return description;
    }

    public void setDescription(String description) {
      this.description = description;
    }

    public String getFilePattern() {
      return filePattern;
    }

    public void setFilePattern(String filePattern) {
      this.filePattern = filePattern;
    }

    public Map<String, FieldMapping> getFieldMappings() {
      return fieldMappings;
    }

    public void setFieldMappings(Map<String, FieldMapping> fieldMappings) {
      this.fieldMappings = fieldMappings;
    }

    public Map<String, Object> getDefaults() {
      return defaults;
    }

    public void setDefaults(Map<String, Object> defaults) {
      this.defaults = defaults;
    }
  }

  /** 字段映射规则 */
  public static class FieldMapping {
    private String target;
    private String type;
    private String separator;

    public String getTarget() {
      return target;
    }

    public void setTarget(String target) {
      this.target = target;
    }

    public String getType() {
      return type;
    }

    public void setType(String type) {
      this.type = type;
    }

    public String getSeparator() {
      return separator;
    }

    public void setSeparator(String separator) {
      this.separator = separator;
    }
  }
}
