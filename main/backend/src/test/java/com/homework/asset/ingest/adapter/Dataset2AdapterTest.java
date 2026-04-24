package com.homework.asset.ingest.adapter;

import static org.assertj.core.api.Assertions.assertThat;

import com.homework.asset.domain.entity.Asset;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Dataset2Adapter 单元测试。
 *
 * <p>数据集2 字段特点：英文字段名，Excel 日期序列号含小数（带时间部分），
 * bytes 整数大小（已是字节），英文审核状态，逗号分隔标签，含平台/分辨率/spend 字段。
 */
class Dataset2AdapterTest {

  private final Dataset2Adapter adapter = new Dataset2Adapter();

  private Map<String, Object> buildRow(
      String assetId,
      String title,
      String uploader,
      Object uploadedAt,
      Object fileSizeBytes,
      String status,
      String tags,
      String city,
      String platform,
      String resolution,
      String spend) {
    Map<String, Object> row = new HashMap<>();
    row.put("asset_id", assetId);
    row.put("title", title);
    row.put("uploader", uploader);
    row.put("uploaded_at", uploadedAt);
    row.put("file_size_bytes", fileSizeBytes);
    row.put("status", status);
    row.put("tags", tags);
    row.put("city", city);
    row.put("platform", platform);
    row.put("resolution", resolution);
    row.put("spend", spend);
    return row;
  }

  // ── 基础字段映射 ──

  @Test
  void convert_sourceDataset_is_2() {
    Map<String, Object> row = buildRow("asset_001", "title", "user1", 45413.79, 106268720.0, "approved", "生活,搞笑", "北京", "千川", "720x1280", "N/A");
    Asset asset = adapter.convert(row);
    assertThat(asset.getSourceDataset()).isEqualTo(2);
  }

  @Test
  void convert_sourceId_maps_from_asset_id() {
    Map<String, Object> row = buildRow("asset_001", "title", "user1", 45413.79, 106268720.0, "approved", "生活", "北京", "千川", "720x1280", "N/A");
    Asset asset = adapter.convert(row);
    assertThat(asset.getSourceId()).isEqualTo("asset_001");
  }

  @Test
  void convert_title_maps_correctly() {
    Map<String, Object> row = buildRow("asset_002", "春节营销素材", "user2", 45413.79, 50000000.0, "pending", "节日", "上海", "千川", null, "N/A");
    Asset asset = adapter.convert(row);
    assertThat(asset.getTitle()).isEqualTo("春节营销素材");
  }

  // ── Excel 日期序列号含小数（带时间部分）→ Instant ──

  @Test
  void convert_uploadedAt_from_excel_serial_with_fraction() {
    // 45413.792025 ≈ 2024-04-06 午后
    Map<String, Object> row = buildRow("asset_001", "title", "user1", 45413.792025, 106268720.0, "approved", "生活", "北京", "千川", "720x1280", "N/A");
    Asset asset = adapter.convert(row);
    assertThat(asset.getUploadedAt()).isNotNull();
    LocalDate date = asset.getUploadedAt().atZone(ZoneOffset.UTC).toLocalDate();
    assertThat(date).isEqualTo(LocalDate.of(2024, 4, 6));
  }

  @Test
  void convert_uploadedAt_integer_serial_also_works() {
    // 整数序列号（无小数）同样能正确处理
    Map<String, Object> row = buildRow("asset_002", "title", "user1", 45540.0, 50000000.0, "pending", "节日", "上海", "千川", null, "N/A");
    Asset asset = adapter.convert(row);
    LocalDate date = asset.getUploadedAt().atZone(ZoneOffset.UTC).toLocalDate();
    assertThat(date).isEqualTo(LocalDate.of(2024, 8, 10));
  }

  // ── bytes 整数大小 ──

  @Test
  void convert_fileSizeBytes_from_integer_bytes() {
    // file_size_bytes 已经是字节数，直接转换
    Map<String, Object> row = buildRow("asset_001", "title", "user1", 45413.79, 106268720.0, "approved", "生活", "北京", "千川", "720x1280", "N/A");
    Asset asset = adapter.convert(row);
    assertThat(asset.getFileSizeBytes()).isEqualTo(106268720L);
  }

  @Test
  void convert_fileSizeBytes_small_value() {
    Map<String, Object> row = buildRow("asset_002", "title", "user1", 45413.79, 1024.0, "pending", "测试", "广州", null, null, null);
    Asset asset = adapter.convert(row);
    assertThat(asset.getFileSizeBytes()).isEqualTo(1024L);
  }

  // ── 审核状态（英文）→ canonical code ──

  @Test
  void convert_status_english_approved() {
    Map<String, Object> row = buildRow("asset_001", "title", "user1", 45413.79, 106268720.0, "approved", "生活", "北京", "千川", "720x1280", "N/A");
    Asset asset = adapter.convert(row);
    assertThat(asset.getStatus()).isEqualTo("approved");
  }

