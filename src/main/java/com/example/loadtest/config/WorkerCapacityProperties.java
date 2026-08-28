package com.example.loadtest.config;

import jakarta.validation.constraints.Min;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

@Component
@ConfigurationProperties(prefix = "loadtest.worker")
@Validated
@Getter
@Setter
public class WorkerCapacityProperties {
    @Min(1)
    private int maxRequestsPerMinute = 1_00; //Initial conservative limit before any testing, need fine-tuning

    @Min(1)
    private int maxWorkersPerTest = 50;
}
