CREATE TABLE application_api_key
(
    id             BIGINT       NOT NULL AUTO_INCREMENT,
    application_id BIGINT       NOT NULL,
    name           VARCHAR(100) NOT NULL,
    key_id         VARCHAR(64)  NOT NULL,
    key_prefix     VARCHAR(100) NOT NULL,
    key_hash       CHAR(64)     NOT NULL,
    status         VARCHAR(20)  NOT NULL,
    created_at     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    revoked_at     TIMESTAMP    NULL,

    PRIMARY KEY (id),
    CONSTRAINT uk_application_api_key_key_id UNIQUE (key_id),
    CONSTRAINT fk_application_api_key_application
        FOREIGN KEY (application_id)
            REFERENCES application (id)
            ON DELETE CASCADE
);

CREATE INDEX idx_application_api_key_application_id
    ON application_api_key (application_id);
