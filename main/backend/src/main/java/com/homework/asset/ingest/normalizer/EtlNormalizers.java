package com.homework.asset.ingest.normalizer;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * ETL 归一化工具集：把三份异构数据集的日期、大小、状态、标签、平台统一转换为标准格式。
 *
 * <p>所有方法均为纯函数（无副作用），可直接单元测试。
 */
public final class EtlNormalizers {

  private EtlNormalizers() {}

  // ─────────────────────────── 日期归一化 ───────────────────────────

  /**
   * 数据集1: Excel 序列号整数，如 45540。
   * 数据集2: Excel 序列号含小数，如 45413.792（含时间）。
   * 数据集3: Unix 秒时间戳，如 1715336373。
   *
   * <p>判断：值 >= 1e9 认为是 Unix 时间戳，否则为 Excel 序列号。
   */
  public static Instant normalizeDate(double raw) {
    if (raw >= UNIX_TIMESTAMP_THRESHOLD) {
      return dateFromUnixSeconds(raw);
    }
    return dateFromExcelSerial(raw);
  }

  /**
   * 从 Excel 日期序列号转换（处理 Excel 虚构的 1900-02-29 闰年 bug）。
   * 序列号 <= 59 使用基准 1899-12-31，> 59 使用基准 1899-12-04。
   */
  public static Instant dateFromExcelSerial(double serial) {
    long days = (long) serial;
    double timeFraction = serial - days;
    long daysForDate = (timeFraction > 0) ? days + 1 : days;
    LocalDate base = (daysForDate <= EXCEL_LEAP_BUG_THRESHOLD) ? EXCEL_EPOCH_EARLY : EXCEL_EPOCH_LATE;
    LocalDate date = base.plusDays(daysForDate);
    long secondsInDay = Math.round(timeFraction * 86400);
    return date.atStartOfDay(ZoneOffset.UTC).toInstant().plusSeconds(secondsInDay);
  }

  public static Instant dateFromUnixSeconds(double unixSeconds) {
    return Instant.ofEpochSecond((long) unixSeconds);
  }

  private static final LocalDate EXCEL_EPOCH_EARLY = LocalDate.of(1899, 12, 31);
  // 补偿 Excel 错误地将 1900 视为闰年（序列号 60 = 虚构的 1900-02-29）
  private static final LocalDate EXCEL_EPOCH_LATE = LocalDate.of(1899, 12, 4);
  private static final long EXCEL_LEAP_BUG_THRESHOLD = 59L;
  private static final double UNIX_TIMESTAMP_THRESHOLD = 1_000_000_000.0;

  // ─────────────────────────── 文件大小归一化 ───────────────────────────

  /** 数据集1：从字符串解析，如 "63.76MB" → 66847825 bytes。 */
  public static long sizeFromString(String raw) {
    if (raw == null || raw.isBlank()) {
      throw new EtlNormalizeException("File size string is null or blank");
    }
    Matcher m = SIZE_STRING_PATTERN.matcher(raw.strip());
    if (!m.find()) {
      throw new EtlNormalizeException("Cannot parse file size: '" + raw + "'");
    }
    return sizeToBytes(new BigDecimal(m.group(1)), m.group(2).toUpperCase());
  }

  /** 数据集3：从数值 + 单位列解析，如 (541.2, "MB") → bytes。 */
  public static long sizeFromValueAndUnit(double value, String unit) {
    if (unit == null || unit.isBlank()) {
      throw new EtlNormalizeException("Size unit is null or blank");
    }
    return sizeToBytes(BigDecimal.valueOf(value), unit.strip().toUpperCase());
  }

  /** 数据集2：file_size_bytes 已是字节，取整。 */
  public static long sizeFromBytes(double bytes) {
    if (bytes < 0) {
      throw new EtlNormalizeException("File size bytes cannot be negative: " + bytes);
    }
    return Math.round(bytes);
  }

