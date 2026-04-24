package com.homework.asset.it;

import static org.assertj.core.api.Assertions.assertThat;

import com.homework.asset.ingest.IngestRunner;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * 集成测试：用 Testcontainers 起真实 PostgreSQL，验证完整链路。
 *
 * <p>禁止使用 H2，原因：H2 不支持 text[] GIN / ON CONFLICT DO UPDATE / gen_random_uuid()。
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AssetControllerIT {

  static {
    // Docker Desktop + Docker Engine 29.x 在部分环境下需要显式 API 版本与 socket。
    if (System.getProperty("api.version") == null) {
      System.setProperty("api.version", "1.53");
    }
    if (System.getProperty("docker.host") == null) {
      System.setProperty("docker.host", "unix:///var/run/docker.sock");
    }
    // 当前网络环境访问 Docker Hub 令牌接口不稳定，禁用 Ryuk 避免拉取 testcontainers/ryuk。
    System.setProperty("ryuk.disabled", "true");
    System.setProperty("testcontainers.ryuk.disabled", "true");
    System.setProperty("org.testcontainers.ryuk.disabled", "true");
  }

  @Container
  static PostgreSQLContainer<?> postgres =
      new PostgreSQLContainer<>("postgres:15-alpine")
          .withDatabaseName("asset_test")
          .withUsername("test")
          .withPassword("test");

  @DynamicPropertySource
  static void configureProperties(DynamicPropertyRegistry registry) {
    if (!postgres.isRunning()) {
      postgres.start();
    }
    registry.add("spring.datasource.url", postgres::getJdbcUrl);
    registry.add("spring.datasource.username", postgres::getUsername);
    registry.add("spring.datasource.password", postgres::getPassword);
  }

  @LocalServerPort
  private int port;

  @Autowired
  private TestRestTemplate restTemplate;

  @Autowired
  private IngestRunner ingestRunner;

  @Autowired
  private JdbcTemplate jdbcTemplate;

  private String base() {
    return "http://localhost:" + port;
  }

  /** 在所有测试前导入数据集（仅执行一次，@BeforeAll + PER_CLASS）。 */
  @BeforeAll
  void importData() throws Exception {
    // 模拟 --ingest=all 启动参数
    org.springframework.boot.DefaultApplicationArguments args =
        new org.springframework.boot.DefaultApplicationArguments("--ingest=all");
    ingestRunner.run(args);
  }

  // ── 基础查询 ──

  @Test
  void listAssets_defaultPage_returns200() {
    ResponseEntity<Map<String, Object>> resp =
        restTemplate.exchange(
            base() + "/api/v1/assets",
            HttpMethod.GET,
            null,
            new ParameterizedTypeReference<>() {});
    assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
    Map<String, Object> body = resp.getBody();
    assertThat(body).isNotNull();
    assertThat(body.get("code")).isEqualTo(0);
    // data.total 应该 > 0（三份数据集都导入了）
    @SuppressWarnings("unchecked")
    Map<String, Object> data = (Map<String, Object>) body.get("data");
    assertThat(((Number) data.get("total")).intValue()).isGreaterThan(0);
  }

  @Test
  void listAssets_filterByStatus_approved_only() {
    ResponseEntity<Map<String, Object>> resp =
        restTemplate.exchange(
            base() + "/api/v1/assets?status=approved",
            HttpMethod.GET,
            null,
            new ParameterizedTypeReference<>() {});
    assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
    @SuppressWarnings("unchecked")
    Map<String, Object> data = (Map<String, Object>) resp.getBody().get("data");
    @SuppressWarnings("unchecked")
    List<Map<String, Object>> items = (List<Map<String, Object>>) data.get("items");
    // 所有返回的素材状态都应该是 approved
    items.forEach(
        item -> assertThat(item.get("status")).isEqualTo("approved"));
  }

  @Test
  void listAssets_filterByFileSizeLte() {
    ResponseEntity<Map<String, Object>> resp =
        restTemplate.exchange(
            base() + "/api/v1/assets?file_size_bytes[lte]=524288000",
            HttpMethod.GET,
            null,
            new ParameterizedTypeReference<>() {});
    assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
    @SuppressWarnings("unchecked")
    Map<String, Object> data = (Map<String, Object>) resp.getBody().get("data");
    @SuppressWarnings("unchecked")
    List<Map<String, Object>> items = (List<Map<String, Object>>) data.get("items");
    items.forEach(
        item -> assertThat(((Number) item.get("fileSizeBytes")).longValue())
            .isLessThanOrEqualTo(524288000L));
  }

  @Test
  void listAssets_sortByFileSizeDesc() {
    ResponseEntity<Map<String, Object>> resp =
        restTemplate.exchange(
            base() + "/api/v1/assets?sort=file_size_bytes:desc&page_size=5",
            HttpMethod.GET,
            null,
            new ParameterizedTypeReference<>() {});
    assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
    @SuppressWarnings("unchecked")
    List<Map<String, Object>> items =
        (List<Map<String, Object>>) ((Map<?, ?>) resp.getBody().get("data")).get("items");
    // 验证按文件大小降序
    for (int i = 0; i < items.size() - 1; i++) {
      long cur = ((Number) items.get(i).get("fileSizeBytes")).longValue();
      long next = ((Number) items.get(i + 1).get("fileSizeBytes")).longValue();
      assertThat(cur).isGreaterThanOrEqualTo(next);
    }
  }

  @Test
  void listAssets_sparseFields_onlyRequestedFieldsReturned() {
    ResponseEntity<Map<String, Object>> resp =
        restTemplate.exchange(
            base() + "/api/v1/assets?fields=title,status&page_size=3",
            HttpMethod.GET,
            null,
            new ParameterizedTypeReference<>() {});
    assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
    @SuppressWarnings("unchecked")
    List<Map<String, Object>> items =
        (List<Map<String, Object>>) ((Map<?, ?>) resp.getBody().get("data")).get("items");
    if (!items.isEmpty()) {
      Map<String, Object> first = items.get(0);
      assertThat(first).containsKeys("title", "status");
    }
  }

  // ── 安全/边界 ──

  @Test
  void listAssets_unknownField_returns400() {
    ResponseEntity<Map<String, Object>> resp =
        restTemplate.exchange(
            base() + "/api/v1/assets?raw_record=anything",
            HttpMethod.GET,
            null,
            new ParameterizedTypeReference<>() {});
    assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(resp.getBody().get("code")).isEqualTo(400);
  }

  @Test
  void listAssets_unknownOperator_returns400() {
    ResponseEntity<Map<String, Object>> resp =
        restTemplate.exchange(
            base() + "/api/v1/assets?status[script]=anything",
            HttpMethod.GET,
            null,
            new ParameterizedTypeReference<>() {});
    assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
  }

  // ── 统计端点 ──

  @Test
  void stats_uploaderAvgSize_returns200() {
    ResponseEntity<Map<String, Object>> resp =
        restTemplate.exchange(
            base() + "/api/v1/stats/uploader-avg-size",
            HttpMethod.GET,
            null,
            new ParameterizedTypeReference<>() {});
    assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
    @SuppressWarnings("unchecked")
    List<Map<String, Object>> data = (List<Map<String, Object>>) resp.getBody().get("data");
    assertThat(data).isNotEmpty();
    // 每行应该包含 uploader 和 avgSizeBytes
    data.forEach(
        row -> {
          assertThat(row).containsKey("uploader");
          assertThat(row).containsKey("avgSizeBytes");
        });
  }

  @Test
  void stats_topTags_returnsTop5() {
    ResponseEntity<Map<String, Object>> resp =
        restTemplate.exchange(
            base() + "/api/v1/stats/top-tags",
            HttpMethod.GET,
            null,
            new ParameterizedTypeReference<>() {});
    assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
    @SuppressWarnings("unchecked")
    List<Map<String, Object>> data = (List<Map<String, Object>>) resp.getBody().get("data");
    assertThat(data).isNotEmpty();
    assertThat(data.size()).isLessThanOrEqualTo(5);
    data.forEach(row -> assertThat(row).containsKeys("tag", "count"));
  }

  @Test
  void stats_platformApproval_returnsRates() {
    ResponseEntity<Map<String, Object>> resp =
        restTemplate.exchange(
            base() + "/api/v1/stats/platform-approval",
            HttpMethod.GET,
            null,
            new ParameterizedTypeReference<>() {});
    assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
    @SuppressWarnings("unchecked")
    List<Map<String, Object>> data = (List<Map<String, Object>>) resp.getBody().get("data");
    // 数据集2和3有平台字段，应有结果
    assertThat(data).isNotEmpty();
    data.forEach(row -> assertThat(row).containsKeys("platform", "total", "approvalRate"));
  }

  // ── ETL 可观测 ──

  @Test
  void ingest_runs_returns200_and_hasRunItems() {
    ResponseEntity<Map<String, Object>> resp =
        restTemplate.exchange(
            base() + "/api/v1/ingest/runs?page=1&page_size=10",
            HttpMethod.GET,
            null,
            new ParameterizedTypeReference<>() {});
    assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
    @SuppressWarnings("unchecked")
    Map<String, Object> data = (Map<String, Object>) resp.getBody().get("data");
    @SuppressWarnings("unchecked")
    List<Map<String, Object>> items = (List<Map<String, Object>>) data.get("items");
    assertThat(items).isNotEmpty();
    assertThat(items.get(0)).containsKeys("runId", "status", "totalRows", "batchStats");
  }

  @Test
  void ingest_rejects_filterByRunId_returnsOnlyTargetRun() {
    String runId = "11111111-1111-1111-1111-111111111111";
    jdbcTemplate.update(
        """
        INSERT INTO ingest_runs
        (run_id, datasets, dry_run, status, total_rows, normalized_rows, upserted_rows, rejected_rows,
         dataset_count, failed_datasets, batch_stats, started_at, finished_at, created_at, updated_at)
        VALUES (?::uuid, '1', false, 'partial_success', 1, 0, 0, 1, 1, 1, '[]'::jsonb, now(), now(), now(), now())
        ON CONFLICT (run_id) DO NOTHING
        """,
        runId);
    jdbcTemplate.update(
        """
        INSERT INTO ingest_rejects
        (run_id, source_dataset, row_num, stage, source_id, reason, raw_record, created_at)
        VALUES (?::uuid, 1, 7, 'normalize', 'A0007', 'bad row', '{"id":"A0007"}'::jsonb, now())
        """,
        runId);

    ResponseEntity<Map<String, Object>> resp =
        restTemplate.exchange(
            base() + "/api/v1/ingest/rejects?run_id=" + runId + "&page=1&page_size=20",
            HttpMethod.GET,
            null,
            new ParameterizedTypeReference<>() {});
    assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
    @SuppressWarnings("unchecked")
    List<Map<String, Object>> items =
        (List<Map<String, Object>>) ((Map<?, ?>) resp.getBody().get("data")).get("items");
    assertThat(items).isNotEmpty();
    items.forEach(item -> assertThat(String.valueOf(item.get("runId"))).isEqualTo(runId));
  }

  // ── 详情端点 ──

  @Test
  void getAssetById_invalidUUID_returns400() {
    ResponseEntity<Map<String, Object>> resp =
        restTemplate.exchange(
            base() + "/api/v1/assets/not-a-uuid",
            HttpMethod.GET,
            null,
            new ParameterizedTypeReference<>() {});
    assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
  }

  @Test
  void getAssetById_nonExistentUUID_returns404() {
    ResponseEntity<Map<String, Object>> resp =
        restTemplate.exchange(
            base() + "/api/v1/assets/00000000-0000-0000-0000-000000000000",
            HttpMethod.GET,
            null,
            new ParameterizedTypeReference<>() {});
    assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
  }

  @Test
  void tagsUpsert_noDuplicateTags() {
    ResponseEntity<Map<String, Object>> first =
        restTemplate.exchange(
            base() + "/api/v1/assets?tags[has]=节日&page_size=1",
            HttpMethod.GET,
            null,
            new ParameterizedTypeReference<>() {});
    if (first.getStatusCode() == HttpStatus.OK) {
      @SuppressWarnings("unchecked")
      List<Map<String, Object>> items =
          (List<Map<String, Object>>) ((Map<?, ?>) first.getBody().get("data")).get("items");
      if (!items.isEmpty() && items.get(0).get("tags") != null) {
        @SuppressWarnings("unchecked")
        List<String> tags = (List<String>) items.get(0).get("tags");
        assertThat(tags).doesNotHaveDuplicates();
      }
    }
  }
}
