package com.example.loadtest.api.dto;

import com.example.loadtest.model.LoadTestStatus;
import com.example.loadtest.model.WorkRequestStatus;
import java.util.UUID;

public record CreateLoadTestResponse(
        UUID loadTestId,
        UUID workRequestId,
        LoadTestStatus loadTestStatus,
        WorkRequestStatus workRequestStatus) {
}
