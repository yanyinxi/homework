package com.homework.asset.api.query;

import com.homework.asset.api.exception.ApiException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.util.MultiValueMap;

/**
 * 查询 DSL 解析器（自研）。
 * 
 * 功能：
 * - 把 HTTP 查询参数转换为 SQL 条件和排序
 * - 支持多字段过滤、范围查询、数组包含等
 * - 所有字段和操作符走白名单防 SQL 注入
 * 
 * 支持语法：
 * - 等值：field=value / field[eq]=value
 * - 范围：field[gt/gte/lt/lte]=value
 * - 模糊：field[like]=pattern（ILIKE）
 * - 数组：tags[has]=tag1（PostgreSQL @>）
 * - 排序：sort=field1:asc,field2:desc
 * - 稀疏字段：fields=col1,col2
 * - 分页：page=1&page_size=20
 */
public class QueryDslParser {

  /** 匹配 field[op] 格式，如 file_size_bytes[lte] */
  private static final Pattern BRACKET_PATTERN = Pattern.compile("^(.+)\\[([a-z]+)\\]$");

  private static final int DEFAULT_PAGE_SIZE = 20;
  private static final int MAX_PAGE_SIZE = 200;

  private static final int MAX_IN_VALUES = 100;
  private static final long EPOCH_MILLIS_THRESHOLD = 1_000_000_000_000L;
  private static final Pattern NUMERIC_PATTERN = Pattern.compile("^-?\\d+(\\.\\d+)?$");
  private static final List<SortClause> DEFAULT_ORDER_CLAUSES =
      List.of(new SortClause("uploaded_at", "DESC"), new SortClause("id", "DESC"));

  private QueryDslParser() {}

  /**
   * 解析所有查询参数，构建 ParsedQuery。
   *
   * @param queryParams Spring 的 MultiValueMap（每个参数可能有多个值）
   * @return 解析结果
   * @throws ApiException 400 当字段名或操作符不在白名单时
   */
  public static ParsedQuery parse(MultiValueMap<String, String> queryParams) {
    Map<String, Object> params = new HashMap<>();
    List<SortClause> orderClauses = Collections.emptyList();
    List<String> fields = Collections.emptyList();
    int page = 1;
    int pageSize = DEFAULT_PAGE_SIZE;

    for (Map.Entry<String, List<String>> entry : queryParams.entrySet()) {
      String key = entry.getKey();
      String value = entry.getValue().get(0); // 取第一个值

      // 系统参数，跳过过滤解析
      if ("sort".equals(key)) {
        orderClauses = parseSort(value);
        continue;
      }
      if ("fields".equals(key)) {
        fields = parseFields(value);
        continue;
      }
      if ("page".equals(key)) {
        page = parsePositiveInt(value, "page");
        continue;
      }
      if ("page_size".equals(key)) {
        pageSize = Math.min(parsePositiveInt(value, "page_size"), MAX_PAGE_SIZE);
        continue;
      }

      // 解析过滤参数
      parseFilterParam(key, value, params);
    }

    return new ParsedQuery(params, stableOrder(orderClauses), fields, page, pageSize);
  }

  /**
   * 解析单个过滤参数，写入 params Map。
   *
   * <p>支持 {@code field=v}（等于）和 {@code field[op]=v}（带操作符）两种格式。
   */
  private static void parseFilterParam(String key, String value, Map<String, Object> params) {
    Matcher m = BRACKET_PATTERN.matcher(key);

    String fieldName;
    FilterOperator op;

    if (m.matches()) {
      // field[op] 格式
      fieldName = m.group(1);
      String opStr = m.group(2);
      op =
          FilterOperator.fromBracket(opStr)
              .orElseThrow(
                  () -> new ApiException(400, "Unknown filter operator: '" + opStr + "'"));
    } else {
      // field=v 格式，默认 EQ
      fieldName = key;
      op = FilterOperator.EQ;
    }

    FilterableField field =
        FilterableField.fromParamName(fieldName)
            .orElseThrow(
                () -> new ApiException(400, "Invalid filter field: '" + fieldName + "'"));

    String base = field.getMapKeyBase();

    switch (op) {
      case EQ -> params.put(base, value);
      case NE -> params.put(base + "Ne", value);
      case GT -> params.put(base + "Gt", parseRangeValue(value, fieldName, field));
      case GTE -> params.put(base + "Gte", parseRangeValue(value, fieldName, field));
      case LT -> params.put(base + "Lt", parseRangeValue(value, fieldName, field));
      case LTE -> params.put(base + "Lte", parseRangeValue(value, fieldName, field));
      case IN -> {
        String[] arr = value.split(",");
        if (arr.length > MAX_IN_VALUES) {
          throw new ApiException(400, "IN filter allows at most " + MAX_IN_VALUES + " values");
        }
        params.put(base + "In", arr);
      }
      case LIKE -> params.put(base + "Like", value);
      case HAS -> params.put(base + "Has", value);
    }
  }

