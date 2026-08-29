package com.example.loadtest.config;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
public class WorkflowExecutorConfiguration {
    @Bean(destroyMethod = "shutdown")
    ScheduledExecutorService workflowLeaseHeartbeatExecutor() {
        return Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "workflow-lease-heartbeat");
            thread.setDaemon(true);
            return thread;
        });
    }

    @Bean("workflowTaskExecutor")
    Executor workflowTaskExecutor(WorkflowLauncherProperties properties) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(properties.getExecutorPoolSize());
        executor.setMaxPoolSize(properties.getExecutorPoolSize());
        executor.setQueueCapacity(properties.getExecutorQueueCapacity());
        executor.setThreadNamePrefix("workflow-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        return executor;
    }

    @Bean("localWorkerTaskExecutor")
    Executor localWorkerTaskExecutor(WorkflowLauncherProperties properties) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(properties.getLocalWorkerPoolSize());
        executor.setMaxPoolSize(properties.getLocalWorkerPoolSize());
        executor.setQueueCapacity(0);
        executor.setThreadNamePrefix("load-worker-");
        executor.initialize();
        return executor;
    }
}
