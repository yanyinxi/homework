package com.homework.asset.ingest.adapter;

import static org.assertj.core.api.Assertions.assertThat;

import com.homework.asset.domain.entity.Asset;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Dataset1Adapter 单元测试。
 *
 * <p>数据集1 字段特点：中文字段名，Excel 日期序列号（整数），字符串大小（"63.76MB"），
 * 中文审核状态，分号分隔标签，含审核人/备注/城市，无平台字段。
 */
class Dataset1AdapterTest {

  private final Dataset1Adapter adapter = new Dataset1Adapter();

  /** 构建完整的数据集1行（模拟 ExcelReader 读出的 Map） */
  private Map<String, Object> buildRow(
      String id,
      String title,
      String uploader,
      Object uploadDate,
      String fileSize,
      String status,
      String tags,
      String city,
      String reviewer,
      String remark) {
    Map<String, Object> row = new HashMap<>();
    row.put("素材编号", id);
    row.put("标题", title);
    row.put("上传人", uploader);
    row.put("上传日期", uploadDate);
    row.put("文件大小(MB)", fileSize);
    row.put("审核状态", status);
    row.put("标签", tags);
    row.put("所在城市", city);
    row.put("审核人", reviewer);
    row.put("备注", remark);
    return row;
  }

  // ── 基础字段映射 ──

  @Test
  void convert_sourceDataset_is_1() {
    Map<String, Object> row = buildRow("A0001", "测试", "张三", 45540.0, "10.0MB", "已通过", "节日", "北京", null, null);
    Asset asset = adapter.convert(row);
    assertThat(asset.getSourceDataset()).isEqualTo(1);
  }

  @Test
  void convert_sourceId_maps_from_素材编号() {
    Map<String, Object> row = buildRow("A0001", "测试", "张三", 45540.0, "10.0MB", "已通过", "节日", "北京", null, null);
    Asset asset = adapter.convert(row);
    assertThat(asset.getSourceId()).isEqualTo("A0001");
  }

  @Test
  void convert_title_maps_correctly() {
    Map<String, Object> row = buildRow("A0001", "节日促销素材", "张三", 45540.0, "10.0MB", "已通过", "节日", "北京", null, null);
    Asset asset = adapter.convert(row);
    assertThat(asset.getTitle()).isEqualTo("节日促销素材");
  }

  @Test
  void convert_uploader_maps_correctly() {
    Map<String, Object> row = buildRow("A0001", "测试", "李四", 45540.0, "10.0MB", "待审核", "节日", "上海", null, null);
    Asset asset = adapter.convert(row);
    assertThat(asset.getUploader()).isEqualTo("李四");
  }

  // ── Excel 日期序列号（整数）→ Instant ──

  @Test
  void convert_uploadedAt_from_excel_serial_integer() {
    // 45540 = 2024-08-10
    Map<String, Object> row = buildRow("A0001", "测试", "张三", 45540.0, "10.0MB", "已通过", "节日", "北京", null, null);
    Asset asset = adapter.convert(row);
    assertThat(asset.getUploadedAt()).isNotNull();
    LocalDate date = asset.getUploadedAt().atZone(ZoneOffset.UTC).toLocalDate();
    assertThat(date).isEqualTo(LocalDate.of(2024, 8, 10));
  }

  @Test
  void convert_uploadedAt_from_excel_serial_another_date() {
    // 45455 = 2024-05-17
    Map<String, Object> row = buildRow("A0002", "测试2", "王五", 45455.0, "20.0MB", "待审核", "促销", "广州", null, null);
    Asset asset = adapter.convert(row);
    LocalDate date = asset.getUploadedAt().atZone(ZoneOffset.UTC).toLocalDate();
    assertThat(date).isEqualTo(LocalDate.of(2024, 5, 17));
  }

  // ── 字符串文件大小（"63.76MB"）→ bytes ──

  @Test
  void convert_fileSizeBytes_from_mb_string() {
    // 10.0MB = 10 * 1024 * 1024 = 10485760 bytes
    Map<String, Object> row = buildRow("A0001", "测试", "张三", 45540.0, "10.0MB", "已通过", "节日", "北京", null, null);
    Asset asset = adapter.convert(row);
    assertThat(asset.getFileSizeBytes()).isEqualTo(10L * 1024 * 1024);
  }

