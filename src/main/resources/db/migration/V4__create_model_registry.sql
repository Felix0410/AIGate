CREATE TABLE provider
(
    id         BIGINT PRIMARY KEY AUTO_INCREMENT,
    name       VARCHAR(100) NOT NULL,
    type       VARCHAR(50)  NOT NULL,
    created_at TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
        ON UPDATE CURRENT_TIMESTAMP,

    CONSTRAINT uk_provider_name UNIQUE (name)
);

CREATE TABLE model
(
    id         BIGINT PRIMARY KEY AUTO_INCREMENT,
    name       VARCHAR(100) NOT NULL,
    created_at TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
        ON UPDATE CURRENT_TIMESTAMP,

    CONSTRAINT uk_model_name UNIQUE (name)
);

CREATE TABLE model_deployment
(
    id                   BIGINT PRIMARY KEY AUTO_INCREMENT,
    name                 VARCHAR(100) NOT NULL,
    provider_id          BIGINT       NOT NULL,
    model_id             BIGINT       NOT NULL,
    endpoint_url         VARCHAR(500) NOT NULL,
    remote_model_name    VARCHAR(255) NOT NULL,
    encrypted_credential TEXT         NULL,
    enabled              BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at           TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at           TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
        ON UPDATE CURRENT_TIMESTAMP,

    CONSTRAINT uk_model_deployment_name UNIQUE (name),

    CONSTRAINT fk_model_deployment_provider
        FOREIGN KEY (provider_id)
            REFERENCES provider (id)
            ON DELETE RESTRICT,

    CONSTRAINT fk_model_deployment_model
        FOREIGN KEY (model_id)
            REFERENCES model (id)
            ON DELETE RESTRICT,

    INDEX idx_model_deployment_provider_id (provider_id),
    INDEX idx_model_deployment_model_id (model_id)
);