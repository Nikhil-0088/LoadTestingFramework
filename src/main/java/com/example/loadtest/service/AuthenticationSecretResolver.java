package com.example.loadtest.service;

import com.example.loadtest.config.AuthenticationProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/** Resolves only env: references; raw credentials are never stored in MySQL. */
@Component
@RequiredArgsConstructor
public class AuthenticationSecretResolver {
    private static final String ENV_PREFIX = "env:";
    private final Environment environment;
    private final AuthenticationProperties properties;

    public String resolve(String secretReference) {
        if (secretReference == null || !secretReference.startsWith(ENV_PREFIX)) {
            throw new IllegalArgumentException("Authentication secretReference must use env:VARIABLE_NAME");
        }
        String variableName = secretReference.substring(ENV_PREFIX.length());
        if (!variableName.startsWith(properties.getAllowedEnvironmentPrefix())) {
            throw new IllegalArgumentException("Authentication environment variable must start with "
                    + properties.getAllowedEnvironmentPrefix());
        }
        String value = environment.getProperty(variableName);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Authentication secret is unavailable for reference " + secretReference);
        }
        return value;
    }
}
