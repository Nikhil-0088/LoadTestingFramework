package com.example.loadtest.repository;

import com.example.loadtest.model.LoadTest;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LoadTestRepository extends JpaRepository<LoadTest, UUID> {
}
