package com.example.loadtest.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
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
@Table(name = "worker_metric_snapshots")
@Getter
@Setter
@NoArgsConstructor
public class WorkerMetricSnapshot {
    @Id @JdbcTypeCode(SqlTypes.CHAR) private UUID id;
    @ManyToOne(optional = false) @JoinColumn(name = "worker_id") private Worker worker;
    private Instant capturedAt;
    private long totalRequests;
    private long successfulRequests;
    private long failedRequests;
    @JdbcTypeCode(SqlTypes.JSON) private Map<String, Long> statusCodeCounts;
    @JdbcTypeCode(SqlTypes.JSON) private Map<String, Object> latencyHistogram;
}