  private static long sizeToBytes(BigDecimal value, String unit) {
    BigDecimal multiplier = switch (unit) {
      case "B"  -> BigDecimal.ONE;
      case "KB" -> BigDecimal.valueOf(1024L);
      case "MB" -> BigDecimal.valueOf(1024L * 1024);
      case "GB" -> BigDecimal.valueOf(1024L * 1024 * 1024);
      case "TB" -> BigDecimal.valueOf(1024L * 1024 * 1024 * 1024);
      default   -> throw new EtlNormalizeException("Unknown size unit: '" + unit + "'");
    };
    return value.multiply(multiplier).setScale(0, RoundingMode.HALF_UP).longValueExact();
  }

  private static final Pattern SIZE_STRING_PATTERN =
      Pattern.compile("([\\d.]+)\\s*(B|KB|MB|GB|TB)", Pattern.CASE_INSENSITIVE);

  // ─────────────────────────── 审核状态归一化 ───────────────────────────

  /**
   * 归一化审核状态：中文/英文/混合大小写 → pending/approved/rejected。
   * 原始值为 null/空时返回 null。
   */
  public static String normalizeStatus(String raw) {
    if (raw == null || raw.isBlank()) return null;
    String trimmed = raw.strip();
    // Try exact match (Chinese values) then case-insensitive (English variants)
    String canonical = STATUS_MAPPING.get(trimmed);
    if (canonical == null) canonical = STATUS_MAPPING.get(trimmed.toLowerCase());
    if (canonical == null) {
      throw new EtlNormalizeException("Unknown status value: '" + trimmed + "'");
    }
    return canonical;
  }

  // Chinese keys: exact match. English keys: lowercase (lookup done case-insensitively).
  // "reject" covers "Reject" variant found in dataset3.
  private static final Map<String, String> STATUS_MAPPING = Map.of(
      "待审核",    "pending",
      "已通过",    "approved",
      "通过",      "approved",
      "已拒绝",    "rejected",
      "pending",   "pending",
      "approved",  "approved",
      "rejected",  "rejected",
      "reject",    "rejected");

  // ─────────────────────────── 标签归一化 ───────────────────────────

  /**
   * 数据集1：分号分隔 "节日;促销"。
   * 数据集2：逗号分隔 "生活,搞笑"。
   * 数据集3：Python list 字符串 "['品牌', '测评']"（正则提取，不使用 eval）。
   */
  public static List<String> normalizeTags(String raw) {
    if (raw == null || raw.isBlank()) return Collections.emptyList();
    String trimmed = raw.strip();
    if (trimmed.startsWith("[")) return parsePythonList(trimmed);
    if (trimmed.contains(";")) return splitAndClean(trimmed, ";");
    if (trimmed.contains(",")) return splitAndClean(trimmed, ",");
    return List.of(trimmed);
  }

  private static List<String> parsePythonList(String raw) {
    Matcher m = PYTHON_LIST_ITEM.matcher(raw);
    List<String> result = new java.util.ArrayList<>();
    while (m.find()) {
      String item = m.group(1);
      if (item != null && !item.isEmpty()) result.add(item.replace("\\'", "'").replace("\\\\", "\\"));
    }
    return Collections.unmodifiableList(result);
  }

  private static List<String> splitAndClean(String raw, String delimiter) {
    return Arrays.stream(raw.split(delimiter))
        .map(String::strip)
        .filter(s -> !s.isEmpty())
        .collect(Collectors.toUnmodifiableList());
  }

  private static final Pattern PYTHON_LIST_ITEM = Pattern.compile("'((?:[^'\\\\]|\\\\.)*)'");

  // ─────────────────────────── 投放平台归一化 ───────────────────────────

  /**
   * 归一化平台名称：千川/qianchuan → "qianchuan"，抖音/douyin → "douyin"。
   * null/空/"N/A" 返回 null；未知平台小写后保留。
   */
  public static String normalizePlatform(String raw) {
    if (raw == null || raw.isBlank() || "N/A".equalsIgnoreCase(raw.strip())) return null;
    String trimmed = raw.strip();
    String canonical = PLATFORM_MAPPING.get(trimmed);
    return canonical != null ? canonical : trimmed.toLowerCase();
  }

  private static final Map<String, String> PLATFORM_MAPPING = Map.of(
      "千川",      "qianchuan",
      "qianchuan", "qianchuan",
      "Qianchuan", "qianchuan",
      "QIANCHUAN", "qianchuan",
      "抖音",      "douyin",
      "douyin",    "douyin");
}
