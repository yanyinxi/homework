package com.homework.asset.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.homework.asset.api.exception.ApiException;
import com.homework.asset.api.query.QueryDslParser;
import com.homework.asset.api.query.QueryDslParser.ParsedQuery;
import java.sql.Timestamp;
import org.junit.jupiter.api.Test;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

class QueryDslParserTest {

  // ── 基础过滤 ──

  @Test
  void parse_simple_eq() {
    MultiValueMap<String, String> params = of("status", "approved");
    ParsedQuery q = QueryDslParser.parse(params);
    assertThat(q.params()).containsEntry("status", "approved");
  }

  @Test
  void parse_bracket_eq() {
    MultiValueMap<String, String> params = of("status[eq]", "pending");
    ParsedQuery q = QueryDslParser.parse(params);
    assertThat(q.params()).containsEntry("status", "pending");
  }

  @Test
  void parse_bracket_lte() {
    MultiValueMap<String, String> params = of("file_size_bytes[lte]", "524288000");
    ParsedQuery q = QueryDslParser.parse(params);
    assertThat(q.params()).containsEntry("fileSizeBytesLte", 524288000L);
  }

  @Test
  void parse_bracket_gte() {
    MultiValueMap<String, String> params = of("file_size_bytes[gte]", "1048576");
    ParsedQuery q = QueryDslParser.parse(params);
    assertThat(q.params()).containsEntry("fileSizeBytesGte", 1048576L);
  }

  @Test
  void parse_bracket_like() {
    MultiValueMap<String, String> params = of("title[like]", "冬季");
    ParsedQuery q = QueryDslParser.parse(params);
    assertThat(q.params()).containsEntry("titleLike", "冬季");
  }

  @Test
  void parse_tags_has() {
    MultiValueMap<String, String> params = of("tags[has]", "节日");
    ParsedQuery q = QueryDslParser.parse(params);
    assertThat(q.params()).containsEntry("tagsHas", "节日");
  }

  // ── 排序 ──

  @Test
  void parse_sort_single_desc() {
    MultiValueMap<String, String> params = of("sort", "uploaded_at:desc");
    ParsedQuery q = QueryDslParser.parse(params);
    assertThat(q.orderClauses()).hasSize(2);
    assertThat(q.orderClauses().get(0).columnName()).isEqualTo("uploaded_at");
    assertThat(q.orderClauses().get(0).direction()).isEqualTo("DESC");
    assertThat(q.orderClauses().get(1).columnName()).isEqualTo("id");
    assertThat(q.orderClauses().get(1).direction()).isEqualTo("DESC");
  }

  @Test
  void parse_sort_multiple() {
    MultiValueMap<String, String> params = of("sort", "uploaded_at:desc,file_size_bytes:asc");
    ParsedQuery q = QueryDslParser.parse(params);
    assertThat(q.orderClauses()).hasSize(3);
    assertThat(q.orderClauses().get(0).columnName()).isEqualTo("uploaded_at");
    assertThat(q.orderClauses().get(1).columnName()).isEqualTo("file_size_bytes");
    assertThat(q.orderClauses().get(1).direction()).isEqualTo("ASC");
    assertThat(q.orderClauses().get(2).columnName()).isEqualTo("id");
  }

  @Test
  void parse_sort_default_asc() {
    MultiValueMap<String, String> params = of("sort", "title");
    ParsedQuery q = QueryDslParser.parse(params);
    assertThat(q.orderClauses()).hasSize(2);
    assertThat(q.orderClauses().get(0).direction()).isEqualTo("ASC");
  }

  @Test
  void parse_sort_city_is_allowed() {
    MultiValueMap<String, String> params = of("sort", "city:asc");
    ParsedQuery q = QueryDslParser.parse(params);
    assertThat(q.orderClauses().get(0).columnName()).isEqualTo("city");
    assertThat(q.orderClauses().get(0).direction()).isEqualTo("ASC");
  }

  @Test
  void parse_default_sort_when_no_sort_param() {
    ParsedQuery q = QueryDslParser.parse(new LinkedMultiValueMap<>());
    assertThat(q.orderClauses()).hasSize(2);
    assertThat(q.orderClauses().get(0).columnName()).isEqualTo("uploaded_at");
    assertThat(q.orderClauses().get(0).direction()).isEqualTo("DESC");
    assertThat(q.orderClauses().get(1).columnName()).isEqualTo("id");
    assertThat(q.orderClauses().get(1).direction()).isEqualTo("DESC");
  }

