CREATE TABLE invocation_record
(
    id             BIGINT       NOT NULL AUTO_INCREMENT,
    application_id BIGINT       NOT NULL,
    status         VARCHAR(16)  NOT NULL,
    error_code     VARCHAR(64)  NULL,
    duration_ms    BIGINT       NOT NULL,
    created_at     TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),

    PRIMARY KEY (id),

    CONSTRAINT chk_invocation_record_status
        CHECK (status IN ('SUCCESS', 'FAILED')),

    CONSTRAINT chk_invocation_record_duration_ms
        CHECK (duration_ms >= 0),

    CONSTRAINT fk_invocation_record_application
        FOREIGN KEY (application_id)
            REFERENCES application (id)
            ON DELETE CASCADE
);

CREATE INDEX idx_invocation_record_application_id_id
    ON invocation_record (application_id, id);