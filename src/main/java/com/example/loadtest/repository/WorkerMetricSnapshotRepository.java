package com.example.loadtest.repository;

import com.example.loadtest.model.WorkerMetricSnapshot;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface WorkerMetricSnapshotRepository extends JpaRepository<WorkerMetricSnapshot, UUID> {
    @Query("""
            select snapshot from WorkerMetricSnapshot snapshot
            where snapshot.worker.workRequest.id = :workRequestId
            order by snapshot.worker.id, snapshot.capturedAt desc
            """)
    List<WorkerMetricSnapshot> findAllForWorkRequest(UUID workRequestId);
}
