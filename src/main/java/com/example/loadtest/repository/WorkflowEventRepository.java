package com.example.loadtest.repository;

import com.example.loadtest.model.WorkflowEvent;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WorkflowEventRepository extends JpaRepository<WorkflowEvent, UUID> {
}
