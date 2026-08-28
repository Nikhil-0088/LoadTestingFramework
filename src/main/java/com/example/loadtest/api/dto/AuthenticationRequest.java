package com.example.loadtest.api.dto;

import com.example.loadtest.model.AuthenticationType;
import jakarta.validation.constraints.NotNull;

public record AuthenticationRequest(
        @NotNull AuthenticationType type,
        String secretReference,
        String apiKeyHeaderName) {
}