  @Test
  void parse_uploadedAt_lte_iso8601() {
    MultiValueMap<String, String> params = of("uploaded_at[lte]", "2026-04-24T11:00:00Z");
    ParsedQuery q = QueryDslParser.parse(params);
    assertThat(q.params().get("uploadedAtLte")).isInstanceOf(Timestamp.class);
  }

  @Test
  void parse_uploadedAt_gte_epochSeconds() {
    MultiValueMap<String, String> params = of("uploaded_at[gte]", "1715336373");
    ParsedQuery q = QueryDslParser.parse(params);
    assertThat(q.params().get("uploadedAtGte")).isInstanceOf(Timestamp.class);
  }

  // ── 分页 ──

  @Test
  void parse_pagination() {
    MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
    params.add("page", "2");
    params.add("page_size", "50");
    ParsedQuery q = QueryDslParser.parse(params);
    assertThat(q.page()).isEqualTo(2);
    assertThat(q.pageSize()).isEqualTo(50);
    assertThat(q.offset()).isEqualTo(50);
  }

  @Test
  void parse_page_size_capped_at_200() {
    MultiValueMap<String, String> params = of("page_size", "9999");
    ParsedQuery q = QueryDslParser.parse(params);
    assertThat(q.pageSize()).isEqualTo(200);
  }

  // ── 安全：白名单拦截 ──

  @Test
  void parse_unknown_field_throws_400() {
    MultiValueMap<String, String> params = of("raw_record", "anything");
    assertThatThrownBy(() -> QueryDslParser.parse(params))
        .isInstanceOf(ApiException.class)
        .hasMessageContaining("Invalid filter field")
        .extracting(e -> ((ApiException) e).getCode())
        .isEqualTo(400);
  }

  @Test
  void parse_unknown_operator_throws_400() {
    MultiValueMap<String, String> params = of("status[script]", "'; DROP TABLE assets; --");
    assertThatThrownBy(() -> QueryDslParser.parse(params))
        .isInstanceOf(ApiException.class)
        .hasMessageContaining("Unknown filter operator")
        .extracting(e -> ((ApiException) e).getCode())
        .isEqualTo(400);
  }

  @Test
  void parse_sql_injection_attempt_treated_as_value() {
    // 合法字段 + 合法操作符，但值是 SQL 注入尝试
    // 值通过 MyBatis #{} 参数化，不会注入 SQL，只是存到 params map
    MultiValueMap<String, String> params = of("uploader", "'; DROP TABLE assets; --");
    ParsedQuery q = QueryDslParser.parse(params);
    // 值原样存入 map，由 MyBatis #{} 处理，无注入风险
    assertThat(q.params()).containsEntry("uploader", "'; DROP TABLE assets; --");
  }

  @Test
  void parse_unknown_sort_field_throws_400() {
    MultiValueMap<String, String> params = of("sort", "raw_record:desc");
    assertThatThrownBy(() -> QueryDslParser.parse(params))
        .isInstanceOf(ApiException.class)
        .hasMessageContaining("Invalid sort field");
  }

  @Test
  void parse_invalid_sort_direction_throws_400() {
    MultiValueMap<String, String> params = of("sort", "title:RANDOM");
    assertThatThrownBy(() -> QueryDslParser.parse(params))
        .isInstanceOf(ApiException.class)
        .hasMessageContaining("Invalid sort direction");
  }

  // ── 稀疏字段 ──

  @Test
  void parse_fields() {
    MultiValueMap<String, String> params = of("fields", "title,status,uploader");
    ParsedQuery q = QueryDslParser.parse(params);
    assertThat(q.fields()).containsExactly("title", "status", "uploader");
  }

  @Test
  void parse_fields_unknown_throws_400() {
    MultiValueMap<String, String> params = of("fields", "raw_record");
    assertThatThrownBy(() -> QueryDslParser.parse(params))
        .isInstanceOf(ApiException.class)
        .hasMessageContaining("Invalid return field");
  }

  // ── 工具方法 ──

  private MultiValueMap<String, String> of(String key, String value) {
    MultiValueMap<String, String> map = new LinkedMultiValueMap<>();
    map.add(key, value);
    return map;
  }
}