  @Test
  void convert_fileSizeBytes_fractional_mb() {
    // 63.76MB → 66847825 bytes（BigDecimal 精确计算）
    Map<String, Object> row = buildRow("A0001", "测试", "张三", 45540.0, "63.76MB", "已通过", "节日", "北京", null, null);
    Asset asset = adapter.convert(row);
    // 63.76 * 1024 * 1024 = 66847129.6 → 四舍五入 = 66847130
    assertThat(asset.getFileSizeBytes()).isGreaterThan(60L * 1024 * 1024);
    assertThat(asset.getFileSizeBytes()).isLessThan(70L * 1024 * 1024);
  }

  // ── 审核状态（中文）→ canonical code ──

  @Test
  void convert_status_chinese_pending() {
    Map<String, Object> row = buildRow("A0001", "测试", "张三", 45540.0, "10.0MB", "待审核", "节日", "北京", null, null);
    Asset asset = adapter.convert(row);
    assertThat(asset.getStatus()).isEqualTo("pending");
  }

  @Test
  void convert_status_chinese_approved() {
    Map<String, Object> row = buildRow("A0001", "测试", "张三", 45540.0, "10.0MB", "已通过", "节日", "北京", null, null);
    Asset asset = adapter.convert(row);
    assertThat(asset.getStatus()).isEqualTo("approved");
  }

  @Test
  void convert_status_chinese_rejected() {
    Map<String, Object> row = buildRow("A0001", "测试", "张三", 45540.0, "10.0MB", "已拒绝", "节日", "北京", null, null);
    Asset asset = adapter.convert(row);
    assertThat(asset.getStatus()).isEqualTo("rejected");
  }

  // ── 标签（分号分隔）→ List<String> ──

  @Test
  void convert_tags_semicolon_separated() {
    Map<String, Object> row = buildRow("A0001", "测试", "张三", 45540.0, "10.0MB", "已通过", "节日;促销", "北京", null, null);
    Asset asset = adapter.convert(row);
    assertThat(asset.getTags()).containsExactly("节日", "促销");
  }

  @Test
  void convert_tags_single_tag() {
    Map<String, Object> row = buildRow("A0001", "测试", "张三", 45540.0, "10.0MB", "已通过", "节日", "北京", null, null);
    Asset asset = adapter.convert(row);
    assertThat(asset.getTags()).containsExactly("节日");
  }

  // ── 审核人/备注/城市 ──

  @Test
  void convert_reviewer_and_remark_present() {
    Map<String, Object> row = buildRow("A0001", "测试", "张三", 45540.0, "10.0MB", "已通过", "节日", "北京", "李审核", "高质量素材");
    Asset asset = adapter.convert(row);
    assertThat(asset.getReviewer()).isEqualTo("李审核");
    assertThat(asset.getRemark()).isEqualTo("高质量素材");
  }

  @Test
  void convert_reviewer_null_when_blank() {
    Map<String, Object> row = buildRow("A0001", "测试", "张三", 45540.0, "10.0MB", "待审核", "节日", "北京", "", null);
    Asset asset = adapter.convert(row);
    assertThat(asset.getReviewer()).isNull();
    assertThat(asset.getRemark()).isNull();
  }

  @Test
  void convert_city_maps_correctly() {
    Map<String, Object> row = buildRow("A0001", "测试", "张三", 45540.0, "10.0MB", "已通过", "节日", "深圳", null, null);
    Asset asset = adapter.convert(row);
    assertThat(asset.getCity()).isEqualTo("深圳");
  }

  // ── 无平台字段（数据集1） ──

  @Test
  void convert_platform_is_null() {
    Map<String, Object> row = buildRow("A0001", "测试", "张三", 45540.0, "10.0MB", "已通过", "节日", "北京", null, null);
    Asset asset = adapter.convert(row);
    assertThat(asset.getPlatform()).isNull();
  }

  // ── rawRecord 保留原始行 ──

  @Test
  void convert_rawRecord_preserved() {
    Map<String, Object> row = buildRow("A0001", "测试", "张三", 45540.0, "10.0MB", "已通过", "节日", "北京", null, null);
    Asset asset = adapter.convert(row);
    assertThat(asset.getRawRecord()).isNotNull();
    assertThat(asset.getRawRecord()).isInstanceOf(Map.class);
  }
}
