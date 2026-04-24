package com.homework.asset.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.homework.asset.mapper.AssetMapper;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AssetStatsServiceTest {

  @Mock
  private AssetMapper assetMapper;

  private AssetStatsService statsService;

  @BeforeEach
  void setUp() {
    statsService = new AssetStatsService(assetMapper);
  }

  @Test
  void uploaderAvgSize_returnsData() {
    when(assetMapper.selectUploaderAvgSize())
        .thenReturn(List.of(
            Map.of("uploader", "张三", "avgSizeBytes", 5000000L),
            Map.of("uploader", "李四", "avgSizeBytes", 3000000L)));

    List<Map<String, Object>> result = statsService.uploaderAvgSize();

    assertThat(result).hasSize(2);
    assertThat(result.get(0)).containsKey("uploader");
    assertThat(result.get(0)).containsKey("avgSizeBytes");
  }

  @Test
  void uploaderAvgSize_empty() {
    when(assetMapper.selectUploaderAvgSize()).thenReturn(List.of());
    assertThat(statsService.uploaderAvgSize()).isEmpty();
  }

  @Test
  void topTags_returnsData() {
    when(assetMapper.selectTopTags(5))
        .thenReturn(List.of(
            Map.of("tag", "节日", "count", 10),
            Map.of("tag", "促销", "count", 8)));

    List<Map<String, Object>> result = statsService.topTags(5);

    assertThat(result).hasSize(2);
    assertThat(result.get(0)).containsKey("tag");
    assertThat(result.get(0)).containsKey("count");
  }

  @Test
  void topTags_outOfRange_throws400() {
    assertThatThrownBy(() -> statsService.topTags(200))
        .hasMessageContaining("Invalid topN parameter");
  }

  @Test
  void platformApprovalRate_returnsData() {
    when(assetMapper.selectPlatformApprovalRate())
        .thenReturn(List.of(
            Map.of("platform", "qianchuan", "total", 100, "approved", 80, "approvalRate", 80.0)));

    List<Map<String, Object>> result = statsService.platformApprovalRate();

    assertThat(result).hasSize(1);
    assertThat(result.get(0)).containsKey("platform");
    assertThat(result.get(0)).containsKey("approvalRate");
  }
}
