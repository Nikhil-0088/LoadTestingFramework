ALTER TABLE work_requests
    ADD COLUMN lease_generation BIGINT NOT NULL DEFAULT 0;
