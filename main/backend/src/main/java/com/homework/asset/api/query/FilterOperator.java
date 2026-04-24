package com.homework.asset.api.query;

import java.util.Optional;

/**
 * 过滤操作符枚举。
 *
 * <p>支持题面要求的 bracket-style 语法：field[op]=value
 */
public enum FilterOperator {
  /** 等于，如 status[eq]=approved */
  EQ("eq"),
  /** 不等于，如 status[ne]=rejected */
  NE("ne"),
  /** 大于，如 file_size_bytes[gt]=1024 */
  GT("gt"),
  /** 大于等于，如 file_size_bytes[gte]=1024 */
  GTE("gte"),
  /** 小于，如 file_size_bytes[lt]=524288000 */
  LT("lt"),
  /** 小于等于，如 file_size_bytes[lte]=524288000 */
  LTE("lte"),
  /** 枚举包含，如 status[in]=approved,pending */
  IN("in"),
  /** 模糊匹配（ILIKE），如 title[like]=冬季 */
  LIKE("like"),
  /** 数组包含，专用于 tags 字段，如 tags[has]=节日 */
  HAS("has");

  private final String bracket;

  FilterOperator(String bracket) {
    this.bracket = bracket;
  }

  public String getBracket() {
    return bracket;
  }

  public static Optional<FilterOperator> fromBracket(String bracket) {
    for (FilterOperator op : values()) {
      if (op.bracket.equalsIgnoreCase(bracket)) return Optional.of(op);
    }
    return Optional.empty();
  }
}
