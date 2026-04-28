package com.homework.asset.api;

import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.homework.asset.api.dto.ApiEnvelope;
import com.homework.asset.api.dto.DeleteBatchRequest;
import com.homework.asset.api.exception.ApiException;
import com.homework.asset.api.dto.DeleteResult;
import com.homework.asset.api.dto.UploadResult;
import com.homework.asset.config.AssetMetrics;
import com.homework.asset.service.AssetCommandService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;

/**
 * 素材写入接口（上传、删除）。
 *
 * <p>所有写入操作需要认证（X-API-Key），且需要 ROLE_USER 或更高权限。
 */
@Tag(name = "Assets Command", description = "素材写入接口（上传、删除）")
@RestController
@RequestMapping("/api/v1/assets")
@SecurityRequirement(name = "ApiKeyAuth")
@Validated
public class AssetCommandController {

  private final AssetCommandService commandService;
  private final AssetMetrics metrics;

  public AssetCommandController(AssetCommandService commandService, AssetMetrics metrics) {
    this.commandService = commandService;
    this.metrics = metrics;
  }

  @Operation(
      summary = "上传 Excel 文件导入素材",
      description =
          "上传 XLS/XLSX 文件批量导入素材。"
              + "支持自动识别数据集格式（Dataset1/2/3），"
              + "幂等导入（相同 source_dataset+source_id 的记录会被更新）。"
              + "需要 ROLE_USER 权限。")
  @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @PreAuthorize("hasRole('USER')")
  public ApiEnvelope<UploadResult> uploadExcel(
      @Parameter(description = "Excel 文件（.xls 或 .xlsx）") @RequestParam("file") MultipartFile file,
      @Parameter(description = "数据集编号（1/2/3），不传则自动识别")
      @RequestParam(required = false)
      @Min(value = 1, message = "Dataset must be 1, 2, or 3")
      @Max(value = 3, message = "Dataset must be 1, 2, or 3")
      Integer dataset) {

    long start = System.currentTimeMillis();

    if (file.isEmpty()) {
      throw new ApiException(400, "File is empty");
    }

    String filename = file.getOriginalFilename();
    if (filename == null || (!filename.endsWith(".xls") && !filename.endsWith(".xlsx"))) {
      throw new ApiException(400, "Only .xls and .xlsx files are supported");
    }

    if (file.getSize() > 10 * 1024 * 1024) {
      throw new ApiException(400, "File size exceeds 10MB limit");
    }

    UploadResult result = commandService.importFromExcel(file, dataset);
    metrics.recordUploadRequest(System.currentTimeMillis() - start);

    return ApiEnvelope.ok(result);
  }

  @Operation(
      summary = "删除单条素材",
      description = "根据 ID 删除单条素材记录。需要 ROLE_ADMIN 权限。此操作不可逆。")
  @DeleteMapping("/{id}")
  @PreAuthorize("hasRole('ADMIN')")
  public ApiEnvelope<DeleteResult> deleteById(
      @Parameter(description = "素材 UUID")
      @PathVariable
      @Pattern(regexp = "^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$",
               message = "Invalid UUID format")
      String id) {

    long start = System.currentTimeMillis();
    DeleteResult result = commandService.deleteById(id);
    metrics.recordDeleteRequest(System.currentTimeMillis() - start);

    return ApiEnvelope.ok(result);
  }

  @Operation(
      summary = "批量删除素材",
      description = "根据 ID 列表批量删除素材记录。需要 ROLE_ADMIN 权限。此操作不可逆。")
  @DeleteMapping("/batch")
  @PreAuthorize("hasRole('ADMIN')")
  public ApiEnvelope<DeleteResult> deleteBatch(
      @Valid @RequestBody DeleteBatchRequest request) {

    long start = System.currentTimeMillis();
    DeleteResult result = commandService.deleteBatch(request.ids());
    metrics.recordDeleteRequest(System.currentTimeMillis() - start);

    return ApiEnvelope.ok(result);
  }

  @Operation(
      summary = "按条件删除素材",
      description = "按过滤条件批量删除素材。需要 ROLE_ADMIN 权限。此操作不可逆，谨慎使用。")
  @DeleteMapping("/by-query")
  @PreAuthorize("hasRole('ADMIN')")
  public ApiEnvelope<DeleteResult> deleteByQuery(
      @Parameter(description = "状态过滤 (pending/approved/rejected)")
      @RequestParam(required = false)
      @Pattern(regexp = "^(pending|approved|rejected)?$", message = "Status must be pending, approved, or rejected")
      String status,
      @Parameter(description = "上传人过滤")
      @RequestParam(required = false)
      String uploader,
      @Parameter(description = "数据集来源过滤 (1/2/3)")
      @RequestParam(required = false)
      @Min(value = 1, message = "SourceDataset must be 1, 2, or 3")
      @Max(value = 3, message = "SourceDataset must be 1, 2, or 3")
      Integer sourceDataset) {

    long start = System.currentTimeMillis();
    DeleteResult result = commandService.deleteByQuery(status, uploader, sourceDataset);
    metrics.recordDeleteRequest(System.currentTimeMillis() - start);

    return ApiEnvelope.ok(result);
  }
}