  /**
   * 解析 sort 参数，如 "uploaded_at:desc,file_size_bytes:asc"。
   *
   * <p>字段名通过 SortableField 白名单映射到 DB 列名，防止注入。
   *
   * @return 安全的 SortClause 列表，每个 clause 的 columnName 来自枚举，不含用户原始输入
   */
  private static List<SortClause> parseSort(String sortParam) {
    if (sortParam == null || sortParam.isBlank()) return Collections.emptyList();
    List<SortClause> clauses = new ArrayList<>();
    for (String part : sortParam.split(",")) {
      String[] tokens = part.strip().split(":");
      String fieldName = tokens[0].strip();
      String direction = tokens.length > 1 ? tokens[1].strip().toUpperCase() : "ASC";

      if (!"ASC".equals(direction) && !"DESC".equals(direction)) {
        throw new ApiException(400, "Invalid sort direction: '" + direction + "'");
      }

      SortableField field =
          SortableField.fromParamName(fieldName)
              .orElseThrow(
                  () -> new ApiException(400, "Invalid sort field: '" + fieldName + "'"));

      clauses.add(new SortClause(field.getColumnName(), direction));
    }
    return Collections.unmodifiableList(clauses);
  }

  /** 解析 fields 参数，如 "title,status,uploader"，并验证每个字段在白名单中。 */
  public static List<String> parseFields(String fieldsParam) {
    if (fieldsParam == null || fieldsParam.isBlank()) return Collections.emptyList();
    List<String> result = new ArrayList<>();
    for (String f : fieldsParam.split(",")) {
      String name = f.strip();
      FilterableField field =
          FilterableField.fromParamName(name)
              .orElseThrow(() -> new ApiException(400, "Invalid return field: '" + name + "'"));
      // 转换为 Java camelCase key（与 XML 中 <choose> 的 f 对应）
      result.add(field.getMapKeyBase());
    }
    return result;
  }

  private static Object parseRangeValue(String value, String fieldName, FilterableField field) {
    if (field == FilterableField.UPLOADED_AT) {
      return parseTimestamp(value, fieldName);
    }
    return parseNumber(value, fieldName);
  }

  private static Object parseNumber(String value, String fieldName) {
    try {
      if (value.contains(".")) return Double.parseDouble(value);
      return Long.parseLong(value);
    } catch (NumberFormatException e) {
      throw new ApiException(400, "Field '" + fieldName + "' expects a numeric value: " + value);
    }
  }

  private static int parsePositiveInt(String value, String paramName) {
    try {
      int v = Integer.parseInt(value);
      if (v < 1) throw new ApiException(400, "'" + paramName + "' must be >= 1");
      return v;
    } catch (NumberFormatException e) {
      throw new ApiException(400, "'" + paramName + "' must be an integer: " + value);
    }
  }

  /**
   * 解析时间过滤值，支持：
   *
   * <ul>
   *   <li>ISO-8601：2026-04-24T11:00:00Z
   *   <li>epoch 秒：1715336373
   *   <li>epoch 毫秒：1715336373000
   *   <li>日期：2026-04-24（按 UTC 00:00:00）
   * </ul>
   */
  private static Timestamp parseTimestamp(String value, String fieldName) {
    String trimmed = value == null ? "" : value.strip();
    if (trimmed.isEmpty()) {
      throw new ApiException(400, "Field '" + fieldName + "' expects a datetime value");
    }
    if (NUMERIC_PATTERN.matcher(trimmed).matches()) {
      try {
        long epoch = Long.parseLong(trimmed.contains(".") ? trimmed.substring(0, trimmed.indexOf('.')) : trimmed);
        Instant instant =
            Math.abs(epoch) >= EPOCH_MILLIS_THRESHOLD
                ? Instant.ofEpochMilli(epoch)
                : Instant.ofEpochSecond(epoch);
        return Timestamp.from(instant);
      } catch (RuntimeException ex) {
        throw new ApiException(400, "Field '" + fieldName + "' expects a valid epoch value: " + value);
      }
    }
    try {
      return Timestamp.from(Instant.parse(trimmed));
    } catch (DateTimeParseException ignored) {
      // continue
    }
    try {
      return Timestamp.from(OffsetDateTime.parse(trimmed).toInstant());
    } catch (DateTimeParseException ignored) {
      // continue
    }
    try {
      return Timestamp.from(LocalDateTime.parse(trimmed).atZone(ZoneOffset.UTC).toInstant());
    } catch (DateTimeParseException ignored) {
      // continue
    }
    try {
      return Timestamp.from(LocalDate.parse(trimmed).atStartOfDay(ZoneOffset.UTC).toInstant());
    } catch (DateTimeParseException ignored) {
      throw new ApiException(
          400,
          "Field '" + fieldName + "' expects datetime (ISO-8601) or epoch seconds/millis: "
              + value);
    }
  }

  private static List<SortClause> stableOrder(List<SortClause> clauses) {
    if (clauses == null || clauses.isEmpty()) {
      return DEFAULT_ORDER_CLAUSES;
    }
    List<SortClause> result = new ArrayList<>(clauses);
    boolean hasIdSort = clauses.stream().anyMatch(c -> "id".equals(c.columnName()));
    if (!hasIdSort) {
      result.add(new SortClause("id", "DESC"));
    }
    return Collections.unmodifiableList(result);
  }

  /** 解析结果封装。 */
  public record ParsedQuery(
      Map<String, Object> params,
      List<SortClause> orderClauses,
      List<String> fields,
      int page,
      int pageSize) {

    public int offset() {
      return (page - 1) * pageSize;
    }
  }
}
