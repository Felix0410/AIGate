CREATE TABLE application
(
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    name       VARCHAR(100) NOT NULL,
    team_id    BIGINT       NOT NULL,
    created_at TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    PRIMARY KEY (id),
    CONSTRAINT uk_application_name UNIQUE (name),
    CONSTRAINT fk_application_team
        FOREIGN KEY (team_id)
            REFERENCES team (id)
            ON DELETE RESTRICT
);

CREATE INDEX idx_application_team_id
    ON application (team_id);