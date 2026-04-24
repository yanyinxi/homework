package com.homework.asset.ingest.adapter;

import static org.assertj.core.api.Assertions.assertThat;

import com.homework.asset.domain.entity.Asset;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Dataset3Adapter 单元测试。
 *
 * <p>数据集3 字段特点：混合中英文字段名，Unix 秒时间戳，value+unit 双列大小，
 * 混合中英文状态，Python list 字符串标签，含平台/时长/城市字段。
 */
class Dataset3AdapterTest {

  private final Dataset3Adapter adapter = new Dataset3Adapter();

  private Map<String, Object> buildRow(
      String id,
      String title,
      String uploader,
      Object timestamp,
      Object size,
      String sizeUnit,
      String reviewStatus,
      String tags,
      String city,
      String platform,
      Object durationSec) {
    Map<String, Object> row = new HashMap<>();
    row.put("ID", id);
    row.put("素材title", title);
    row.put("上传者", uploader);
    row.put("timestamp", timestamp);
    row.put("size", size);
    row.put("size_unit", sizeUnit);
    row.put("review_status", reviewStatus);
    row.put("tags", tags);
    row.put("城市", city);
    row.put("投放平台", platform);
    row.put("duration_sec", durationSec);
    return row;
  }

  // ── 基础字段映射 ──

  @Test
  void convert_sourceDataset_is_3() {
    Map<String, Object> row = buildRow("vid0001", "测试", "王五", 1715336373.0, 541.2, "MB", "pending", "['品牌']", "北京", "qianchuan", 30.0);
    Asset asset = adapter.convert(row);
    assertThat(asset.getSourceDataset()).isEqualTo(3);
  }

  @Test
  void convert_sourceId_maps_from_ID() {
    Map<String, Object> row = buildRow("vid0001", "测试", "王五", 1715336373.0, 541.2, "MB", "pending", "['品牌']", "北京", "qianchuan", 30.0);
    Asset asset = adapter.convert(row);
    assertThat(asset.getSourceId()).isEqualTo("vid0001");
  }

  @Test
  void convert_title_from_素材title() {
    Map<String, Object> row = buildRow("vid0001", "品牌宣传视频", "王五", 1715336373.0, 541.2, "MB", "approved", "['品牌']", "北京", "qianchuan", 60.0);
    Asset asset = adapter.convert(row);
    assertThat(asset.getTitle()).isEqualTo("品牌宣传视频");
  }

  @Test
  void convert_uploader_from_上传者() {
    Map<String, Object> row = buildRow("vid0001", "测试", "赵六", 1715336373.0, 541.2, "MB", "pending", "['品牌']", "上海", "qianchuan", 30.0);
    Asset asset = adapter.convert(row);
    assertThat(asset.getUploader()).isEqualTo("赵六");
  }

  // ── Unix 秒时间戳 → Instant ──

  @Test
  void convert_uploadedAt_from_unix_timestamp() {
    // 1715336373 = 2024-05-10
    Map<String, Object> row = buildRow("vid0001", "测试", "王五", 1715336373.0, 541.2, "MB", "pending", "['品牌']", "北京", "qianchuan", 30.0);
    Asset asset = adapter.convert(row);
    assertThat(asset.getUploadedAt()).isNotNull();
    LocalDate date = asset.getUploadedAt().atZone(ZoneOffset.UTC).toLocalDate();
    assertThat(date).isEqualTo(LocalDate.of(2024, 5, 10));
  }

  @Test
  void convert_uploadedAt_another_unix_timestamp() {
    // 1700000000 = 2023-11-14
    Map<String, Object> row = buildRow("vid0002", "测试2", "王五", 1700000000.0, 100.0, "MB", "approved", "['测评']", "广州", "千川", 45.0);
    Asset asset = adapter.convert(row);
    LocalDate date = asset.getUploadedAt().atZone(ZoneOffset.UTC).toLocalDate();
    assertThat(date).isEqualTo(LocalDate.of(2023, 11, 14));
  }

  // ── value + unit 双列大小 ──

  @Test
  void convert_fileSizeBytes_mb_unit() {
    // 541.2 MB = 541.2 * 1024 * 1024 bytes
    Map<String, Object> row = buildRow("vid0001", "测试", "王五", 1715336373.0, 541.2, "MB", "pending", "['品牌']", "北京", "qianchuan", 30.0);
    Asset asset = adapter.convert(row);
    long expected = Math.round(541.2 * 1024 * 1024);
    // 允许 BigDecimal 精确计算与 double 近似之间有微小误差（使用范围判断）
    assertThat(asset.getFileSizeBytes()).isBetween(expected - 10, expected + 10);
  }

  @Test
  void convert_fileSizeBytes_gb_unit() {
    // 1.5 GB = 1.5 * 1024 * 1024 * 1024 bytes
    Map<String, Object> row = buildRow("vid0003", "测试", "王五", 1715336373.0, 1.5, "GB", "approved", "['品牌']", "北京", "qianchuan", 120.0);
    Asset asset = adapter.convert(row);
    long expected = Math.round(1.5 * 1024 * 1024 * 1024);
    assertThat(asset.getFileSizeBytes()).isBetween(expected - 10, expected + 10);
  }

  @Test
  void convert_fileSizeBytes_kb_unit() {
    Map<String, Object> row = buildRow("vid0004", "测试", "王五", 1715336373.0, 512.0, "KB", "pending", "['测试']", "上海", null, 10.0);
    Asset asset = adapter.convert(row);
    assertThat(asset.getFileSizeBytes()).isEqualTo(512L * 1024);
  }

