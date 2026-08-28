CREATE TABLE load_tests (
    id CHAR(36) NOT NULL,
    name VARCHAR(200) NOT NULL,
    status VARCHAR(32) NOT NULL,
    requests_per_minute INT NOT NULL,
    duration_seconds INT NOT NULL,
    worker_count INT NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    started_at TIMESTAMP(6) NULL,
    ends_at TIMESTAMP(6) NULL,
    completed_at TIMESTAMP(6) NULL,
    failure_reason VARCHAR(2000) NULL,
    PRIMARY KEY (id)
);

CREATE TABLE request_definitions (
    id CHAR(36) NOT NULL,
    load_test_id CHAR(36) NOT NULL,
    name VARCHAR(200) NOT NULL,
    http_method VARCHAR(10) NOT NULL,
    url VARCHAR(2048) NOT NULL,
    headers JSON NULL,
    body TEXT NULL,
    auth_type VARCHAR(32) NOT NULL,
    auth_secret_reference VARCHAR(512) NULL,
    api_key_header_name VARCHAR(200) NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_request_definitions_load_test FOREIGN KEY (load_test_id) REFERENCES load_tests (id)
);

CREATE TABLE work_requests (
    id CHAR(36) NOT NULL,
    load_test_id CHAR(36) NOT NULL,
    status VARCHAR(32) NOT NULL,
    current_step VARCHAR(32) NOT NULL,
    attempt_count INT NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMP(6) NOT NULL,
    lease_owner VARCHAR(255) NULL,
    lease_until TIMESTAMP(6) NULL,
    last_error VARCHAR(2000) NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    completed_at TIMESTAMP(6) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_work_requests_load_test (load_test_id),
    KEY idx_work_requests_runnable (status, next_attempt_at, lease_until),
    CONSTRAINT fk_work_requests_load_test FOREIGN KEY (load_test_id) REFERENCES load_tests (id)
);

CREATE TABLE workers (
    id CHAR(36) NOT NULL,
    load_test_id CHAR(36) NOT NULL,
    work_request_id CHAR(36) NOT NULL,
    worker_type VARCHAR(32) NOT NULL,
    status VARCHAR(32) NOT NULL,
    owner_node_id VARCHAR(255) NULL,
    allocated_requests_per_minute INT NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    started_at TIMESTAMP(6) NULL,
    completed_at TIMESTAMP(6) NULL,
    last_heartbeat TIMESTAMP(6) NULL,
    last_error VARCHAR(2000) NULL,
    PRIMARY KEY (id),
    KEY idx_workers_work_request (work_request_id),
    CONSTRAINT fk_workers_load_test FOREIGN KEY (load_test_id) REFERENCES load_tests (id),
    CONSTRAINT fk_workers_work_request FOREIGN KEY (work_request_id) REFERENCES work_requests (id)
);

CREATE TABLE workflow_events (
    id CHAR(36) NOT NULL,
    work_request_id CHAR(36) NOT NULL,
    event_type VARCHAR(32) NOT NULL,
    status VARCHAR(32) NOT NULL,
    payload JSON NULL,
    created_at TIMESTAMP(6) NOT NULL,
    processed_at TIMESTAMP(6) NULL,
    last_error VARCHAR(2000) NULL,
    PRIMARY KEY (id),
    KEY idx_workflow_events_pending (status, created_at),
    CONSTRAINT fk_workflow_events_work_request FOREIGN KEY (work_request_id) REFERENCES work_requests (id)
);

CREATE TABLE worker_metric_snapshots (
    id CHAR(36) NOT NULL,
    worker_id CHAR(36) NOT NULL,
    captured_at TIMESTAMP(6) NOT NULL,
    total_requests BIGINT NOT NULL,
    successful_requests BIGINT NOT NULL,
    failed_requests BIGINT NOT NULL,
    status_code_counts JSON NULL,
    latency_histogram JSON NULL,
    PRIMARY KEY (id),
    KEY idx_worker_metric_snapshots_worker_time (worker_id, captured_at),
    CONSTRAINT fk_worker_metric_snapshots_worker FOREIGN KEY (worker_id) REFERENCES workers (id)
);

CREATE TABLE load_test_results (
    load_test_id CHAR(36) NOT NULL,
    total_requests BIGINT NOT NULL,
    successful_requests BIGINT NOT NULL,
    failed_requests BIGINT NOT NULL,
    p50_latency_ms BIGINT NULL,
    p95_latency_ms BIGINT NULL,
    p99_latency_ms BIGINT NULL,
    status_code_counts JSON NULL,
    final_result BOOLEAN NOT NULL,
    collected_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (load_test_id),
    CONSTRAINT fk_load_test_results_load_test FOREIGN KEY (load_test_id) REFERENCES load_tests (id)
);
