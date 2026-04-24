-- =============================================================================
-- V2: ETL 可观测能力
-- 1) ingest_runs   : 每次导入运行的总览（批次可观测）
-- 2) ingest_rejects: 失败行落表（归一化失败 / 写库失败）
-- =============================================================================

CREATE TABLE ingest_runs
(
    run_id            UUID        NOT NULL,
    datasets          TEXT        NOT NULL,
    dry_run           BOOLEAN     NOT NULL DEFAULT FALSE,
    status            TEXT        NOT NULL CHECK (status IN ('running', 'success', 'partial_success', 'failed')),
    total_rows        INT         NOT NULL DEFAULT 0 CHECK (total_rows >= 0),
    normalized_rows   INT         NOT NULL DEFAULT 0 CHECK (normalized_rows >= 0),
    upserted_rows     INT         NOT NULL DEFAULT 0 CHECK (upserted_rows >= 0),
    rejected_rows     INT         NOT NULL DEFAULT 0 CHECK (rejected_rows >= 0),
    dataset_count     INT         NOT NULL DEFAULT 0 CHECK (dataset_count >= 0),
    failed_datasets   INT         NOT NULL DEFAULT 0 CHECK (failed_datasets >= 0),
    batch_stats       JSONB       NOT NULL DEFAULT '[]'::jsonb,
    error_message     TEXT,
    started_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    finished_at       TIMESTAMPTZ,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (run_id)
);

COMMENT ON TABLE ingest_runs IS 'ETL 导入运行记录：用于批次可观测与审计';
COMMENT ON COLUMN ingest_runs.datasets IS '本次运行导入的数据集列表，如 1,2,3';
COMMENT ON COLUMN ingest_runs.batch_stats IS '按 dataset 的批次统计明细(JSON数组)';

CREATE INDEX idx_ingest_runs_started_at ON ingest_runs (started_at DESC);
CREATE INDEX idx_ingest_runs_status ON ingest_runs (status);

CREATE TABLE ingest_rejects
(
    id               BIGSERIAL   NOT NULL,
    run_id           UUID        NOT NULL,
    source_dataset   SMALLINT    NOT NULL,
    row_num          INT,
    stage            TEXT        NOT NULL CHECK (stage IN ('normalize', 'upsert')),
    source_id        TEXT,
    reason           TEXT        NOT NULL,
    raw_record       JSONB       NOT NULL,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (id),
    CONSTRAINT fk_ingest_rejects_run_id FOREIGN KEY (run_id) REFERENCES ingest_runs (run_id)
);

COMMENT ON TABLE ingest_rejects IS 'ETL 失败行明细：保存失败原因与原始行';
COMMENT ON COLUMN ingest_rejects.stage IS '失败阶段：normalize=归一化失败，upsert=写库失败';

CREATE INDEX idx_ingest_rejects_run_id_created_at ON ingest_rejects (run_id, created_at DESC);
CREATE INDEX idx_ingest_rejects_dataset ON ingest_rejects (source_dataset);
CREATE INDEX idx_ingest_rejects_stage ON ingest_rejects (stage);
