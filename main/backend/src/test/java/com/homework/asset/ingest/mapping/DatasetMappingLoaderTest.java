package com.homework.asset.ingest.mapping;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DatasetMappingLoaderTest {

  private DatasetMappingLoader loader;

  @BeforeEach
  void setUp() {
    loader = new DatasetMappingLoader();
  }

  @Test
  void shouldLoadMappingsFromClasspath() {
    loader.load();

    Map<String, DatasetMappingConfig.DatasetDefinition> all = loader.getAll();
    assertFalse(all.isEmpty(), "Should load at least one dataset mapping");
  }

  @Test
  void shouldGetDatasetById() {
    loader.load();

    DatasetMappingConfig.DatasetDefinition dataset = loader.getById("dataset_001");
    assertNotNull(dataset, "Should find dataset_001");
    assertEquals("素材数据集1", dataset.getName());
    assertNotNull(dataset.getFieldMappings());
    assertTrue(dataset.getFieldMappings().containsKey("素材编号"));
  }

  @Test
  void shouldGetDatasetByFileName() {
    loader.load();

    DatasetMappingConfig.DatasetDefinition dataset = loader.getByFileName("素材数据集1.xls");
    assertNotNull(dataset, "Should find dataset by file name");
    assertEquals("dataset_001", dataset.getId());
  }

  @Test
  void shouldHaveCorrectFieldMappings() {
    loader.load();

    DatasetMappingConfig.DatasetDefinition dataset = loader.getById("dataset_001");
    DatasetMappingConfig.FieldMapping mapping = dataset.getFieldMappings().get("上传日期");

    assertNotNull(mapping);
    assertEquals("uploaded_at", mapping.getTarget());
    assertEquals("excel_date", mapping.getType());
  }
}
