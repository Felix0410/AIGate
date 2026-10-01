CREATE TABLE employee
(
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    name       VARCHAR(100) NOT NULL,
    email      VARCHAR(255) NOT NULL,
    team_id    BIGINT       NOT NULL,
    created_at TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    PRIMARY KEY (id),
    CONSTRAINT uk_employee_email UNIQUE (email),
    CONSTRAINT fk_employee_team
        FOREIGN KEY (team_id)
            REFERENCES team (id)
            ON DELETE RESTRICT
);

CREATE INDEX idx_employee_team_id
    ON employee (team_id);