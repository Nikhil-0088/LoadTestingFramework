package com.example.loadtest.api.dto;

import com.example.loadtest.model.LoadTestStatus;
import com.example.loadtest.model.WorkRequestStatus;
import com.example.loadtest.model.WorkflowStep;
import java.time.Instant;
import java.util.UUID;

public record LoadTestStatusResponse(
        UUID loadTestId,
        String name,
        LoadTestStatus loadTestStatus,
        Instant createdAt,
        Instant startedAt,
        Instant endsAt,
        Instant completedAt,
        UUID workRequestId,
        WorkRequestStatus workRequestStatus,
        WorkflowStep currentStep,
        String failureReason) {
}
