-- SCRUM-96: rate limit chung cho các backend, không lưu IP/email/userId dạng rõ.
CREATE TABLE ha_rate_limit_fixed (
    scope VARCHAR(32) NOT NULL,
    subject_hash VARCHAR(64) NOT NULL,
    window_started_at TIMESTAMPTZ NOT NULL,
    request_count INTEGER NOT NULL,
    PRIMARY KEY (scope, subject_hash)
);
CREATE TABLE ha_rate_limit_sliding (
    id BIGSERIAL PRIMARY KEY,
    scope VARCHAR(32) NOT NULL,
    subject_hash VARCHAR(64) NOT NULL,
    called_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX idx_ha_rate_limit_subject_time
    ON ha_rate_limit_sliding(scope, subject_hash, called_at);
