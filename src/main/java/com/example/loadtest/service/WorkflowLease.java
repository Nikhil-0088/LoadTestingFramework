package com.example.loadtest.service;

import java.util.UUID;

/** Immutable fencing token for one ownership period of a work request. */
public record WorkflowLease(UUID workRequestId, String owner, long generation) {
}
