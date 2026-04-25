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

  /**
   * 设置非空参数。
   * 将 Java List&lt;String&gt; 转换为 PostgreSQL text[] 数组。
   *
   * @param ps PreparedStatement
   * @param i 参数索引
   * @param parameter List&lt;String&gt; 参数
   * @param jdbcType JDBC 类型
   * @throws SQLException SQL 异常
   */
  @Override
  public void setNonNullParameter(
      PreparedStatement ps, int i, List<String> parameter, JdbcType jdbcType) throws SQLException {
    Array array = ps.getConnection().createArrayOf("text", parameter.toArray(new String[0]));
    ps.setArray(i, array);
  }

  /**
   * 从结果集获取数组（按列名）。
   *
   * @param rs ResultSet
   * @param columnName 列名
   * @return List&lt;String&gt; 结果
   * @throws SQLException SQL 异常
   */
  @Override
  public List<String> getNullableResult(ResultSet rs, String columnName) throws SQLException {
    return toList(rs.getArray(columnName));
  }

  /**
   * 从结果集获取数组（按列索引）。
   *
   * @param rs ResultSet
   * @param columnIndex 列索引
   * @return List&lt;String&gt; 结果
   * @throws SQLException SQL 异常
   */
  @Override
  public List<String> getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
    return toList(rs.getArray(columnIndex));
  }

  /**
   * 从存储过程获取数组。
   *
   * @param cs CallableStatement
   * @param columnIndex 参数索引
   * @return List&lt;String&gt; 结果
   * @throws SQLException SQL 异常
   */
  @Override
  public List<String> getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
    return toList(cs.getArray(columnIndex));
  }

  /**
   * 将 JDBC Array 转换为 List&lt;String&gt;。
   *
   * @param array JDBC Array 对象
   * @return List&lt;String&gt; 结果，null 或空数组返回空列表
   * @throws SQLException SQL 异常
   */
  private List<String> toList(Array array) throws SQLException {
    if (array == null) return Collections.emptyList();
    Object[] arr = (Object[]) array.getArray();
    return Arrays.stream(arr).map(Object::toString).toList();
  }
}
