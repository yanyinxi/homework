package com.homework.asset.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.homework.asset.domain.entity.Asset;
import com.homework.asset.api.query.SortClause;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** 素材 Mapper：简单 CRUD 继承 BaseMapper；动态 DSL 查询定义在 AssetMapper.xml。 */
@Mapper
public interface AssetMapper extends BaseMapper<Asset> {

  /**
   * 动态 DSL 查询（核心方法）。 params 中包含 QueryDslParser 解析出的过滤条件，AssetMapper.xml 用
   * &lt;where&gt;&lt;if&gt; 动态拼接 SQL。 所有值通过 #{} 参数化，字段名通过白名单枚举映射后内置到 XML 条件分支。
   */
  List<Map<String, Object>> selectByDsl(
      @Param("params") Map<String, Object> params,
      @Param("fields") List<String> fields,
      @Param("orderClauses") List<SortClause> orderClauses,
      @Param("limit") int limit,
      @Param("offset") int offset);

  /** 动态 DSL 查询总数（用于分页 total）。 */
  long countByDsl(@Param("params") Map<String, Object> params);

  /**
   * 幂等 upsert：相同 (source_dataset, source_id) 的记录更新，否则插入。
   * 使用 PostgreSQL ON CONFLICT DO UPDATE 语法。
   */
  int upsert(Asset asset);

  /** Q1：审核已通过素材中，各上传人的平均文件大小。 */
  List<Map<String, Object>> selectUploaderAvgSize();

  /** Q2：按标签统计素材数量，Top 5。 */
  List<Map<String, Object>> selectTopTags(@Param("limit") int limit);

  /** Q3：各平台审核通过率。 */
  List<Map<String, Object>> selectPlatformApprovalRate();

  /**
   * Keyset/Cursor 分页查询。基于 uploaded_at DESC, id DESC 排序，用上一页最后一条记录的
   * (uploaded_at, id) 作为游标，避免 OFFSET 扫描。
   */
  List<Map<String, Object>> selectByCursor(
      @Param("params") Map<String, Object> params,
      @Param("fields") List<String> fields,
      @Param("cursorUploadedAt") String cursorUploadedAt,
      @Param("cursorId") String cursorId,
      @Param("limit") int limit);

  /** 批量删除：根据 ID 列表删除记录。返回实际删除数量。 */
  int deleteBatchByIds(@Param("ids") List<UUID> ids);

  /** 按条件删除：根据过滤参数删除记录。返回实际删除数量。 */
  int deleteByParams(@Param("params") Map<String, Object> params);
}
