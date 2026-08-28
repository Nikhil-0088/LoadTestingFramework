package com.example.loadtest.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

public record CreateLoadTestRequest(
        @NotBlank String name,
        @Min(1) int durationSeconds,
        @Min(1) int requestsPerMinute,
        @Min(1) int workerCount,
        @NotEmpty List<@Valid RequestDefinitionRequest> requests) {
}