  // ── 审核状态（混合中英文）→ canonical code ──

  @Test
  void convert_status_english_pending() {
    Map<String, Object> row = buildRow("vid0001", "测试", "王五", 1715336373.0, 541.2, "MB", "pending", "['品牌']", "北京", "qianchuan", 30.0);
    Asset asset = adapter.convert(row);
    assertThat(asset.getStatus()).isEqualTo("pending");
  }

  @Test
  void convert_status_english_approved() {
    Map<String, Object> row = buildRow("vid0002", "测试", "王五", 1715336373.0, 100.0, "MB", "approved", "['测评']", "广州", "千川", 45.0);
    Asset asset = adapter.convert(row);
    assertThat(asset.getStatus()).isEqualTo("approved");
  }

  @Test
  void convert_status_chinese_通过_maps_to_approved() {
    // 数据集3 中"通过"应归一到 "approved"
    Map<String, Object> row = buildRow("vid0003", "测试", "王五", 1715336373.0, 100.0, "MB", "通过", "['品牌']", "北京", "qianchuan", 30.0);
    Asset asset = adapter.convert(row);
    assertThat(asset.getStatus()).isEqualTo("approved");
  }

  @Test
  void convert_status_chinese_已通过_maps_to_approved() {
    Map<String, Object> row = buildRow("vid0004", "测试", "王五", 1715336373.0, 100.0, "MB", "已通过", "['品牌']", "北京", "qianchuan", 30.0);
    Asset asset = adapter.convert(row);
    assertThat(asset.getStatus()).isEqualTo("approved");
  }

  // ── 标签（Python list 字符串）→ List<String> ──

  @Test
  void convert_tags_python_list_single() {
    Map<String, Object> row = buildRow("vid0001", "测试", "王五", 1715336373.0, 541.2, "MB", "pending", "['品牌']", "北京", "qianchuan", 30.0);
    Asset asset = adapter.convert(row);
    assertThat(asset.getTags()).containsExactly("品牌");
  }

  @Test
  void convert_tags_python_list_multiple() {
    Map<String, Object> row = buildRow("vid0001", "测试", "王五", 1715336373.0, 541.2, "MB", "pending", "['品牌', '测评']", "北京", "qianchuan", 30.0);
    Asset asset = adapter.convert(row);
    assertThat(asset.getTags()).containsExactly("品牌", "测评");
  }

  @Test
  void convert_tags_python_list_three_items() {
    Map<String, Object> row = buildRow("vid0001", "测试", "王五", 1715336373.0, 541.2, "MB", "pending", "['品牌', '测评', '节日']", "北京", "qianchuan", 30.0);
    Asset asset = adapter.convert(row);
    assertThat(asset.getTags()).containsExactly("品牌", "测评", "节日");
  }

  // ── 平台（同义词归一）──

  @Test
  void convert_platform_qianchuan_english_canonical() {
    Map<String, Object> row = buildRow("vid0001", "测试", "王五", 1715336373.0, 541.2, "MB", "pending", "['品牌']", "北京", "qianchuan", 30.0);
    Asset asset = adapter.convert(row);
    assertThat(asset.getPlatform()).isEqualTo("qianchuan");
  }

  @Test
  void convert_platform_chinese_千川_canonical() {
    Map<String, Object> row = buildRow("vid0002", "测试", "王五", 1715336373.0, 100.0, "MB", "approved", "['测评']", "广州", "千川", 45.0);
    Asset asset = adapter.convert(row);
    assertThat(asset.getPlatform()).isEqualTo("qianchuan");
  }

  @Test
  void convert_platform_null_when_missing() {
    Map<String, Object> row = buildRow("vid0003", "测试", "王五", 1715336373.0, 100.0, "MB", "pending", "['品牌']", "上海", null, 30.0);
    Asset asset = adapter.convert(row);
    assertThat(asset.getPlatform()).isNull();
  }

  // ── 时长（duration_sec）──

  @Test
  void convert_durationSec_present() {
    Map<String, Object> row = buildRow("vid0001", "测试", "王五", 1715336373.0, 541.2, "MB", "pending", "['品牌']", "北京", "qianchuan", 60.0);
    Asset asset = adapter.convert(row);
    assertThat(asset.getDurationSec()).isEqualTo(60);
  }

  @Test
  void convert_durationSec_null_when_missing() {
    Map<String, Object> row = buildRow("vid0002", "测试", "王五", 1715336373.0, 100.0, "MB", "approved", "['测评']", "广州", "千川", null);
    Asset asset = adapter.convert(row);
    assertThat(asset.getDurationSec()).isNull();
  }

  // ── 城市 ──

  @Test
  void convert_city_from_城市() {
    Map<String, Object> row = buildRow("vid0001", "测试", "王五", 1715336373.0, 541.2, "MB", "pending", "['品牌']", "成都", "qianchuan", 30.0);
    Asset asset = adapter.convert(row);
    assertThat(asset.getCity()).isEqualTo("成都");
  }

  // ── rawRecord 保留原始行 ──

  @Test
  void convert_rawRecord_preserved() {
    Map<String, Object> row = buildRow("vid0001", "测试", "王五", 1715336373.0, 541.2, "MB", "pending", "['品牌']", "北京", "qianchuan", 30.0);
    Asset asset = adapter.convert(row);
    assertThat(asset.getRawRecord()).isNotNull();
  }
}
