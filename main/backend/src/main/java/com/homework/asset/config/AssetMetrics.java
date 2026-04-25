package com.homework.asset.config;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.concurrent.TimeUnit;
import org.springframework.stereotype.Component;

/** 业务指标收集器。为 Prometheus 暴露自定义指标。 */
@Component
public class AssetMetrics {

  private final Counter assetListCounter;
  private final Timer assetListTimer;
  private final Counter assetDetailCounter;
  private final Timer assetDetailTimer;
  private final Counter statsQueryCounter;
  private final Timer statsQueryTimer;
  private final Counter uploadCounter;
  private final Timer uploadTimer;
  private final Counter deleteCounter;
  private final Timer deleteTimer;

  public AssetMetrics(MeterRegistry registry) {
    this.assetListCounter =
        Counter.builder("asset_api_requests_total")
            .tag("endpoint", "list")
            .tag("method", "GET")
            .description("Asset list API request count")
            .register(registry);

    this.assetListTimer =
        Timer.builder("asset_api_duration_seconds")
            .tag("endpoint", "list")
            .description("Asset list API latency")
            .register(registry);

    this.assetDetailCounter =
        Counter.builder("asset_api_requests_total")
            .tag("endpoint", "detail")
            .tag("method", "GET")
            .description("Asset detail API request count")
            .register(registry);

    this.assetDetailTimer =
        Timer.builder("asset_api_duration_seconds")
            .tag("endpoint", "detail")
            .description("Asset detail API latency")
            .register(registry);

    this.statsQueryCounter =
        Counter.builder("asset_api_requests_total")
            .tag("endpoint", "stats")
            .tag("method", "GET")
            .description("Stats API request count")
            .register(registry);

    this.statsQueryTimer =
        Timer.builder("asset_api_duration_seconds")
            .tag("endpoint", "stats")
            .description("Stats API latency")
            .register(registry);

    this.uploadCounter =
        Counter.builder("asset_api_requests_total")
            .tag("endpoint", "upload")
            .tag("method", "POST")
            .description("Upload API request count")
            .register(registry);

    this.uploadTimer =
        Timer.builder("asset_api_duration_seconds")
            .tag("endpoint", "upload")
            .description("Upload API latency")
            .register(registry);

    this.deleteCounter =
        Counter.builder("asset_api_requests_total")
            .tag("endpoint", "delete")
            .tag("method", "DELETE")
            .description("Delete API request count")
            .register(registry);

    this.deleteTimer =
        Timer.builder("asset_api_duration_seconds")
            .tag("endpoint", "delete")
            .description("Delete API latency")
            .register(registry);
  }

  /** 记录列表查询请求 */
  public void recordListRequest(long durationMs) {
    assetListCounter.increment();
    assetListTimer.record(durationMs, TimeUnit.MILLISECONDS);
  }

  /** 记录详情查询请求 */
  public void recordDetailRequest(long durationMs) {
    assetDetailCounter.increment();
    assetDetailTimer.record(durationMs, TimeUnit.MILLISECONDS);
  }

  /** 记录统计查询请求 */
  public void recordStatsRequest(long durationMs) {
    statsQueryCounter.increment();
    statsQueryTimer.record(durationMs, TimeUnit.MILLISECONDS);
  }

  /** 记录上传请求 */
  public void recordUploadRequest(long durationMs) {
    uploadCounter.increment();
    uploadTimer.record(durationMs, TimeUnit.MILLISECONDS);
  }

  /** 记录删除请求 */
  public void recordDeleteRequest(long durationMs) {
    deleteCounter.increment();
    deleteTimer.record(durationMs, TimeUnit.MILLISECONDS);
  }
}
