package com.example.loadtest.service;

import com.example.loadtest.config.WorkflowLauncherProperties;
import com.example.loadtest.model.WorkRequestStatus;
import com.example.loadtest.repository.WorkRequestRepository;
import java.time.Instant;
import java.util.UUID;
import java.util.List;
import java.util.concurrent.Executor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.core.task.TaskRejectedException;

/** Polls new and recoverable work requests and dispatches only requests successfully leased by this node. */
@Component
@ConditionalOnProperty(prefix = "loadtest.workflow-launcher", name = "enabled", havingValue = "true", matchIfMissing = true)
@Slf4j
public class WorkflowLauncherDaemon {
    private final WorkRequestRepository workRequestRepository;
    private final WorkflowLauncherProperties properties;
    private final WorkflowEngine workflowEngine;
    private final Executor workflowTaskExecutor;

    public WorkflowLauncherDaemon(
            WorkRequestRepository workRequestRepository,
            WorkflowLauncherProperties properties,
            WorkflowEngine workflowEngine,
            @Qualifier("workflowTaskExecutor") Executor workflowTaskExecutor) {
        this.workRequestRepository = workRequestRepository;
        this.properties = properties;
        this.workflowEngine = workflowEngine;
        this.workflowTaskExecutor = workflowTaskExecutor;
    }

    @Scheduled(fixedDelayString = "${loadtest.workflow-launcher.poll-interval:PT2S}")
    public void pollAndDispatch() {
        Instant now = Instant.now();
        for (UUID workRequestId : workRequestRepository.findRunnableIds(
                List.of(WorkRequestStatus.ACCEPTED, WorkRequestStatus.RUNNING, WorkRequestStatus.CLEANUP_REQUIRED),
                now, PageRequest.of(0, properties.getBatchSize()))) {
            WorkflowLease lease = tryAcquireLease(workRequestId, now);
            if (lease != null) {
                dispatch(lease);
            }
        }
    }

    WorkflowLease tryAcquireLease(UUID workRequestId, Instant now) {
        Instant leaseUntil = now.plus(properties.getLeaseDuration());
        int claimed = workRequestRepository.tryAcquireLease(
                workRequestId,
                List.of(WorkRequestStatus.ACCEPTED, WorkRequestStatus.RUNNING, WorkRequestStatus.CLEANUP_REQUIRED),
                properties.getNodeId(),
                leaseUntil,
                now);
        if (claimed != 1) {
            return null;
        }
        long generation = workRequestRepository.findById(workRequestId)
                .orElseThrow(() -> new IllegalStateException("Claimed work request disappeared: " + workRequestId))
                .getLeaseGeneration();
        return new WorkflowLease(workRequestId, properties.getNodeId(), generation);
    }

    private void dispatch(WorkflowLease lease) {
        try {
            workflowTaskExecutor.execute(() -> workflowEngine.execute(lease));
            log.info("Leased and dispatched workflow {} on node {}", lease.workRequestId(), properties.getNodeId());
        } catch (TaskRejectedException ex) {
            releaseLeaseAfterRejectedDispatch(lease);
            log.warn("Workflow executor is full; released lease for work request {}", lease.workRequestId());
        }
    }

    void releaseLeaseAfterRejectedDispatch(WorkflowLease lease) {
        workRequestRepository.releaseLease(
                lease.workRequestId(), lease.owner(), lease.generation(), WorkRequestStatus.ACCEPTED, Instant.now());
    }
}
