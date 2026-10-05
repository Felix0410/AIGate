ALTER TABLE application
    ADD COLUMN default_deployment_id BIGINT NULL;

ALTER TABLE application
    ADD CONSTRAINT fk_application_default_deployment
        FOREIGN KEY (default_deployment_id)
            REFERENCES model_deployment (id)
            ON DELETE RESTRICT;
