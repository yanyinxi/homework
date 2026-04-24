package com.homework.asset.api.query;

/** 排序子句：由 SortableField 枚举映射后的安全排序条件，不含任何用户原始输入。 */
public record SortClause(String columnName, String direction) {

  public SortClause {
    if (!"ASC".equals(direction) && !"DESC".equals(direction)) {
      throw new IllegalArgumentException("direction must be ASC or DESC");
    }
  }
}
