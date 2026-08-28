package com.example.loadtest.model;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "load_tests")
@Getter
@Setter
@NoArgsConstructor
public class LoadTest {
    @Id
    @JdbcTypeCode(SqlTypes.CHAR)
    private UUID id;
    private String name;
    @Enumerated(EnumType.STRING)
    private LoadTestStatus status;
    private int requestsPerMinute;
    private int durationSeconds;
    private int workerCount;
    private Instant createdAt;
    private Instant startedAt;
    private Instant endsAt;
    private Instant completedAt;
    private String failureReason;

    @OneToMany(mappedBy = "loadTest", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<RequestDefinition> requests = new ArrayList<>();

    public void addRequest(RequestDefinition request) {
        request.setLoadTest(this);
        requests.add(request);
    }
}