  @Test
  void convert_status_english_pending() {
    Map<String, Object> row = buildRow("asset_002", "title", "user1", 45413.79, 50000000.0, "pending", "节日", "上海", "千川", null, "N/A");
    Asset asset = adapter.convert(row);
    assertThat(asset.getStatus()).isEqualTo("pending");
  }

  @Test
  void convert_status_english_rejected() {
    Map<String, Object> row = buildRow("asset_003", "title", "user1", 45413.79, 50000000.0, "rejected", "广告", "深圳", null, null, null);
    Asset asset = adapter.convert(row);
    assertThat(asset.getStatus()).isEqualTo("rejected");
  }

  // ── 标签（逗号分隔）→ List<String> ──

  @Test
  void convert_tags_comma_separated() {
    Map<String, Object> row = buildRow("asset_001", "title", "user1", 45413.79, 106268720.0, "approved", "生活,搞笑,测评", "北京", "千川", "720x1280", "N/A");
    Asset asset = adapter.convert(row);
    assertThat(asset.getTags()).containsExactly("生活", "搞笑", "测评");
  }

  @Test
  void convert_tags_single_item() {
    Map<String, Object> row = buildRow("asset_002", "title", "user1", 45413.79, 50000000.0, "pending", "测评", "上海", "千川", null, null);
    Asset asset = adapter.convert(row);
    assertThat(asset.getTags()).containsExactly("测评");
  }

  // ── 平台（同义词归一）──

  @Test
  void convert_platform_qianchuan_canonical() {
    Map<String, Object> row = buildRow("asset_001", "title", "user1", 45413.79, 106268720.0, "approved", "生活", "北京", "千川", "720x1280", "N/A");
    Asset asset = adapter.convert(row);
    assertThat(asset.getPlatform()).isEqualTo("qianchuan");
  }

  @Test
  void convert_platform_null_when_missing() {
    Map<String, Object> row = buildRow("asset_003", "title", "user1", 45413.79, 50000000.0, "pending", "测试", "广州", null, null, null);
    Asset asset = adapter.convert(row);
    assertThat(asset.getPlatform()).isNull();
  }

  // ── 分辨率 ──

  @Test
  void convert_resolution_present() {
    Map<String, Object> row = buildRow("asset_001", "title", "user1", 45413.79, 106268720.0, "approved", "生活", "北京", "千川", "720x1280", "N/A");
    Asset asset = adapter.convert(row);
    assertThat(asset.getResolution()).isEqualTo("720x1280");
  }

  @Test
  void convert_resolution_null_when_null_value() {
    Map<String, Object> row = buildRow("asset_002", "title", "user1", 45413.79, 50000000.0, "pending", "节日", "上海", "千川", "NULL", null);
    Asset asset = adapter.convert(row);
    assertThat(asset.getResolution()).isNull();
  }

  @Test
  void convert_resolution_null_when_na() {
    Map<String, Object> row = buildRow("asset_003", "title", "user1", 45413.79, 50000000.0, "pending", "节日", "上海", "千川", "N/A", null);
    Asset asset = adapter.convert(row);
    assertThat(asset.getResolution()).isNull();
  }

  // ── spend 字段进入 extra ──

  @Test
  void convert_spend_na_produces_empty_extra() {
    Map<String, Object> row = buildRow("asset_001", "title", "user1", 45413.79, 106268720.0, "approved", "生活", "北京", "千川", "720x1280", "N/A");
    Asset asset = adapter.convert(row);
    // spend 为 N/A 时 extra 为空 Map
    @SuppressWarnings("unchecked")
    Map<String, Object> extra = (Map<String, Object>) asset.getExtra();
    assertThat(extra).doesNotContainKey("spend");
  }

  @Test
  void convert_spend_value_goes_into_extra() {
    Map<String, Object> row = buildRow("asset_004", "title", "user1", 45413.79, 50000000.0, "approved", "生活", "北京", "千川", "720x1280", "1500.00");
    Asset asset = adapter.convert(row);
    @SuppressWarnings("unchecked")
    Map<String, Object> extra = (Map<String, Object>) asset.getExtra();
    assertThat(extra).containsKey("spend");
    assertThat(extra.get("spend")).isEqualTo("1500.00");
  }

  // ── rawRecord 保留原始行 ──

  @Test
  void convert_rawRecord_preserved() {
    Map<String, Object> row = buildRow("asset_001", "title", "user1", 45413.79, 106268720.0, "approved", "生活", "北京", "千川", "720x1280", "N/A");
    Asset asset = adapter.convert(row);
    assertThat(asset.getRawRecord()).isNotNull();
  }
}
