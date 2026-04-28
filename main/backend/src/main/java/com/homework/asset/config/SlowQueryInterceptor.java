package com.homework.asset.config;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.sql.Statement;
import java.util.Properties;
import org.apache.ibatis.executor.statement.StatementHandler;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.plugin.Intercepts;
import org.apache.ibatis.plugin.Invocation;
import org.apache.ibatis.plugin.Plugin;
import org.apache.ibatis.plugin.Signature;
import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.reflection.SystemMetaObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 慢查询监控拦截器。
 * 
 * 功能：监控 SQL 执行时间，超过阈值（500ms）打印 WARN 日志并记录 Prometheus 指标。
 */
@Component
@Intercepts({
  @Signature(
      type = StatementHandler.class,
      method = "update",
      args = {Statement.class}),
  @Signature(
      type = StatementHandler.class,
      method = "query",
      args = {Statement.class, org.apache.ibatis.session.ResultHandler.class})
})
public class SlowQueryInterceptor implements Interceptor {

  private static final Logger log = LoggerFactory.getLogger(SlowQueryInterceptor.class);
  private static final long SLOW_QUERY_THRESHOLD_MS = 500;

  private final Counter slowQueryCounter;
  private final Timer queryTimer;

  /**
   * 构造函数，注册 Prometheus 指标。
   *
   * @param meterRegistry Micrometer 指标注册表
   */
  public SlowQueryInterceptor(MeterRegistry meterRegistry) {
    this.slowQueryCounter = Counter.builder("db.slow.queries")
        .description("Number of slow database queries")
        .tag("threshold_ms", String.valueOf(SLOW_QUERY_THRESHOLD_MS))
        .register(meterRegistry);

    this.queryTimer = Timer.builder("db.query.duration")
        .description("Database query duration")
        .register(meterRegistry);
  }

  /**
   * 拦截 SQL 执行，记录执行时间。
   * 超过 500ms 的查询标记为慢查询，打印 WARN 日志并记录指标。
   *
   * @param invocation MyBatis 调用上下文
   * @return SQL 执行结果
   * @throws Throwable 执行异常
   */
  @Override
  public Object intercept(Invocation invocation) throws Throwable {
    long startTime = System.currentTimeMillis();
    Object result = invocation.proceed();
    long duration = System.currentTimeMillis() - startTime;

    StatementHandler statementHandler = (StatementHandler) invocation.getTarget();
    MetaObject metaObject = SystemMetaObject.forObject(statementHandler);

    while (metaObject.hasGetter("h")) {
      Object h = metaObject.getValue("h");
      metaObject = SystemMetaObject.forObject(h);
    }

    BoundSql boundSql = statementHandler.getBoundSql();
    String sql = boundSql.getSql();

    sql = sql.replaceAll("\\s+", " ").trim();
    if (sql.length() > 200) {
      sql = sql.substring(0, 200) + "...";
    }

    queryTimer.record(duration, java.util.concurrent.TimeUnit.MILLISECONDS);

    if (duration > SLOW_QUERY_THRESHOLD_MS) {
      slowQueryCounter.increment();

      MappedStatement mappedStatement = (MappedStatement) metaObject.getValue("mappedStatement");
      String statementId = mappedStatement != null ? mappedStatement.getId() : "unknown";

      log.warn("[SLOW QUERY] {}ms | {} | {}", duration, statementId, sql);
    } else {
      log.debug("[SQL] {}ms | {}", duration, sql);
    }

    return result;
  }

  /**
   * 包装目标对象，创建代理。
   *
   * @param target 目标对象
   * @return 代理对象
   */
  @Override
  public Object plugin(Object target) {
    return Plugin.wrap(target, this);
  }

  /**
   * 设置插件属性（当前未使用）。
   *
   * @param properties 属性配置
   */
  @Override
  public void setProperties(Properties properties) {}
}
