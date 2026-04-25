package com.homework.asset.service;

import com.homework.asset.api.dto.CursorPage;
import com.homework.asset.api.dto.PagedResponse;
import com.homework.asset.api.exception.ApiException;
import com.homework.asset.api.query.QueryDslParser;
import com.homework.asset.api.query.QueryDslParser.ParsedQuery;
import com.homework.asset.mapper.AssetMapper;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
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

  public PagedResponse<Map<String, Object>> listAssets(MultiValueMap<String, String> params) {
    ParsedQuery query = QueryDslParser.parse(params);
    List<Map<String, Object>> items = assetMapper.selectByDsl(
        query.params(), query.fields(), query.orderClauses(), query.pageSize(), query.offset());
    long total = assetMapper.countByDsl(query.params());
    return PagedResponse.of(items, total, query.page(), query.pageSize());
  }

  /**
   * Keyset/Cursor 分页。用于大数据集场景，避免 OFFSET 性能问题。
   *
   * @param params 过滤参数
   * @param cursor Base64 编码的游标，格式为 "uploaded_at|id"
   * @param pageSize 每页条数
   * @return 分页结果，包含 nextCursor
   */
  public CursorPage<Map<String, Object>> listAssetsByCursor(
      MultiValueMap<String, String> params, String cursor, int pageSize) {

    Map<String, Object> filterParams = parseCursorFilters(params);

    String cursorUploadedAt = null;
    String cursorId = null;

    if (cursor != null && !cursor.isBlank()) {
      String[] parts = decodeCursor(cursor);
      if (parts.length == 2) {
        cursorUploadedAt = parts[0];
        cursorId = parts[1];
      }
    }

    int limit = pageSize + 1;
    List<Map<String, Object>> items =
        assetMapper.selectByCursor(filterParams, Collections.emptyList(), cursorUploadedAt, cursorId, limit);

    String nextCursor = null;
    if (items.size() > pageSize) {
      Map<String, Object> lastItem = items.get(pageSize - 1);
      Object uploadedAt = lastItem.get("uploadedAt");
      Object id = lastItem.get("id");
      if (uploadedAt != null && id != null) {
        nextCursor = encodeCursor(uploadedAt.toString(), id.toString());
      }
      items = items.subList(0, pageSize);
    }

    return CursorPage.of(items, nextCursor, pageSize);
  }

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

  private Map<String, Object> parseCursorFilters(MultiValueMap<String, String> params) {
    ParsedQuery query = QueryDslParser.parse(params);
    return query.params();
  }

  private String[] decodeCursor(String cursor) {
    try {
      String decoded = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
      return decoded.split("\\|", 2);
    } catch (IllegalArgumentException e) {
      throw new ApiException(400, "Invalid cursor format");
    }
  }

  private String encodeCursor(String uploadedAt, String id) {
    String raw = uploadedAt + "|" + id;
    return Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
  }
}
