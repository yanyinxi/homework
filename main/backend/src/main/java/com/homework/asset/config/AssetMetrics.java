package com.homework.asset.config;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.concurrent.TimeUnit;
import org.springframework.stereotype.Component;

/**
 * 业务指标收集器。为 Prometheus 暴露自定义指标。
 * 
 * <p>指标命名规范（Prometheus 最佳实践）：
 * 
 * <ul>
 *   <li><b>asset_api_requests_total</b> - API 请求总数计数器
 *       <ul>
 *         <li>用途：统计各 API 端点的累计请求次数</li>
 *         <li>标签：endpoint（端点名称）、method（HTTP 方法）</li>
 *         <li>示例：asset_api_requests_total{endpoint="list",method="GET"} 1523</li>
 *       </ul>
 *   </li>
 *   <li><b>asset_api_duration_seconds</b> - API 响应延迟计时器（单位：秒）
 *       <ul>
 *         <li>用途：统计各 API 端点的响应时间分布</li>
 *         <li>标签：endpoint（端点名称）</li>
 *         <li>示例：asset_api_duration_seconds_sum{endpoint="list"} 15.23（总耗时）</li>
 *         <li>计算平均延迟：sum / count，再乘以 1000 转为毫秒</li>
 *       </ul>
 *   </li>
 * </ul>
 * 
 * <p>端点说明：
 * <ul>
 *   <li>list - 素材列表查询（GET /api/v1/assets）</li>
 *   <li>detail - 素材详情查询（GET /api/v1/assets/{id}）</li>
 *   <li>stats - 统计数据查询（GET /api/v1/stats）</li>
 *   <li>upload - 素材上传（POST /api/v1/assets/upload）</li>
 *   <li>delete - 素材删除（DELETE /api/v1/assets/{id}）</li>
 * </ul>
 */
@Component
public class AssetMetrics {

  /** 素材列表查询请求计数器 */
  private final Counter assetListCounter;
  /** 素材列表查询延迟计时器 */
  private final Timer assetListTimer;
  /** 素材详情查询请求计数器 */
  private final Counter assetDetailCounter;
  /** 素材详情查询延迟计时器 */
  private final Timer assetDetailTimer;
  /** 统计查询请求计数器 */
  private final Counter statsQueryCounter;
  /** 统计查询延迟计时器 */
  private final Timer statsQueryTimer;
  /** 上传请求计数器 */
  private final Counter uploadCounter;
  /** 上传延迟计时器 */
  private final Timer uploadTimer;
  /** 删除请求计数器 */
  private final Counter deleteCounter;
  /** 删除延迟计时器 */
  private final Timer deleteTimer;

  /**
   * 构造函数，注册所有 Prometheus 指标。
   * 为每个 API 端点创建请求计数器和延迟计时器。
   *
   * @param registry Micrometer 指标注册表
   */
  public AssetMetrics(MeterRegistry registry) {
    // 素材列表查询指标（GET /api/v1/assets）
    this.assetListCounter =
        Counter.builder("asset_api_requests_total")
            .tag("endpoint", "list")
            .tag("method", "GET")
            .description("Asset list API request count - 素材列表查询请求次数")
            .register(registry);

    this.assetListTimer =
        Timer.builder("asset_api_duration_seconds")
            .tag("endpoint", "list")
            .description("Asset list API latency - 素材列表查询响应时间（秒）")
            .register(registry);

    // 素材详情查询指标（GET /api/v1/assets/{id}）
    this.assetDetailCounter =
        Counter.builder("asset_api_requests_total")
            .tag("endpoint", "detail")
            .tag("method", "GET")
            .description("Asset detail API request count - 素材详情查询请求次数")
            .register(registry);

    this.assetDetailTimer =
        Timer.builder("asset_api_duration_seconds")
            .tag("endpoint", "detail")
            .description("Asset detail API latency - 素材详情查询响应时间（秒）")
            .register(registry);

    // 统计查询指标（GET /api/v1/stats）
    this.statsQueryCounter =
        Counter.builder("asset_api_requests_total")
            .tag("endpoint", "stats")
            .tag("method", "GET")
            .description("Stats API request count - 统计查询请求次数")
            .register(registry);

    this.statsQueryTimer =
        Timer.builder("asset_api_duration_seconds")
            .tag("endpoint", "stats")
            .description("Stats API latency - 统计查询响应时间（秒）")
            .register(registry);

    // 上传指标（POST /api/v1/assets/upload）
    this.uploadCounter =
        Counter.builder("asset_api_requests_total")
            .tag("endpoint", "upload")
            .tag("method", "POST")
            .description("Upload API request count - 上传请求次数")
            .register(registry);

    this.uploadTimer =
        Timer.builder("asset_api_duration_seconds")
            .tag("endpoint", "upload")
            .description("Upload API latency - 上传响应时间（秒）")
            .register(registry);

    // 删除指标（DELETE /api/v1/assets/{id}）
    this.deleteCounter =
        Counter.builder("asset_api_requests_total")
            .tag("endpoint", "delete")
            .tag("method", "DELETE")
            .description("Delete API request count - 删除请求次数")
            .register(registry);

    this.deleteTimer =
        Timer.builder("asset_api_duration_seconds")
            .tag("endpoint", "delete")
            .description("Delete API latency - 删除响应时间（秒）")
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
