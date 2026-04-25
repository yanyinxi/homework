package com.homework.asset.api;

import com.homework.asset.api.dto.ApiEnvelope;
import com.homework.asset.api.dto.CursorPage;
import com.homework.asset.api.dto.PagedResponse;
import com.homework.asset.config.AssetMetrics;
import com.homework.asset.service.AssetQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.Parameters;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Map;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Assets", description = "视频素材数据查询接口")
@RestController
@RequestMapping("/api/v1/assets")
public class AssetController {

  private final AssetQueryService queryService;
  private final AssetMetrics metrics;

  public AssetController(AssetQueryService queryService, AssetMetrics metrics) {
    this.queryService = queryService;
    this.metrics = metrics;
  }

  @Operation(
      summary = "列出素材（OFFSET 分页）",
      description =
          "支持过滤: field=v, field[eq/ne/gt/gte/lt/lte/in/like/has]=v；"
              + "排序: sort=field:dir；稀疏字段: fields=a,b,c；分页: page&page_size。"
              + "注意：大页码时性能下降，建议使用 /cursor 端点。")
  @Parameters({
    @Parameter(name = "status",            description = "等值过滤：approved | pending | rejected",                example = "approved"),
    @Parameter(name = "uploader",          description = "等值过滤或模糊匹配：uploader=张三 / uploader[like]=张",   example = "张三"),
    @Parameter(name = "file_size_bytes[lte]", description = "文件大小上限（bytes）",                              example = "524288000"),
    @Parameter(name = "tags[has]",         description = "标签包含（PostgreSQL @> 数组操作）",                    example = "节日"),
    @Parameter(name = "sort",              description = "排序：field:asc|desc，多字段逗号分隔。可用字段：uploaded_at|file_size_bytes|title|uploader|status|city", example = "uploaded_at:desc"),
    @Parameter(name = "fields",            description = "稀疏字段集：仅返回指定列，逗号分隔",                      example = "title,status,uploader"),
    @Parameter(name = "page",             description = "页码（1-indexed，默认 1）",                             example = "1"),
    @Parameter(name = "page_size",        description = "每页条数（默认 20，最大 200）",                          example = "20"),
  })
  @GetMapping
  public ApiEnvelope<PagedResponse<Map<String, Object>>> listAssets(
      @Parameter(hidden = true) @RequestParam MultiValueMap<String, String> allParams) {
    long start = System.currentTimeMillis();
    PagedResponse<Map<String, Object>> result = queryService.listAssets(allParams);
    metrics.recordListRequest(System.currentTimeMillis() - start);
    return ApiEnvelope.ok(result);
  }

  @Operation(
      summary = "列出素材（Cursor 分页）",
      description =
          "Keyset 分页，O(1) 性能，适合大数据集和无限滚动场景。"
              + "排序固定为 uploaded_at DESC, id DESC。")
  @Parameters({
    @Parameter(name = "cursor", description = "Base64 编码的游标，首次请求不传", example = ""),
    @Parameter(name = "page_size", description = "每页条数（默认 20，最大 200）", example = "20"),
    @Parameter(name = "status", description = "等值过滤：approved | pending | rejected", example = "approved"),
    @Parameter(name = "tags[has]", description = "标签包含", example = "节日"),
  })
  @GetMapping("/cursor")
  public ApiEnvelope<CursorPage<Map<String, Object>>> listAssetsByCursor(
      @Parameter(hidden = true) @RequestParam MultiValueMap<String, String> allParams,
      @Parameter(description = "游标，首次请求不传") @RequestParam(required = false) String cursor,
      @Parameter(description = "每页条数") @RequestParam(defaultValue = "20") int page_size) {
    long start = System.currentTimeMillis();
    CursorPage<Map<String, Object>> result = queryService.listAssetsByCursor(allParams, cursor, Math.min(page_size, 200));
    metrics.recordListRequest(System.currentTimeMillis() - start);
    return ApiEnvelope.ok(result);
  }

  @Operation(summary = "获取素材详情", description = "支持稀疏字段集: ?fields=title,status,uploader")
  @GetMapping("/{id}")
  public ApiEnvelope<Map<String, Object>> getAssetById(
      @Parameter(description = "素材 UUID", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
      @PathVariable String id,
      @Parameter(description = "稀疏字段集：仅返回指定列，逗号分隔", example = "title,status,uploader")
      @RequestParam(required = false) String fields) {
    long start = System.currentTimeMillis();
    Map<String, Object> result = queryService.getAssetById(id, fields);
    metrics.recordDetailRequest(System.currentTimeMillis() - start);
    return ApiEnvelope.ok(result);
  }
}
