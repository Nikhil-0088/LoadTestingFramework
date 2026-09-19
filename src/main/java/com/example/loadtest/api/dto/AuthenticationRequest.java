package com.example.loadtest.api.dto;

import com.example.loadtest.model.AuthenticationType;
import com.example.loadtest.model.ApiKeyLocation;
import jakarta.validation.constraints.NotNull;

public record AuthenticationRequest(
        @NotNull AuthenticationType type,
        String secretReference,
        String apiKeyHeaderName,
        ApiKeyLocation apiKeyLocation) {
}
