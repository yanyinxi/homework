package com.homework.asset.api;

import com.homework.asset.api.dto.ApiEnvelope;
import com.homework.asset.service.AssetStatsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 统计聚合接口：对应作业要求的三条指定查询。 */
@Tag(name = "Stats", description = "素材统计聚合接口（对应作业三条指定查询）")
@RestController
@RequestMapping("/api/v1/stats")
public class StatsController {

  private final AssetStatsService statsService;

  public StatsController(AssetStatsService statsService) {
    this.statsService = statsService;
  }

  /** Q1：审核已通过素材中，各上传人的平均文件大小。 */
  @Operation(summary = "Q1 各上传人平均文件大小")
  @GetMapping("/uploader-avg-size")
  public ApiEnvelope<List<Map<String, Object>>> uploaderAvgSize() {
    return ApiEnvelope.ok(statsService.uploaderAvgSize());
  }

  /** Q2：按标签统计素材数量，Top N。 */
  @Operation(summary = "Q2 标签 Top N", description = "返回出现频次最高的 N 个标签及其计数（UNNEST 展开 text[] 后聚合）")
  @GetMapping("/top-tags")
  public ApiEnvelope<List<Map<String, Object>>> topTags(
      @Parameter(description = "返回 Top N 标签，默认 5，最大 50", example = "5")
      @RequestParam(name = "topN", defaultValue = "5") int topN) {
    return ApiEnvelope.ok(statsService.topTags(topN));
  }

  /** Q3：各投放平台的审核通过率。 */
  @Operation(summary = "Q3 各平台审核通过率")
  @GetMapping("/platform-approval")
  public ApiEnvelope<List<Map<String, Object>>> platformApproval() {
    return ApiEnvelope.ok(statsService.platformApprovalRate());
  }
}
