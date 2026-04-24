package com.homework.asset.ingest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.homework.asset.ingest.normalizer.EtlNormalizeException;
import com.homework.asset.ingest.normalizer.EtlNormalizers;
import java.time.LocalDate;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class EtlNormalizersTest {

  // ═══════════════════════════════════════════════════════
  // normalizeDate / dateFromExcelSerial / dateFromUnixSeconds
  // ═══════════════════════════════════════════════════════

  @Test
  void dateFromExcelSerial_dataset1_integer() {
    LocalDate date = EtlNormalizers.dateFromExcelSerial(45540.0).atZone(ZoneOffset.UTC).toLocalDate();
    assertThat(date).isEqualTo(LocalDate.of(2024, 8, 10));
  }

  @Test
  void dateFromExcelSerial_dataset1_another() {
    LocalDate date = EtlNormalizers.dateFromExcelSerial(45455.0).atZone(ZoneOffset.UTC).toLocalDate();
    assertThat(date).isEqualTo(LocalDate.of(2024, 5, 17));
  }

  @Test
  void dateFromExcelSerial_dataset2_with_fraction() {
    LocalDate date = EtlNormalizers.dateFromExcelSerial(45413.792025).atZone(ZoneOffset.UTC).toLocalDate();
    assertThat(date).isEqualTo(LocalDate.of(2024, 4, 6));
  }

  @Test
  void dateFromUnixSeconds_dataset3() {
    LocalDate date = EtlNormalizers.dateFromUnixSeconds(1715336373.0).atZone(ZoneOffset.UTC).toLocalDate();
    assertThat(date).isEqualTo(LocalDate.of(2024, 5, 10));
  }

  @Test
  void normalizeDate_auto_detect_unix_timestamp() {
    LocalDate date = EtlNormalizers.normalizeDate(1715336373.0).atZone(ZoneOffset.UTC).toLocalDate();
    assertThat(date).isEqualTo(LocalDate.of(2024, 5, 10));
  }

  @Test
  void normalizeDate_auto_detect_excel_serial() {
    LocalDate date = EtlNormalizers.normalizeDate(45540.0).atZone(ZoneOffset.UTC).toLocalDate();
    assertThat(date).isEqualTo(LocalDate.of(2024, 8, 10));
  }

  @Test
  void dateFromExcelSerial_epoch_day1() {
    LocalDate date = EtlNormalizers.dateFromExcelSerial(1.0).atZone(ZoneOffset.UTC).toLocalDate();
    assertThat(date).isEqualTo(LocalDate.of(1900, 1, 1));
  }

  // ═══════════════════════════════════════════════════════
  // sizeFromString / sizeFromValueAndUnit / sizeFromBytes
  // ═══════════════════════════════════════════════════════

  @Test
  void sizeFromString_megabytes() {
    assertThat(EtlNormalizers.sizeFromString("63.76MB")).isEqualTo(66857206L);
  }

  @Test
  void sizeFromString_with_space() {
    assertThat(EtlNormalizers.sizeFromString("100 MB")).isEqualTo(104857600L);
  }

  @Test
  void sizeFromString_kilobytes() {
    assertThat(EtlNormalizers.sizeFromString("512KB")).isEqualTo(524288L);
  }

  @Test
  void sizeFromString_bytes() {
    assertThat(EtlNormalizers.sizeFromString("1024B")).isEqualTo(1024L);
  }

  @Test
  void sizeFromString_gigabytes() {
    assertThat(EtlNormalizers.sizeFromString("1.5GB")).isEqualTo(1610612736L);
  }

  @Test
  void sizeFromString_null_throws() {
    assertThatThrownBy(() -> EtlNormalizers.sizeFromString(null))
        .isInstanceOf(EtlNormalizeException.class);
  }

  @Test
  void sizeFromString_invalid_throws() {
    assertThatThrownBy(() -> EtlNormalizers.sizeFromString("abc"))
        .isInstanceOf(EtlNormalizeException.class)
        .hasMessageContaining("Cannot parse file size");
  }

  @Test
  void sizeFromValueAndUnit_mb() {
    assertThat(EtlNormalizers.sizeFromValueAndUnit(541.2, "MB")).isEqualTo(567489331L);
  }

  @Test
  void sizeFromValueAndUnit_kb() {
    assertThat(EtlNormalizers.sizeFromValueAndUnit(582144.0, "KB")).isEqualTo(596115456L);
  }

  @Test
  void sizeFromValueAndUnit_case_insensitive() {
    assertThat(EtlNormalizers.sizeFromValueAndUnit(1.0, "mb")).isEqualTo(1048576L);
  }

  @Test
  void sizeFromBytes_exact() {
    assertThat(EtlNormalizers.sizeFromBytes(106268720.0)).isEqualTo(106268720L);
  }

  @Test
  void sizeFromBytes_rounds_decimal() {
    assertThat(EtlNormalizers.sizeFromBytes(100.6)).isEqualTo(101L);
  }

  @Test
  void sizeFromBytes_negative_throws() {
    assertThatThrownBy(() -> EtlNormalizers.sizeFromBytes(-1.0))
        .isInstanceOf(EtlNormalizeException.class);
  }

  // ═══════════════════════════════════════════════════════
  // normalizeStatus
  // ═══════════════════════════════════════════════════════

  @Test
  void normalizeStatus_chinese_pending() {
    assertThat(EtlNormalizers.normalizeStatus("待审核")).isEqualTo("pending");
  }

  @Test
  void normalizeStatus_chinese_approved() {
    assertThat(EtlNormalizers.normalizeStatus("已通过")).isEqualTo("approved");
  }

  @Test
  void normalizeStatus_chinese_approved_short() {
    assertThat(EtlNormalizers.normalizeStatus("通过")).isEqualTo("approved");
  }

  @Test
  void normalizeStatus_chinese_rejected() {
    assertThat(EtlNormalizers.normalizeStatus("已拒绝")).isEqualTo("rejected");
  }

  @Test
  void normalizeStatus_english_pending() {
    assertThat(EtlNormalizers.normalizeStatus("pending")).isEqualTo("pending");
  }

  @Test
  void normalizeStatus_english_approved() {
    assertThat(EtlNormalizers.normalizeStatus("approved")).isEqualTo("approved");
  }

  @Test
  void normalizeStatus_english_rejected() {
    assertThat(EtlNormalizers.normalizeStatus("rejected")).isEqualTo("rejected");
  }

  @Test
  void normalizeStatus_null_returns_null() {
    assertThat(EtlNormalizers.normalizeStatus(null)).isNull();
  }

  @Test
  void normalizeStatus_blank_returns_null() {
    assertThat(EtlNormalizers.normalizeStatus("  ")).isNull();
  }

  @Test
  void normalizeStatus_trimmed_before_lookup() {
    assertThat(EtlNormalizers.normalizeStatus("  已通过  ")).isEqualTo("approved");
  }

  @Test
  void normalizeStatus_unknown_value_throws() {
    assertThatThrownBy(() -> EtlNormalizers.normalizeStatus("未知状态"))
        .isInstanceOf(EtlNormalizeException.class)
        .hasMessageContaining("Unknown status value");
  }

  // ═══════════════════════════════════════════════════════
  // normalizeTags
  // ═══════════════════════════════════════════════════════

  @Test
  void normalizeTags_semicolon_separated() {
    assertThat(EtlNormalizers.normalizeTags("节日;促销")).containsExactly("节日", "促销");
  }

  @Test
  void normalizeTags_single_tag_semicolon() {
    assertThat(EtlNormalizers.normalizeTags("节日")).containsExactly("节日");
  }

  @Test
  void normalizeTags_comma_separated() {
    assertThat(EtlNormalizers.normalizeTags("生活,搞笑,测评")).containsExactly("生活", "搞笑", "测评");
  }

  @Test
  void normalizeTags_comma_with_spaces() {
    assertThat(EtlNormalizers.normalizeTags("生活, 搞笑, 测评")).containsExactly("生活", "搞笑", "测评");
  }

  @Test
  void normalizeTags_python_list_single() {
    assertThat(EtlNormalizers.normalizeTags("['品牌']")).containsExactly("品牌");
  }

  @Test
  void normalizeTags_python_list_multiple() {
    assertThat(EtlNormalizers.normalizeTags("['测评', '搞笑']")).containsExactly("测评", "搞笑");
  }

  @Test
  void normalizeTags_python_list_three_items() {
    assertThat(EtlNormalizers.normalizeTags("['品牌', '测评', '节日']"))
        .containsExactly("品牌", "测评", "节日");
  }

  @Test
  void normalizeTags_null_returns_empty() {
    assertThat(EtlNormalizers.normalizeTags(null)).isEmpty();
  }

  @Test
  void normalizeTags_blank_returns_empty() {
    assertThat(EtlNormalizers.normalizeTags("   ")).isEmpty();
  }

  @Test
  void normalizeTags_empty_string_returns_empty() {
    assertThat(EtlNormalizers.normalizeTags("")).isEmpty();
  }

  @Test
  void normalizeTags_single_tag_no_separator() {
    assertThat(EtlNormalizers.normalizeTags("品牌")).containsExactly("品牌");
  }

  // ═══════════════════════════════════════════════════════
  // normalizePlatform
  // ═══════════════════════════════════════════════════════

  @Test
  void normalizePlatform_chinese_qianchuan() {
    assertThat(EtlNormalizers.normalizePlatform("千川")).isEqualTo("qianchuan");
  }

  @Test
  void normalizePlatform_lowercase_qianchuan() {
    assertThat(EtlNormalizers.normalizePlatform("qianchuan")).isEqualTo("qianchuan");
  }

  @Test
  void normalizePlatform_titlecase_qianchuan() {
    assertThat(EtlNormalizers.normalizePlatform("Qianchuan")).isEqualTo("qianchuan");
  }

  @Test
  void normalizePlatform_uppercase_qianchuan() {
    assertThat(EtlNormalizers.normalizePlatform("QIANCHUAN")).isEqualTo("qianchuan");
  }

  @Test
  void normalizePlatform_chinese_douyin() {
    assertThat(EtlNormalizers.normalizePlatform("抖音")).isEqualTo("douyin");
  }

  @Test
  void normalizePlatform_lowercase_douyin() {
    assertThat(EtlNormalizers.normalizePlatform("douyin")).isEqualTo("douyin");
  }

  @Test
  void normalizePlatform_na_returns_null() {
    assertThat(EtlNormalizers.normalizePlatform("N/A")).isNull();
  }

  @Test
  void normalizePlatform_na_lowercase_returns_null() {
    assertThat(EtlNormalizers.normalizePlatform("n/a")).isNull();
  }

  @Test
  void normalizePlatform_na_with_spaces_returns_null() {
    assertThat(EtlNormalizers.normalizePlatform("  N/A  ")).isNull();
  }

  @Test
  void normalizePlatform_null_returns_null() {
    assertThat(EtlNormalizers.normalizePlatform(null)).isNull();
  }

  @Test
  void normalizePlatform_blank_returns_null() {
    assertThat(EtlNormalizers.normalizePlatform("   ")).isNull();
  }

  @Test
  void normalizePlatform_empty_returns_null() {
    assertThat(EtlNormalizers.normalizePlatform("")).isNull();
  }

  @Test
  void normalizePlatform_unknown_returns_lowercase() {
    assertThat(EtlNormalizers.normalizePlatform("WeChat")).isEqualTo("wechat");
  }

  @Test
  void normalizePlatform_unknown_chinese_preserved() {
    assertThat(EtlNormalizers.normalizePlatform("微信")).isEqualTo("微信");
  }

  @Test
  void normalizePlatform_trims_whitespace() {
    assertThat(EtlNormalizers.normalizePlatform("  千川  ")).isEqualTo("qianchuan");
  }

  @Test
  void normalizePlatform_trims_whitespace_unknown() {
    assertThat(EtlNormalizers.normalizePlatform("  WeChat  ")).isEqualTo("wechat");
  }
}
