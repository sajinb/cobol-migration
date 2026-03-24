-- COBOL Migration UI — PostgreSQL schema
CREATE EXTENSION IF NOT EXISTS "pgcrypto";

CREATE TABLE IF NOT EXISTS projects (
    id           UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    name         VARCHAR(255) NOT NULL,
    source_type  VARCHAR(20),                          -- 'github' | 'upload'
    github_url   VARCHAR(500),
    cobol_dir    VARCHAR(500),
    copybook_dir VARCHAR(500),
    status       VARCHAR(50)  NOT NULL DEFAULT 'pending',  -- pending | ready | migrating | completed | failed
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS migration_runs (
    id           UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id   UUID        NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    status       VARCHAR(50) NOT NULL DEFAULT 'pending',  -- pending | running | completed | failed
    started_at   TIMESTAMPTZ,
    completed_at TIMESTAMPTZ,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS migration_logs (
    id        BIGSERIAL   PRIMARY KEY,
    run_id    UUID        NOT NULL REFERENCES migration_runs(id) ON DELETE CASCADE,
    logged_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    level     VARCHAR(20) NOT NULL DEFAULT 'INFO',
    step      VARCHAR(100),
    message   TEXT        NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_runs_project_id  ON migration_runs(project_id);
CREATE INDEX IF NOT EXISTS idx_logs_run_id      ON migration_logs(run_id);
CREATE INDEX IF NOT EXISTS idx_logs_logged_at   ON migration_logs(logged_at);
CREATE INDEX IF NOT EXISTS idx_projects_status  ON projects(status);

-- Auto-update updated_at on projects
CREATE OR REPLACE FUNCTION _update_updated_at()
RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN NEW.updated_at = NOW(); RETURN NEW; END;
$$;

DROP TRIGGER IF EXISTS trg_projects_updated_at ON projects;
CREATE TRIGGER trg_projects_updated_at
    BEFORE UPDATE ON projects
    FOR EACH ROW EXECUTE PROCEDURE _update_updated_at();
