package com.homework.asset.service;

import com.homework.asset.api.dto.PagedResponse;
import com.homework.asset.api.exception.ApiException;
import com.homework.asset.api.query.QueryDslParser;
import com.homework.asset.api.query.QueryDslParser.ParsedQuery;
import com.homework.asset.mapper.AssetMapper;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.util.MultiValueMap;

@Service
public class AssetQueryService {

  private final AssetMapper assetMapper;

  public AssetQueryService(AssetMapper assetMapper) {
    this.assetMapper = assetMapper;
  }

  /** 解析 HTTP 查询参数，执行过滤/排序/分页，返回分页结果。 */
  public PagedResponse<Map<String, Object>> listAssets(MultiValueMap<String, String> params) {
    ParsedQuery query = QueryDslParser.parse(params);
    List<Map<String, Object>> items = assetMapper.selectByDsl(
        query.params(), query.fields(), query.orderClauses(), query.pageSize(), query.offset());
    long total = assetMapper.countByDsl(query.params());
    return PagedResponse.of(items, total, query.page(), query.pageSize());
  }

  /** 按 UUID 查询单条素材，fields 为空时返回全部字段。 */
  public Map<String, Object> getAssetById(String id, String fields) {
    try {
      UUID.fromString(id);
    } catch (IllegalArgumentException e) {
      throw new ApiException(400, "Invalid UUID: " + id);
    }
    List<String> fieldList = (fields != null && !fields.isBlank())
        ? QueryDslParser.parseFields(fields)
        : Collections.emptyList();
    return assetMapper.selectByDsl(Map.of("id", id), fieldList, List.of(), 1, 0)
        .stream().findFirst()
        .orElseThrow(() -> new ApiException(404, "Asset not found: " + id));
  }
}
