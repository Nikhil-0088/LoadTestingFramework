package com.example.loadtest.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "load_test_results")
@Getter
@Setter
@NoArgsConstructor
public class LoadTestResult {
    @Id @JdbcTypeCode(SqlTypes.CHAR) private UUID loadTestId;
    @OneToOne(optional = false) @MapsId @JoinColumn(name = "load_test_id") private LoadTest loadTest;
    private long totalRequests;
    private long successfulRequests;
    private long failedRequests;
    private Long p50LatencyMs;
    private Long p95LatencyMs;
    private Long p99LatencyMs;
    @JdbcTypeCode(SqlTypes.JSON) private Map<String, Long> statusCodeCounts;
    private boolean finalResult;
    private Instant collectedAt;
}
