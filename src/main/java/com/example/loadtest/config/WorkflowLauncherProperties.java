package com.example.loadtest.config;

import jakarta.validation.constraints.Min;
import java.time.Duration;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

@Component
@ConfigurationProperties(prefix = "loadtest.workflow-launcher")
@Validated
@Getter
@Setter
public class WorkflowLauncherProperties {
    private String nodeId = "local-dev";

    @Min(1)
    private int batchSize = 10;

    private Duration leaseDuration = Duration.ofSeconds(30);

    @Min(1)
    private int executorPoolSize = 4;

    @Min(0)
    private int executorQueueCapacity = 100;

    @Min(1)
    private int localWorkerPoolSize = 50;
}
