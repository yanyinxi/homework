package com.homework.asset.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.homework.asset.config.PgStringArrayTypeHandler;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** 统一素材实体，对应 assets 表。三份异构数据集归一后存储。 */
@TableName(value = "assets", autoResultMap = true)
public class Asset {

  // IdType.INPUT：不让 MP 自动生成 ID，交给 PostgreSQL DEFAULT gen_random_uuid()
  @TableId(type = IdType.INPUT)
  private UUID id;

  /** 数据来源：1=数据集1 / 2=数据集2 / 3=数据集3 */
  private Integer sourceDataset;

  /** 原始数据集中的 ID（A0001 / asset_001 / vid0001） */
  private String sourceId;

  /** 数据导入时间 */
  private Instant ingestedAt;
  
  /** 素材标题 */
  private String title;
  
  /** 上传人名称 */
  private String uploader;
  
  /** 素材上传时间 */
  private Instant uploadedAt;

  /** 文件大小（字节），所有来源归一到 bytes */
  private Long fileSizeBytes;

  /** 审核状态：pending / approved / rejected */
  private String status;

  /** 标签数组，三种分隔符格式归一后存为 text[] */
  @TableField(typeHandler = PgStringArrayTypeHandler.class)
  private List<String> tags;

  /** 城市 */
  private String city;
  
  /** 投放平台 */
  private String platform;
  
  /** 审核人 */
  private String reviewer;
  
  /** 备注 */
  private String remark;
  
  /** 分辨率（如 1080p） */
  private String resolution;
  
  /** 时长（秒） */
  private Integer durationSec;

  /** 其他稀疏字段（JSONB open schema） */
  @TableField(typeHandler = JacksonTypeHandler.class)
  private Object extra;

  /** 原始行记录（JSON），用于审计和 ETL 重跑 */
  @TableField(typeHandler = JacksonTypeHandler.class)
  private Object rawRecord;

  // Getters and Setters
  public UUID getId() {
    return id;
  }

  public void setId(UUID id) {
    this.id = id;
  }

  public Integer getSourceDataset() {
    return sourceDataset;
  }

  public void setSourceDataset(Integer sourceDataset) {
    this.sourceDataset = sourceDataset;
  }

  public String getSourceId() {
    return sourceId;
  }

  public void setSourceId(String sourceId) {
    this.sourceId = sourceId;
  }

  public Instant getIngestedAt() {
    return ingestedAt;
  }

  public void setIngestedAt(Instant ingestedAt) {
    this.ingestedAt = ingestedAt;
  }

  public String getTitle() {
    return title;
  }

  public void setTitle(String title) {
    this.title = title;
  }

  public String getUploader() {
    return uploader;
  }

  public void setUploader(String uploader) {
    this.uploader = uploader;
  }

  public Instant getUploadedAt() {
    return uploadedAt;
  }

  public void setUploadedAt(Instant uploadedAt) {
    this.uploadedAt = uploadedAt;
  }

  public Long getFileSizeBytes() {
    return fileSizeBytes;
  }

  public void setFileSizeBytes(Long fileSizeBytes) {
    this.fileSizeBytes = fileSizeBytes;
  }

  public String getStatus() {
    return status;
  }

  public void setStatus(String status) {
    this.status = status;
  }

  public List<String> getTags() {
    return tags;
  }

  public void setTags(List<String> tags) {
    this.tags = tags;
  }

  public String getCity() {
    return city;
  }

  public void setCity(String city) {
    this.city = city;
  }

  public String getPlatform() {
    return platform;
  }

  public void setPlatform(String platform) {
    this.platform = platform;
  }

  public String getReviewer() {
    return reviewer;
  }

  public void setReviewer(String reviewer) {
    this.reviewer = reviewer;
  }

  public String getRemark() {
    return remark;
  }

  public void setRemark(String remark) {
    this.remark = remark;
  }

  public String getResolution() {
    return resolution;
  }

  public void setResolution(String resolution) {
    this.resolution = resolution;
  }

  public Integer getDurationSec() {
    return durationSec;
  }

  public void setDurationSec(Integer durationSec) {
    this.durationSec = durationSec;
  }

  public Object getExtra() {
    return extra;
  }

  public void setExtra(Object extra) {
    this.extra = extra;
  }

  public Object getRawRecord() {
    return rawRecord;
  }

  public void setRawRecord(Object rawRecord) {
    this.rawRecord = rawRecord;
  }
}
