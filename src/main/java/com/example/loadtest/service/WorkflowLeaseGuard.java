package com.example.loadtest.service;

import com.example.loadtest.config.WorkflowLauncherProperties;
import com.example.loadtest.model.WorkRequestStatus;
import com.example.loadtest.repository.WorkRequestRepository;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Renews a lease while a workflow step is executing and exposes lease loss to the step. */
@Component
@RequiredArgsConstructor
public class WorkflowLeaseGuard {
    private final WorkRequestRepository workRequestRepository;
    private final WorkflowLauncherProperties properties;
    private final ScheduledExecutorService workflowLeaseHeartbeatExecutor;

    public LeaseHandle start(WorkflowLease lease) {
        AtomicBoolean lost = new AtomicBoolean(false);
        long intervalMillis = Math.max(1_000, properties.getLeaseDuration().toMillis() / 3);
        ScheduledFuture<?> renewal = workflowLeaseHeartbeatExecutor.scheduleWithFixedDelay(() -> {
            Instant now = Instant.now();
            int renewed = workRequestRepository.renewLease(
                    lease.workRequestId(), lease.owner(), lease.generation(),
                    now.plus(properties.getLeaseDuration()),
                    List.of(WorkRequestStatus.ACCEPTED, WorkRequestStatus.RUNNING, WorkRequestStatus.CLEANUP_REQUIRED),
                    now);
            if (renewed != 1) {
                lost.set(true);
            }
        }, intervalMillis, intervalMillis, TimeUnit.MILLISECONDS);
        return new LeaseHandle(lost, renewal);
    }

    public static final class LeaseHandle implements AutoCloseable {
        private final AtomicBoolean lost;
        private final ScheduledFuture<?> renewal;

        private LeaseHandle(AtomicBoolean lost, ScheduledFuture<?> renewal) {
            this.lost = lost;
            this.renewal = renewal;
        }

        public void assertStillOwned() {
            if (lost.get()) {
                throw new LeaseLostException();
            }
        }

        @Override
        public void close() {
            renewal.cancel(false);
        }
    }

    public static class LeaseLostException extends RuntimeException {
        public LeaseLostException() {
            super("Workflow lease was lost");
        }
    }
}
