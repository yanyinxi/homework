package com.homework.asset.ingest.mapping;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/** 数据集映射配置加载器 */
@Component
public class DatasetMappingLoader {

  private static final Logger log = LoggerFactory.getLogger(DatasetMappingLoader.class);
  private static final String CONFIG_PATH = "dataset-mappings.json";

  private final Map<String, DatasetMappingConfig.DatasetDefinition> mappingById = new HashMap<>();
  private final Map<String, DatasetMappingConfig.DatasetDefinition> mappingByPattern = new HashMap<>();
  private boolean loaded = false;

  public synchronized void load() {
    if (loaded) {
      return;
    }

    try {
      ClassPathResource resource = new ClassPathResource(CONFIG_PATH);
      if (!resource.exists()) {
        log.warn("Dataset mapping config not found: {}", CONFIG_PATH);
        loaded = true;
        return;
      }

      ObjectMapper mapper = new ObjectMapper();
      try (InputStream is = resource.getInputStream()) {
        DatasetMappingConfig config = mapper.readValue(is, DatasetMappingConfig.class);

        for (DatasetMappingConfig.DatasetDefinition dataset : config.getDatasets()) {
          mappingById.put(dataset.getId(), dataset);
          if (dataset.getFilePattern() != null) {
            mappingByPattern.put(dataset.getFilePattern(), dataset);
          }
          log.info("Loaded dataset mapping: {} -> {}", dataset.getId(), dataset.getName());
        }
      }

      loaded = true;
      log.info("Loaded {} dataset mappings", mappingById.size());

    } catch (IOException e) {
      log.error("Failed to load dataset mappings", e);
      loaded = true;
    }
  }

  public DatasetMappingConfig.DatasetDefinition getById(String id) {
    ensureLoaded();
    return mappingById.get(id);
  }

  public DatasetMappingConfig.DatasetDefinition getByFileName(String fileName) {
    ensureLoaded();
    for (Map.Entry<String, DatasetMappingConfig.DatasetDefinition> entry : mappingByPattern.entrySet()) {
      if (fileName.contains(entry.getKey().replace("*", ""))) {
        return entry.getValue();
      }
    }
    return null;
  }

  public Map<String, DatasetMappingConfig.DatasetDefinition> getAll() {
    ensureLoaded();
    return mappingById;
  }

  private void ensureLoaded() {
    if (!loaded) {
      load();
    }
  }
}
