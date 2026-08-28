package com.example.loadtest.model;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
@Table(name = "workflow_events")
@Getter
@Setter
@NoArgsConstructor
public class WorkflowEvent {
    @Id @JdbcTypeCode(SqlTypes.CHAR) private UUID id;
    @ManyToOne(optional = false) @JoinColumn(name = "work_request_id") private WorkRequest workRequest;
    @Enumerated(EnumType.STRING) private EventType eventType;
    @Enumerated(EnumType.STRING) private EventStatus status;
    @JdbcTypeCode(SqlTypes.JSON) private Map<String, Object> payload;
    private Instant createdAt;
    private Instant processedAt;
    private String lastError;
}
