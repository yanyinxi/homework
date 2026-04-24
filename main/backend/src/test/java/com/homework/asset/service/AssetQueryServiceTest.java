package com.homework.asset.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.when;

import com.homework.asset.api.dto.PagedResponse;
import com.homework.asset.api.exception.ApiException;
import com.homework.asset.mapper.AssetMapper;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

@ExtendWith(MockitoExtension.class)
class AssetQueryServiceTest {

  @Mock
  private AssetMapper assetMapper;

  private AssetQueryService queryService;

  @BeforeEach
  void setUp() {
    queryService = new AssetQueryService(assetMapper);
  }

  @Test
  void listAssets_returnsPagedResponse() {
    MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
    params.add("status", "approved");

    when(assetMapper.selectByDsl(anyMap(), anyList(), any(), anyInt(), anyInt()))
        .thenReturn(List.of(Map.of("id", "abc", "title", "Test")));
    when(assetMapper.countByDsl(anyMap())).thenReturn(1L);

    PagedResponse<Map<String, Object>> result = queryService.listAssets(params);

    assertThat(result.items()).hasSize(1);
    assertThat(result.total()).isEqualTo(1);
  }

  @Test
  void listAssets_emptyResults() {
    when(assetMapper.selectByDsl(anyMap(), anyList(), any(), anyInt(), anyInt()))
        .thenReturn(List.of());
    when(assetMapper.countByDsl(anyMap())).thenReturn(0L);

    PagedResponse<Map<String, Object>> result = queryService.listAssets(new LinkedMultiValueMap<>());

    assertThat(result.items()).isEmpty();
    assertThat(result.total()).isEqualTo(0);
  }

  @Test
  void getAssetById_exists() {
    String validUuid = "550e8400-e29b-41d4-a716-446655440000";
    when(assetMapper.selectByDsl(anyMap(), anyList(), any(), anyInt(), anyInt()))
        .thenReturn(List.of(Map.of("id", validUuid, "title", "Test")));

    Map<String, Object> result = queryService.getAssetById(validUuid, null);

    assertThat(result).containsKey("id");
    assertThat(result.get("id")).isEqualTo(validUuid);
  }

  @Test
  void getAssetById_invalidUuid_throws400() {
    assertThatThrownBy(() -> queryService.getAssetById("not-a-uuid", null))
        .isInstanceOf(ApiException.class)
        .hasMessageContaining("Invalid UUID");
  }

  @Test
  void getAssetById_notFound_throws404() {
    String validUuid = "550e8400-e29b-41d4-a716-446655440000";
    when(assetMapper.selectByDsl(anyMap(), anyList(), any(), anyInt(), anyInt()))
        .thenReturn(List.of());

    assertThatThrownBy(() -> queryService.getAssetById(validUuid, null))
        .isInstanceOf(ApiException.class)
        .hasMessageContaining("Asset not found");
  }
}
