package com.homework.asset.domain.enums;

/**
 * 素材审核状态枚举。
 *
 * <p>canonical code 与数据库 CHECK 约束保持一致：
 * <ul>
 *   <li>PENDING — 待审核
 *   <li>APPROVED — 已通过
 *   <li>REJECTED — 已拒绝
 * </ul>
 *
 * <p>数据库存储值为小写字符串（pending/approved/rejected），与 ETL 归一化结果对应。
 */
public enum AssetStatus {

  /** 待审核：素材已提交，尚未完成审核 */
  PENDING("pending", "待审核"),

  /** 已通过：素材审核通过，可正常投放 */
  APPROVED("approved", "已通过"),

  /** 已拒绝：素材不符合要求，审核被拒 */
  REJECTED("rejected", "已拒绝");

  /** 数据库存储的 canonical code（小写英文） */
  private final String code;

  /** 中文展示名称 */
  private final String label;

  AssetStatus(String code, String label) {
    this.code = code;
    this.label = label;
  }

  public String getCode() {
    return code;
  }

  public String getLabel() {
    return label;
  }

  /**
   * 根据 canonical code 查找枚举值。
   *
   * @param code 小写 canonical code，如 "pending"
   * @return 对应枚举，找不到时返回 null
   */
  public static AssetStatus fromCode(String code) {
    if (code == null) {
      return null;
    }
    for (AssetStatus s : values()) {
      if (s.code.equalsIgnoreCase(code)) {
        return s;
      }
    }
    return null;
  }

  @Override
  public String toString() {
    return code;
  }
}
