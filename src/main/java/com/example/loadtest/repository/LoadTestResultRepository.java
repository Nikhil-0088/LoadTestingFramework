package com.example.loadtest.repository;

import com.example.loadtest.model.LoadTestResult;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LoadTestResultRepository extends JpaRepository<LoadTestResult, UUID> {
}
