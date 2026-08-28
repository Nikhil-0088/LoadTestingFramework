package com.example.loadtest.model;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "workers")
@Getter
@Setter
@NoArgsConstructor
public class Worker {
    @Id @JdbcTypeCode(SqlTypes.CHAR) private UUID id;
    @ManyToOne(optional = false) @JoinColumn(name = "load_test_id") private LoadTest loadTest;
    @ManyToOne(optional = false) @JoinColumn(name = "work_request_id") private WorkRequest workRequest;
    @Enumerated(EnumType.STRING) private WorkerType workerType;
    @Enumerated(EnumType.STRING) private WorkerStatus status;
    private String ownerNodeId;
    private int allocatedRequestsPerMinute;
    private Instant createdAt;
    private Instant startedAt;
    private Instant completedAt;
    private Instant lastHeartbeat;
    private String lastError;
}
