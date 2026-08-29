package com.example.loadtest.repository;

import com.example.loadtest.model.RequestDefinition;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RequestDefinitionRepository extends JpaRepository<RequestDefinition, UUID> {
    List<RequestDefinition> findByLoadTestId(UUID loadTestId);
}
