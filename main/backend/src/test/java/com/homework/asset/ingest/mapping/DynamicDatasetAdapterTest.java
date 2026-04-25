package com.homework.asset.ingest.mapping;

import static org.junit.jupiter.api.Assertions.*;

import com.homework.asset.domain.entity.Asset;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DynamicDatasetAdapterTest {

  private DatasetMappingLoader loader;

  @BeforeEach
  void setUp() {
    loader = new DatasetMappingLoader();
    loader.load();
  }

  @Test
  void shouldConvertDataset1Row() {
    DatasetMappingConfig.DatasetDefinition definition = loader.getById("dataset_001");
    DynamicDatasetAdapter adapter = new DynamicDatasetAdapter(definition);

    Map<String, Object> row = Map.of(
        "素材编号", "A0001",
        "标题", "测试素材",
        "上传人", "张三",
        "上传日期", 45540,
        "文件大小(MB)", "63.76MB",
        "审核状态", "已通过",
        "标签", "节日;促销",
        "所在城市", "北京",
        "审核人", "李四",
        "备注", "测试备注"
    );

    Asset asset = adapter.convert(row);

    assertEquals(1, asset.getSourceDataset());
    assertEquals("A0001", asset.getSourceId());
    assertEquals("测试素材", asset.getTitle());
    assertEquals("张三", asset.getUploader());
    assertNotNull(asset.getUploadedAt());
    assertTrue(asset.getFileSizeBytes() > 60000000L, "File size should be around 66MB");
    assertEquals("approved", asset.getStatus());
    assertEquals(2, asset.getTags().size());
    assertTrue(asset.getTags().contains("节日"));
    assertEquals("北京", asset.getCity());
    assertEquals("李四", asset.getReviewer());
  }

  @Test
  void shouldConvertDataset2Row() {
    DatasetMappingConfig.DatasetDefinition definition = loader.getById("dataset_002");
    DynamicDatasetAdapter adapter = new DynamicDatasetAdapter(definition);

    Map<String, Object> row = Map.of(
        "asset_id", "asset_001",
        "title", "Test Asset",
        "uploader", "John",
        "upload_time", 45413.5,
        "file_size", 123456789L,
        "status", "approved",
        "tags", "life,funny",
        "city", "Shanghai",
        "resolution", "1080x1920",
        "platform", "qianchuan"
    );

    Asset asset = adapter.convert(row);

    assertEquals(2, asset.getSourceDataset());
    assertEquals("asset_001", asset.getSourceId());
    assertEquals("Test Asset", asset.getTitle());
    assertEquals(123456789L, asset.getFileSizeBytes());
    assertEquals("approved", asset.getStatus());
    assertEquals(2, asset.getTags().size());
    assertEquals("qianchuan", asset.getPlatform());
    assertEquals("1080x1920", asset.getResolution());
  }

  @Test
  void shouldConvertDataset3Row() {
    DatasetMappingConfig.DatasetDefinition definition = loader.getById("dataset_003");
    DynamicDatasetAdapter adapter = new DynamicDatasetAdapter(definition);

    Map<String, Object> row = Map.of(
        "video_id", "vid0001",
        "title", "Video Title",
        "uploader", "Alice",
        "upload_timestamp", 1715336373.0,
        "file_size", 500000000L,
        "status", "pending",
        "tags", "['品牌', '测评']",
        "city", "Guangzhou",
        "platform", "千川",
        "duration", 120
    );

    Asset asset = adapter.convert(row);

    assertEquals(3, asset.getSourceDataset());
    assertEquals("vid0001", asset.getSourceId());
    assertEquals("Video Title", asset.getTitle());
    assertEquals("pending", asset.getStatus());
    assertEquals(2, asset.getTags().size());
    assertTrue(asset.getTags().contains("品牌"));
    assertEquals("qianchuan", asset.getPlatform());
    assertEquals(120, asset.getDurationSec());
  }

  @Test
  void shouldReturnDatasetNumber() {
    DatasetMappingConfig.DatasetDefinition definition = loader.getById("dataset_001");
    DynamicDatasetAdapter adapter = new DynamicDatasetAdapter(definition);

    assertEquals(1, adapter.datasetNumber());
  }
}
