package com.example.loadtest.model;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "work_requests")
@Getter
@Setter
@NoArgsConstructor
public class WorkRequest {
    @Id
    @JdbcTypeCode(SqlTypes.CHAR)
    private UUID id;
    @OneToOne(optional = false)
    @JoinColumn(name = "load_test_id", unique = true)
    private LoadTest loadTest;
    @Enumerated(EnumType.STRING)
    private WorkRequestStatus status;
    @Enumerated(EnumType.STRING)
    private WorkflowStep currentStep;
    private int attemptCount;
    private Instant nextAttemptAt;
    private String leaseOwner;
    private Instant leaseUntil;
    private String lastError;
    @Version
    private long version;
    private Instant createdAt;
    private Instant updatedAt;
    private Instant completedAt;
}
