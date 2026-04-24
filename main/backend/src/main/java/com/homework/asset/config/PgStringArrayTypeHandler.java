package com.homework.asset.config;

import java.sql.Array;
import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedTypes;

/**
 * PostgreSQL text[] ↔ Java List&lt;String&gt; 类型处理器。
 *
 * <p>原因：MyBatis 默认的 JacksonTypeHandler 产出 JSON 格式 ["a","b"]，PostgreSQL 需要数组格式
 * {a,b}。两种格式不兼容，直接 ::text[] cast 会失败。 此 Handler 使用 JDBC createArrayOf("text", ...) 正确传递 PG
 * 数组。
 */
@MappedTypes(List.class)
public class PgStringArrayTypeHandler extends BaseTypeHandler<List<String>> {

  @Override
  public void setNonNullParameter(
      PreparedStatement ps, int i, List<String> parameter, JdbcType jdbcType) throws SQLException {
    Array array = ps.getConnection().createArrayOf("text", parameter.toArray(new String[0]));
    ps.setArray(i, array);
  }

  @Override
  public List<String> getNullableResult(ResultSet rs, String columnName) throws SQLException {
    return toList(rs.getArray(columnName));
  }

  @Override
  public List<String> getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
    return toList(rs.getArray(columnIndex));
  }

  @Override
  public List<String> getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
    return toList(cs.getArray(columnIndex));
  }

  private List<String> toList(Array array) throws SQLException {
    if (array == null) return Collections.emptyList();
    Object[] arr = (Object[]) array.getArray();
    return Arrays.stream(arr).map(Object::toString).toList();
  }
}
