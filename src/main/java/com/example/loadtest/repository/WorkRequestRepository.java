package com.example.loadtest.repository;

import com.example.loadtest.model.WorkRequest;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WorkRequestRepository extends JpaRepository<WorkRequest, UUID> {
    Optional<WorkRequest> findByLoadTestId(UUID loadTestId);
}
