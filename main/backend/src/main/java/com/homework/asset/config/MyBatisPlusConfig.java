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

@Configuration
public class MyBatisPlusConfig {

  @Bean
  public MybatisPlusInterceptor mybatisPlusInterceptor() {
    MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
    interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.POSTGRE_SQL));
    return interceptor;
  }

  @Bean
  public ConfigurationCustomizer configurationCustomizer() {
    return configuration -> {
      configuration.getTypeHandlerRegistry().register(UUID.class, JdbcType.OTHER,
          new BaseTypeHandler<UUID>() {
            @Override
            public void setNonNullParameter(PreparedStatement ps, int i, UUID p, JdbcType j)
                throws SQLException {
              ps.setObject(i, p, java.sql.Types.OTHER);
            }
            @Override
            public UUID getNullableResult(ResultSet rs, String col) throws SQLException {
              String v = rs.getString(col);
              return v != null ? UUID.fromString(v) : null;
            }
            @Override
            public UUID getNullableResult(ResultSet rs, int idx) throws SQLException {
              String v = rs.getString(idx);
              return v != null ? UUID.fromString(v) : null;
            }
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
