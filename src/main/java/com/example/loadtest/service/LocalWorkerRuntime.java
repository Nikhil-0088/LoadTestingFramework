package com.example.loadtest.service;

import com.example.loadtest.model.LoadTestStatus;
import com.example.loadtest.model.RequestDefinition;
import com.example.loadtest.model.Worker;
import com.example.loadtest.model.WorkerMetricSnapshot;
import com.example.loadtest.model.WorkerStatus;
import com.example.loadtest.repository.RequestDefinitionRepository;
import com.example.loadtest.repository.WorkerMetricSnapshotRepository;
import com.example.loadtest.repository.WorkerRepository;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.LockSupport;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

/** Local V1 worker: waits for the DB state RUNNING, then sends its allocated share of requests. */
@Service
@Slf4j
public class LocalWorkerRuntime {
    private final WorkerRepository workerRepository;
    private final RequestDefinitionRepository requestDefinitionRepository;
    private final WorkerMetricSnapshotRepository snapshotRepository;
    private final Executor executor;
    private final java.util.Set<UUID> active = ConcurrentHashMap.newKeySet();
    private final HttpClient client = HttpClient.newHttpClient();

    public LocalWorkerRuntime(
            WorkerRepository workerRepository,
            RequestDefinitionRepository requestDefinitionRepository,
            WorkerMetricSnapshotRepository snapshotRepository,
            @Qualifier("localWorkerTaskExecutor") Executor executor) {
        this.workerRepository = workerRepository;
        this.requestDefinitionRepository = requestDefinitionRepository;
        this.snapshotRepository = snapshotRepository;
        this.executor = executor;
    }

    public void prepare(Collection<UUID> workerIds) {
        workerIds.forEach(id -> { if (active.add(id)) executor.execute(() -> run(id)); });
    }

    private void run(UUID workerId) {
        AtomicLong total = new AtomicLong(), success = new AtomicLong(), failed = new AtomicLong();
        Map<String, AtomicLong> codes = new ConcurrentHashMap<>();
        Instant lastSnapshot = Instant.EPOCH;
        long nextRequestAtNanos = System.nanoTime();
        int nextRequestIndex = 0;
        try {
            while (!Thread.currentThread().isInterrupted()) {
                Worker worker = workerRepository.findById(workerId).orElse(null);
                if (worker == null) { sleep(100); continue; }
                if (worker.getStatus() == WorkerStatus.STOPPED || worker.getStatus() == WorkerStatus.FAILED) return;
                Instant now = Instant.now();
                if (worker.getLoadTest().getStatus() != LoadTestStatus.RUNNING || !worker.getLoadTest().getEndsAt().isAfter(now)) {
                    if (worker.getStartedAt() != null) return;
                    worker.setLastHeartbeat(now); workerRepository.save(worker); sleep(100); continue;
                }
                if (worker.getStatus() != WorkerStatus.RUNNING) {
                    worker.setStatus(WorkerStatus.RUNNING); worker.setStartedAt(now); workerRepository.save(worker);
                    nextRequestAtNanos = System.nanoTime();
                }
                List<RequestDefinition> definitions = requestDefinitionRepository.findByLoadTestId(worker.getLoadTest().getId());
                if (definitions.isEmpty()) throw new IllegalStateException("Worker has no request definitions");
                send(definitions.get(nextRequestIndex++ % definitions.size()), total, success, failed, codes);
                if (lastSnapshot.plusSeconds(5).isBefore(Instant.now())) {
                    snapshot(worker, total.get(), success.get(), failed.get(), codes); lastSnapshot = Instant.now();
                }
                nextRequestAtNanos += 60_000_000_000L / worker.getAllocatedRequestsPerMinute();
                if (nextRequestAtNanos < System.nanoTime()) nextRequestAtNanos = System.nanoTime();
                waitUntil(nextRequestAtNanos);
            }
        } catch (Exception exception) {
            workerRepository.findById(workerId).ifPresent(worker -> { worker.setStatus(WorkerStatus.FAILED); worker.setLastError(exception.getMessage()); workerRepository.save(worker); });
            log.error("Worker {} failed", workerId, exception);
        } finally {
            workerRepository.findById(workerId).ifPresent(worker -> {
                snapshot(worker, total.get(), success.get(), failed.get(), codes);
                if (worker.getStatus() != WorkerStatus.FAILED) {
                    worker.setStatus(WorkerStatus.STOPPED);
                    worker.setCompletedAt(Instant.now());
                    workerRepository.save(worker);
                }
            });
            active.remove(workerId);
        }
    }

    private void send(RequestDefinition definition, AtomicLong total, AtomicLong success, AtomicLong failed, Map<String, AtomicLong> codes) {
        try {
            HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(definition.getUrl()));
            if (definition.getHeaders() != null) definition.getHeaders().forEach(request::header);
            request.method(definition.getHttpMethod(), definition.getBody() == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(definition.getBody()));
            int status = client.send(request.build(), HttpResponse.BodyHandlers.discarding()).statusCode();
            total.incrementAndGet(); codes.computeIfAbsent(String.valueOf(status), unused -> new AtomicLong()).incrementAndGet();
            if (status < 400) success.incrementAndGet(); else failed.incrementAndGet();
        } catch (Exception exception) { total.incrementAndGet(); failed.incrementAndGet(); codes.computeIfAbsent("NETWORK_ERROR", unused -> new AtomicLong()).incrementAndGet(); }
    }

    private void snapshot(Worker worker, long total, long success, long failed, Map<String, AtomicLong> codes) {
        worker.setLastHeartbeat(Instant.now());
        workerRepository.save(worker);
        WorkerMetricSnapshot snapshot = new WorkerMetricSnapshot();
        snapshot.setId(UUID.randomUUID()); snapshot.setWorker(worker); snapshot.setCapturedAt(Instant.now());
        snapshot.setTotalRequests(total); snapshot.setSuccessfulRequests(success); snapshot.setFailedRequests(failed);
        Map<String, Long> values = new java.util.HashMap<>(); codes.forEach((code, count) -> values.put(code, count.get()));
        snapshot.setStatusCodeCounts(values); snapshotRepository.save(snapshot);
    }

    private void sleep(long milliseconds) { try { Thread.sleep(milliseconds); } catch (InterruptedException exception) { Thread.currentThread().interrupt(); } }

    private void waitUntil(long scheduledTimeNanos) {
        long remaining = scheduledTimeNanos - System.nanoTime();
        if (remaining > 0) LockSupport.parkNanos(remaining);
    }
}
