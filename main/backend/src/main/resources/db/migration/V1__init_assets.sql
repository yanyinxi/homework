-- =============================================================================
-- V1: 初始化 assets 表
-- 设计说明：
--   1. 单表存储三份异构数据集，共有字段做正式列，稀疏字段存 extra JSONB
--   2. source_dataset + source_id 联合唯一，保证 ETL 幂等性
--   3. 所有枚举值归一到 canonical code：status(pending/approved/rejected)
--   4. 标签存 text[] + GIN 索引，支持高效 @> 包含查询
--   5. raw_record 保留原始数据，支持审计和重跑 ETL
-- =============================================================================

CREATE EXTENSION IF NOT EXISTS "pgcrypto";

CREATE TABLE assets
(
    -- 全局主键：UUID，避免三套数据集 ID 命名空间冲突，分布式友好
    id               UUID        NOT NULL DEFAULT gen_random_uuid(),

    -- 数据源血缘（保证可追溯、可重跑 ETL）
    source_dataset   SMALLINT    NOT NULL,         -- 1=数据集1 / 2=数据集2 / 3=数据集3
    source_id        TEXT        NOT NULL,         -- 原始 ID: A0001 / asset_001 / vid0001
    ingested_at      TIMESTAMPTZ NOT NULL DEFAULT now(),

    -- 业务核心字段（三份数据集归一后的共有字段）
    title            TEXT        NOT NULL,
    uploader         TEXT        NOT NULL,
    uploaded_at      TIMESTAMPTZ NOT NULL,
    file_size_bytes  BIGINT      NOT NULL CHECK (file_size_bytes >= 0),
    -- status 归一：数据集1(待审核→pending, 已通过→approved, 已拒绝→rejected)
    --             数据集2(pending/approved/rejected 已是英文)
    --             数据集3(pending/approved/通过→approved)
    status           TEXT        NOT NULL CHECK (status IN ('pending', 'approved', 'rejected')),
    -- 标签：分号/逗号/Python list 字符串三种格式归一为 text[]
    tags             TEXT[]      NOT NULL DEFAULT '{}',
    city             TEXT,

    -- 部分数据集才有、但查询频率较高的字段（独立列，不进 extra）
    platform         TEXT,         -- 平台归一：qianchuan(千川/qianchuan/Qianchuan)
    reviewer         TEXT,         -- 审核人（仅数据集1）
    remark           TEXT,         -- 备注（仅数据集1）
    resolution       TEXT,         -- 分辨率如 1080x1920（仅数据集2）
    duration_sec     INT,          -- 视频时长秒数（仅数据集3）

    -- 其他稀疏字段 + 未来扩展（open schema，避免提前 over-engineering）
    extra            JSONB       NOT NULL DEFAULT '{}'::jsonb,

    -- 原始记录（调试 + 审计 + 事后重新解析）
    raw_record       JSONB       NOT NULL,

    PRIMARY KEY (id),
    -- 幂等导入：相同来源的同一条记录只存一次
    UNIQUE (source_dataset, source_id)
);

COMMENT ON TABLE assets IS '统一素材表：整合三份异构数据集，字段归一化后统一存储';
COMMENT ON COLUMN assets.source_dataset IS '数据来源：1=数据集1(中文字段) / 2=数据集2(英文字段) / 3=数据集3(混合字段)';
COMMENT ON COLUMN assets.source_id IS '原始数据集中的 ID，如 A0001 / asset_001 / vid0001';
COMMENT ON COLUMN assets.file_size_bytes IS '文件大小（字节），所有来源归一到 bytes 便于比较';
COMMENT ON COLUMN assets.status IS '审核状态：pending=待审核 / approved=已通过 / rejected=已拒绝';
COMMENT ON COLUMN assets.tags IS '标签数组，三种分隔符格式归一后存为 text[]';
COMMENT ON COLUMN assets.platform IS '投放平台归一 canonical code，如 qianchuan';
COMMENT ON COLUMN assets.extra IS '平台特有稀疏字段，JSONB open schema';
COMMENT ON COLUMN assets.raw_record IS '原始行记录（JSON），用于审计和 ETL 重跑';

-- =============================================================================
-- 索引策略：查询驱动建索引，每个索引都有对应的真实查询场景
-- =============================================================================

-- Q1: WHERE status='approved' GROUP BY uploader
CREATE INDEX idx_assets_status          ON assets (status);
CREATE INDEX idx_assets_uploader        ON assets (uploader);
CREATE INDEX idx_assets_status_uploader ON assets (status, uploader);   -- 组合优化 Q1

-- Q2: 标签包含查询 tags @> ARRAY[...]（UNNEST 聚合通常仍为 Seq Scan）
CREATE INDEX idx_assets_tags_gin        ON assets USING GIN (tags);     -- 支持 @> 数组包含

-- Q3: WHERE platform IS NOT NULL GROUP BY platform
CREATE INDEX idx_assets_platform        ON assets (platform);

-- 列表 API 常用过滤/排序字段
CREATE INDEX idx_assets_uploaded_at     ON assets (uploaded_at DESC);   -- 按时间排序
CREATE INDEX idx_assets_file_size_bytes ON assets (file_size_bytes);    -- 大小范围过滤 + 排序
CREATE INDEX idx_assets_city            ON assets (city);               -- 城市过滤

-- JSONB 路径查询（extra 字段的动态过滤）
CREATE INDEX idx_assets_extra_gin       ON assets USING GIN (extra jsonb_path_ops);
