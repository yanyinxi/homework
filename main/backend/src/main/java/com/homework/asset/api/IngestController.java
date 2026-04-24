package com.homework.asset.api;

import com.homework.asset.api.dto.ApiEnvelope;
import com.homework.asset.api.dto.PagedResponse;
import com.homework.asset.service.IngestObservabilityService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** ETL 可观测只读 API。 */
@Tag(name = "Ingest", description = "ETL 批次运行与失败行只读查询接口")
@RestController
@RequestMapping("/api/v1/ingest")
public class IngestController {

  private final IngestObservabilityService observabilityService;

  public IngestController(IngestObservabilityService observabilityService) {
    this.observabilityService = observabilityService;
  }

  @Operation(summary = "查询导入运行记录")
  @GetMapping("/runs")
  public ApiEnvelope<PagedResponse<Map<String, Object>>> listRuns(
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(name = "page_size", defaultValue = "20") int pageSize) {
    return ApiEnvelope.ok(observabilityService.listRuns(page, pageSize));
  }

  @Operation(summary = "查询失败行记录")
  @GetMapping("/rejects")
  public ApiEnvelope<PagedResponse<Map<String, Object>>> listRejects(
      @RequestParam(name = "run_id", required = false) String runId,
      @RequestParam(name = "source_dataset", required = false) Integer sourceDataset,
      @RequestParam(required = false) String stage,
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(name = "page_size", defaultValue = "20") int pageSize) {
    return ApiEnvelope.ok(observabilityService.listRejects(runId, sourceDataset, stage, page, pageSize));
  }
}
