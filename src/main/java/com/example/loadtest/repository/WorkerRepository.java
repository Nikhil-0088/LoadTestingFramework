package com.example.loadtest.repository;

import com.example.loadtest.model.Worker;
import com.example.loadtest.model.WorkerStatus;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WorkerRepository extends JpaRepository<Worker, UUID> {
    List<Worker> findByWorkRequestId(UUID workRequestId);

    long countByWorkRequestIdAndStatus(UUID workRequestId, WorkerStatus status);

    long countByWorkRequestIdAndStatusIn(UUID workRequestId, Collection<WorkerStatus> statuses);
}
