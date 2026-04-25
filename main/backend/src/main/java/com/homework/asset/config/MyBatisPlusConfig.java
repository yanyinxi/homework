package com.homework.asset.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.autoconfigure.ConfigurationCustomizer;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MyBatis-Plus 配置。
 * 
 * 包含：分页插件、UUID 类型处理器、PostgreSQL text[] 类型处理器
 */
@Configuration
public class MyBatisPlusConfig {

  /**
   * 创建 MyBatis-Plus 拦截器。
   * 添加分页插件，支持 PostgreSQL 方言。
   *
   * @return MybatisPlusInterceptor 实例
   */
  @Bean
  public MybatisPlusInterceptor mybatisPlusInterceptor() {
    MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
    interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.POSTGRE_SQL));
    return interceptor;
  }

  /**
   * 创建配置定制器。
   * 注册 UUID 类型处理器和 PostgreSQL text[] 类型处理器。
   *
   * @return ConfigurationCustomizer 实例
   */
  @Bean
  public ConfigurationCustomizer configurationCustomizer() {
    return configuration -> {
      configuration.getTypeHandlerRegistry().register(UUID.class, JdbcType.OTHER,
          new BaseTypeHandler<UUID>() {
            /**
             * 设置 UUID 参数。
             *
             * @param ps PreparedStatement
             * @param i 参数索引
             * @param p UUID 值
             * @param j JDBC 类型
             * @throws SQLException SQL 异常
             */
            @Override
            public void setNonNullParameter(PreparedStatement ps, int i, UUID p, JdbcType j)
                throws SQLException {
              ps.setObject(i, p, java.sql.Types.OTHER);
            }
            /**
             * 从结果集获取 UUID（按列名）。
             *
             * @param rs ResultSet
             * @param col 列名
             * @return UUID 值
             * @throws SQLException SQL 异常
             */
            @Override
            public UUID getNullableResult(ResultSet rs, String col) throws SQLException {
              String v = rs.getString(col);
              return v != null ? UUID.fromString(v) : null;
            }
            /**
             * 从结果集获取 UUID（按列索引）。
             *
             * @param rs ResultSet
             * @param idx 列索引
             * @return UUID 值
             * @throws SQLException SQL 异常
             */
            @Override
            public UUID getNullableResult(ResultSet rs, int idx) throws SQLException {
              String v = rs.getString(idx);
              return v != null ? UUID.fromString(v) : null;
            }
            /**
             * 从存储过程获取 UUID。
             *
             * @param cs CallableStatement
             * @param idx 参数索引
             * @return UUID 值
             * @throws SQLException SQL 异常
             */
            @Override
            public UUID getNullableResult(CallableStatement cs, int idx) throws SQLException {
              String v = cs.getString(idx);
              return v != null ? UUID.fromString(v) : null;
            }
          });
      configuration.getTypeHandlerRegistry().register(PgStringArrayTypeHandler.class);
    };
  }
}
