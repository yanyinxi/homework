package com.homework.asset.api;

import com.homework.asset.api.dto.ApiEnvelope;
import com.homework.asset.api.dto.PagedResponse;
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

/** 素材只读 API 控制器。 */
@Tag(name = "Assets", description = "视频素材数据查询接口")
@RestController
@RequestMapping("/api/v1/assets")
public class AssetController {

  private final AssetQueryService queryService;

  public AssetController(AssetQueryService queryService) {
    this.queryService = queryService;
  }

  /**
   * 列出素材数据（支持多字段过滤、排序、分页、稀疏字段集）。
   *
   * <pre>
   * GET /api/v1/assets?status=approved&sort=uploaded_at:desc&page=1&page_size=20
   * GET /api/v1/assets?file_size_bytes[lte]=524288000&tags[has]=节日
   * GET /api/v1/assets?fields=title,status,uploader
   * </pre>
   */
  @Operation(
      summary = "列出素材",
      description =
          "支持过滤: field=v, field[eq/ne/gt/gte/lt/lte/in/like/has]=v；"
              + "排序: sort=field:dir；稀疏字段: fields=a,b,c；分页: page&page_size")
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
    return ApiEnvelope.ok(queryService.listAssets(allParams));
  }

  /**
   * 获取单条素材详情。
   *
   * <pre>
   * GET /api/v1/assets/{id}
   * GET /api/v1/assets/{id}?fields=title,status,uploader
   * </pre>
   */
  @Operation(summary = "获取素材详情", description = "支持稀疏字段集: ?fields=title,status,uploader")
  @GetMapping("/{id}")
  public ApiEnvelope<Map<String, Object>> getAssetById(
      @Parameter(description = "素材 UUID", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
      @PathVariable String id,
      @Parameter(description = "稀疏字段集：仅返回指定列，逗号分隔", example = "title,status,uploader")
      @RequestParam(required = false) String fields) {
    return ApiEnvelope.ok(queryService.getAssetById(id, fields));
  }
}
