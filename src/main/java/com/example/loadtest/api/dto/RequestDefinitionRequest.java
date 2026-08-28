package com.example.loadtest.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.Map;
import org.springframework.http.HttpMethod;

public record RequestDefinitionRequest(
        @NotBlank String name,
        @NotNull HttpMethod method,
        @NotBlank String url,
        Map<String, String> headers,
        String body,
        @Valid AuthenticationRequest authentication) {
}
