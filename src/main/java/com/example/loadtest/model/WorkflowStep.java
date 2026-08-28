package com.example.loadtest.model;

public enum WorkflowStep {
    VALIDATE, PROVISION_WORKERS, WAIT_FOR_WORKERS, START_LOAD,
    RUN_LOAD, STOP_WORKERS, COLLECT_RESULTS, COMPLETE
}
