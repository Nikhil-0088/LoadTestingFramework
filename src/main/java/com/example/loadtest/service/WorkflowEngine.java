package com.example.loadtest.service;

import com.example.loadtest.model.*;
import com.example.loadtest.repository.*;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Runs one short, durable workflow step. Its state is persisted before another node can pick it up. */
@Service
@RequiredArgsConstructor
@Slf4j
public class WorkflowEngine {
    private static final Duration RECHECK_DELAY = Duration.ofSeconds(1);
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    private final WorkRequestRepository workRequestRepository;
    private final WorkerRepository workerRepository;
    private final WorkflowEventRepository workflowEventRepository;
    private final WorkerMetricSnapshotRepository metricSnapshotRepository;
    private final LoadTestResultRepository loadTestResultRepository;
    private final WorkflowLeaseGuard leaseGuard;
    private final LocalWorkerRuntime localWorkerRuntime;

    @Transactional
    public void execute(WorkflowLease lease) {
        try (WorkflowLeaseGuard.LeaseHandle guard = leaseGuard.start(lease)) {
            if (workRequestRepository.markAcceptedRunningOrVerifyLeaseOwned(
                    lease.workRequestId(), lease.owner(), lease.generation(),
                    WorkRequestStatus.ACCEPTED, WorkRequestStatus.RUNNING,
                    List.of(WorkRequestStatus.ACCEPTED, WorkRequestStatus.RUNNING, WorkRequestStatus.CLEANUP_REQUIRED),
                    Instant.now()) != 1) {
                return;
            }
            WorkRequest request = workRequestRepository.findById(lease.workRequestId())
                    .orElseThrow(() -> new IllegalStateException("Claimed work request disappeared"));
            runStep(request, guard);
            guard.assertStillOwned();
            request.setUpdatedAt(Instant.now());
            workRequestRepository.save(request);
            release(lease, request.getStatus());
        } catch (WorkflowLeaseGuard.LeaseLostException exception) {
            log.warn("Lease lost for workflow {}; another node will recover it", lease.workRequestId());
        } catch (Exception exception) {
            handleFailure(lease, exception);
        }
    }

    private void runStep(WorkRequest request, WorkflowLeaseGuard.LeaseHandle guard) {
        log.info("Workflow {} executing {}", request.getId(), request.getCurrentStep());
        rejectAbandonedV1Workers(request);
        switch (request.getCurrentStep()) {
            case VALIDATE -> validate(request, guard);
            case PROVISION_WORKERS -> provisionWorkers(request);
            case WAIT_FOR_WORKERS -> waitForWorkers(request);
            case START_LOAD -> startLoad(request);
            case RUN_LOAD -> runLoad(request);
            case STOP_WORKERS -> stopWorkers(request);
            case COLLECT_RESULTS -> collectResults(request);
            case COMPLETE -> complete(request);
        }
    }

    /** Java threads vanish with their JVM. A stale heartbeat therefore fails safely instead of pretending recovery continued them. */
    private void rejectAbandonedV1Workers(WorkRequest request) {
        // Cleanup is the recovery path for a crashed V1 JVM. It must be allowed to finish even
        // though its local worker heartbeat is necessarily stale.
        if (request.getStatus() == WorkRequestStatus.CLEANUP_REQUIRED) return;
        if (request.getCurrentStep() == WorkflowStep.VALIDATE || request.getCurrentStep() == WorkflowStep.PROVISION_WORKERS) return;
        Instant staleBefore = Instant.now().minusSeconds(30);
        boolean abandoned = workerRepository.findByWorkRequestId(request.getId()).stream().anyMatch(worker ->
                (worker.getStatus() == WorkerStatus.STARTING || worker.getStatus() == WorkerStatus.RUNNING)
                        && (worker.getLastHeartbeat() == null || worker.getLastHeartbeat().isBefore(staleBefore)));
        if (abandoned) throw new IllegalStateException("Local V1 worker heartbeat expired; cleaning up and failing safely");
    }

    private void validate(WorkRequest request, WorkflowLeaseGuard.LeaseHandle guard) {
        LoadTest loadTest = request.getLoadTest();
        if (loadTest.getStatus() != LoadTestStatus.ACCEPTED) {
            throw new IllegalStateException("Load test is not in ACCEPTED state");
        }
        for (RequestDefinition definition : loadTest.getRequests()) {
            guard.assertStillOwned();
            validateTarget(definition);
        }
        loadTest.setStatus(LoadTestStatus.PROVISIONING);
        moveTo(request, WorkflowStep.PROVISION_WORKERS, Instant.now());
    }

