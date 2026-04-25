package com.homework.asset.config;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
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

  public SlowQueryInterceptor(MeterRegistry meterRegistry) {
    this.slowQueryCounter = Counter.builder("db.slow.queries")
        .description("Number of slow database queries")
        .tag("threshold_ms", String.valueOf(SLOW_QUERY_THRESHOLD_MS))
        .register(meterRegistry);

    this.queryTimer = Timer.builder("db.query.duration")
        .description("Database query duration")
        .register(meterRegistry);
  }

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

  @Override
  public Object plugin(Object target) {
    return Plugin.wrap(target, this);
  }

  @Override
  public void setProperties(Properties properties) {}
}
