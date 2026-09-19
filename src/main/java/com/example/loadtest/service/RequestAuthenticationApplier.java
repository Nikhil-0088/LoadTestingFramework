package com.example.loadtest.service;

import com.example.loadtest.model.ApiKeyLocation;
import com.example.loadtest.model.AuthenticationType;
import com.example.loadtest.model.RequestDefinition;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpRequest;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Applies resolved authentication immediately before an outbound request is sent. */
@Component
@RequiredArgsConstructor
public class RequestAuthenticationApplier {
    private final AuthenticationSecretResolver secretResolver;

    public URI authenticatedUri(RequestDefinition definition) {
        if (definition.getAuthType() != AuthenticationType.API_KEY
                || apiKeyLocation(definition) != ApiKeyLocation.QUERY_PARAMETER) {
            return URI.create(definition.getUrl());
        }
        String separator = definition.getUrl().contains("?") ? "&" : "?";
        String key = URLEncoder.encode(definition.getApiKeyHeaderName(), StandardCharsets.UTF_8);
        String value = URLEncoder.encode(secretResolver.resolve(definition.getAuthSecretReference()), StandardCharsets.UTF_8);
        return URI.create(definition.getUrl() + separator + key + "=" + value);
    }

    public void applyHeaders(RequestDefinition definition, HttpRequest.Builder builder) {
        AuthenticationType type = definition.getAuthType() == null ? AuthenticationType.NONE : definition.getAuthType();
        switch (type) {
            case NONE -> { }
            case BASIC -> builder.setHeader("Authorization", "Basic " + Base64.getEncoder()
                    .encodeToString(secretResolver.resolve(definition.getAuthSecretReference()).getBytes(StandardCharsets.UTF_8)));
            case BEARER_TOKEN -> builder.setHeader("Authorization", "Bearer "
                    + secretResolver.resolve(definition.getAuthSecretReference()));
            case API_KEY -> {
                if (apiKeyLocation(definition) == ApiKeyLocation.HEADER) {
                    builder.setHeader(definition.getApiKeyHeaderName(), secretResolver.resolve(definition.getAuthSecretReference()));
                }
            }
        }
    }

    private ApiKeyLocation apiKeyLocation(RequestDefinition definition) {
        return definition.getApiKeyLocation() == null ? ApiKeyLocation.HEADER : definition.getApiKeyLocation();
    }
}