    private void validateTarget(RequestDefinition definition) {
        if (definition.getAuthType() != null && definition.getAuthType() != AuthenticationType.NONE) {
            throw new IllegalArgumentException("V1 cannot resolve authentication secret references yet");
        }
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(definition.getUrl())).timeout(Duration.ofSeconds(10));
        if (definition.getHeaders() != null) definition.getHeaders().forEach(builder::header);
        builder.method(definition.getHttpMethod(), definition.getBody() == null || definition.getBody().isBlank()
                ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(definition.getBody()));
        IOException lastFailure = null;
        for (int attempt = 0; attempt < 3; attempt++) {
            try {
                if (HTTP.send(builder.build(), HttpResponse.BodyHandlers.discarding()).statusCode() < 500) return;
            } catch (IOException exception) {
                lastFailure = exception;
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Validation interrupted", exception);
            }
        }
        throw new IllegalStateException("Validation call failed for " + definition.getName(), lastFailure);
    }

    private void provisionWorkers(WorkRequest request) {
        if (workerRepository.findByWorkRequestId(request.getId()).isEmpty()) {
            LoadTest test = request.getLoadTest();
            int base = test.getRequestsPerMinute() / test.getWorkerCount();
            int remainder = test.getRequestsPerMinute() % test.getWorkerCount();
            for (int index = 0; index < test.getWorkerCount(); index++) {
                Worker worker = new Worker();
                worker.setId(UUID.randomUUID());
                worker.setLoadTest(test);
                worker.setWorkRequest(request);
                worker.setWorkerType(WorkerType.LOCAL_THREAD);
                worker.setStatus(WorkerStatus.STARTING);
                worker.setOwnerNodeId(request.getLeaseOwner());
                worker.setAllocatedRequestsPerMinute(base + (index < remainder ? 1 : 0));
                worker.setCreatedAt(Instant.now());
                worker.setLastHeartbeat(Instant.now());
                workerRepository.save(worker);
            }
        }
        localWorkerRuntime.prepare(workerRepository.findByWorkRequestId(request.getId()).stream().map(Worker::getId).toList());
        moveTo(request, WorkflowStep.WAIT_FOR_WORKERS, Instant.now());
    }

    private void waitForWorkers(WorkRequest request) {
        long failed = workerRepository.countByWorkRequestIdAndStatus(request.getId(), WorkerStatus.FAILED);
        long starting = workerRepository.countByWorkRequestIdAndStatus(request.getId(), WorkerStatus.STARTING);
        if (failed > 0) throw new IllegalStateException("A worker failed while starting");
        if (starting == request.getLoadTest().getWorkerCount()) moveTo(request, WorkflowStep.START_LOAD, Instant.now());
        else request.setNextAttemptAt(Instant.now().plus(RECHECK_DELAY));
    }

    private void startLoad(WorkRequest request) {
        Instant now = Instant.now();
        LoadTest test = request.getLoadTest();
        test.setStatus(LoadTestStatus.RUNNING);
        test.setStartedAt(now);
        test.setEndsAt(now.plusSeconds(test.getDurationSeconds()));
        createEvent(request, EventType.START_WORKERS);
        moveTo(request, WorkflowStep.RUN_LOAD, now);
    }

    private void runLoad(WorkRequest request) {
        Instant now = Instant.now();
        if (request.getLoadTest().getEndsAt().isAfter(now)) request.setNextAttemptAt(now.plus(RECHECK_DELAY));
        else moveTo(request, WorkflowStep.STOP_WORKERS, now);
    }

    private void stopWorkers(WorkRequest request) {
        LoadTest test = request.getLoadTest();
        if (test.getStatus() != LoadTestStatus.STOPPING) {
            createEvent(request, EventType.STOP_WORKERS);
            test.setStatus(LoadTestStatus.STOPPING);
        }
        if (request.getStatus() == WorkRequestStatus.CLEANUP_REQUIRED) {
            // A local Java thread died with the previous JVM. There is no process left to receive
            // a stop signal, so record its terminal state and finish cleanup instead of retrying forever.
            for (Worker worker : workerRepository.findByWorkRequestId(request.getId())) {
                if (worker.getStatus() != WorkerStatus.STOPPED) {
                    worker.setStatus(WorkerStatus.STOPPED);
                    worker.setCompletedAt(Instant.now());
                    worker.setLastError("Stopped during cleanup after local worker heartbeat expired");
                    workerRepository.save(worker);
                }
            }
            failNow(request);
            return;
        }
        long stopped = workerRepository.countByWorkRequestIdAndStatus(request.getId(), WorkerStatus.STOPPED);
        if (stopped < test.getWorkerCount()) {
            request.setNextAttemptAt(Instant.now().plus(RECHECK_DELAY));
        } else {
            moveTo(request, WorkflowStep.COLLECT_RESULTS, Instant.now());
        }
    }

    private void collectResults(WorkRequest request) {
        Map<UUID, WorkerMetricSnapshot> latest = new HashMap<>();
        for (WorkerMetricSnapshot snapshot : metricSnapshotRepository.findAllForWorkRequest(request.getId()))
            latest.putIfAbsent(snapshot.getWorker().getId(), snapshot);
        Map<String, Long> statusCodes = new HashMap<>();
        latest.values().forEach(snapshot -> {
            if (snapshot.getStatusCodeCounts() != null)
                snapshot.getStatusCodeCounts().forEach((key, value) -> statusCodes.merge(key, value, Long::sum));
        });
        LoadTestResult result = new LoadTestResult();
        result.setLoadTest(request.getLoadTest());
        result.setTotalRequests(latest.values().stream().mapToLong(WorkerMetricSnapshot::getTotalRequests).sum());
        result.setSuccessfulRequests(latest.values().stream().mapToLong(WorkerMetricSnapshot::getSuccessfulRequests).sum());
        result.setFailedRequests(latest.values().stream().mapToLong(WorkerMetricSnapshot::getFailedRequests).sum());
        result.setStatusCodeCounts(statusCodes);
        result.setFinalResult(true);
        result.setCollectedAt(Instant.now());
        loadTestResultRepository.save(result);
        moveTo(request, WorkflowStep.COMPLETE, Instant.now());
    }

    private void complete(WorkRequest request) {
        Instant now = Instant.now();
        request.setStatus(WorkRequestStatus.COMPLETED);
        request.setCompletedAt(now);
        request.getLoadTest().setStatus(LoadTestStatus.COMPLETED);
        request.getLoadTest().setCompletedAt(now);
    }

    private void moveTo(WorkRequest request, WorkflowStep step, Instant now) {
        request.setCurrentStep(step);
        request.setNextAttemptAt(now);
    }

    private void createEvent(WorkRequest request, EventType type) {
        WorkflowEvent event = new WorkflowEvent();
        event.setId(UUID.randomUUID());
        event.setWorkRequest(request);
        event.setEventType(type);
        event.setStatus(EventStatus.PENDING);
        event.setCreatedAt(Instant.now());
        workflowEventRepository.save(event);
    }

    @Transactional
    protected void handleFailure(WorkflowLease lease, Exception exception) {
        log.error("Workflow {} failed", lease.workRequestId(), exception);
        WorkRequest request = workRequestRepository.findById(lease.workRequestId()).orElse(null);
        if (request == null || !lease.owner().equals(request.getLeaseOwner()) || lease.generation() != request.getLeaseGeneration()) return;
        request.setLastError(exception.getMessage());
        if (workerRepository.findByWorkRequestId(request.getId()).isEmpty()) failNow(request);
        else {
            request.setStatus(WorkRequestStatus.CLEANUP_REQUIRED);
            request.getLoadTest().setStatus(LoadTestStatus.STOPPING);
            moveTo(request, WorkflowStep.STOP_WORKERS, Instant.now());
        }
        workRequestRepository.save(request);
        release(lease, request.getStatus());
    }

    private void failNow(WorkRequest request) {
        Instant now = Instant.now();
        request.setStatus(WorkRequestStatus.FAILED);
        request.setCompletedAt(now);
        request.getLoadTest().setStatus(LoadTestStatus.FAILED);
        request.getLoadTest().setCompletedAt(now);
        request.getLoadTest().setFailureReason(request.getLastError());
    }

    private void release(WorkflowLease lease, WorkRequestStatus status) {
        workRequestRepository.releaseLease(lease.workRequestId(), lease.owner(), lease.generation(), status, Instant.now());
    }
}
