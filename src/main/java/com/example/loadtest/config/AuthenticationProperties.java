package com.example.loadtest.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** Limits secret references to explicitly named process environment variables. */
@Component
@ConfigurationProperties(prefix = "loadtest.authentication")
@Getter
@Setter
public class AuthenticationProperties {
    private String allowedEnvironmentPrefix = "LOADTEST_SECRET_";
}
