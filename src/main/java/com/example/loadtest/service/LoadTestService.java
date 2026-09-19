package com.example.loadtest.service;

import com.example.loadtest.api.dto.AuthenticationRequest;
import com.example.loadtest.api.dto.CreateLoadTestRequest;
import com.example.loadtest.api.dto.CreateLoadTestResponse;
import com.example.loadtest.api.dto.LoadTestStatusResponse;
import com.example.loadtest.api.dto.RequestDefinitionRequest;
import com.example.loadtest.config.WorkerCapacityProperties;
import com.example.loadtest.model.AuthenticationType;
import com.example.loadtest.model.LoadTest;
import com.example.loadtest.model.LoadTestStatus;
import com.example.loadtest.model.LoadTestResult;
import com.example.loadtest.model.RequestDefinition;
import com.example.loadtest.model.WorkRequest;
import com.example.loadtest.model.WorkRequestStatus;
import com.example.loadtest.model.WorkflowStep;
import com.example.loadtest.repository.LoadTestRepository;
import com.example.loadtest.repository.LoadTestResultRepository;
import com.example.loadtest.repository.WorkRequestRepository;
import java.net.URI;
import java.time.Instant;
import java.util.NoSuchElementException;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class LoadTestService {
    private final LoadTestRepository loadTestRepository;
    private final LoadTestResultRepository loadTestResultRepository;
    private final WorkRequestRepository workRequestRepository;
    private final WorkerCapacityProperties workerCapacityProperties;

    @Transactional
    public CreateLoadTestResponse create(CreateLoadTestRequest request) {
        validateTargetUrls(request);
        Instant now = Instant.now();

        LoadTest loadTest = new LoadTest();
        loadTest.setId(UUID.randomUUID());
        loadTest.setName(request.name());
        loadTest.setStatus(LoadTestStatus.ACCEPTED);
        loadTest.setDurationSeconds(request.durationSeconds());
        loadTest.setRequestsPerMinute(request.requestsPerMinute());
        loadTest.setWorkerCount(calculateWorkerCount(request.requestsPerMinute()));
        loadTest.setCreatedAt(now);

        for (RequestDefinitionRequest requestDefinition : request.requests()) {
            loadTest.addRequest(toEntity(requestDefinition));
        }
        loadTestRepository.save(loadTest);

        WorkRequest workRequest = new WorkRequest();
        workRequest.setId(UUID.randomUUID());
        workRequest.setLoadTest(loadTest);
        workRequest.setStatus(WorkRequestStatus.ACCEPTED);
        workRequest.setCurrentStep(WorkflowStep.VALIDATE);
        workRequest.setAttemptCount(0);
        workRequest.setNextAttemptAt(now);
        workRequest.setCreatedAt(now);
        workRequest.setUpdatedAt(now);
        workRequestRepository.save(workRequest);

        return new CreateLoadTestResponse(loadTest.getId(), workRequest.getId(), loadTest.getStatus(), workRequest.getStatus());
    }

    @Transactional(readOnly = true)
    public LoadTestStatusResponse getStatus(UUID loadTestId) {
        LoadTest loadTest = loadTestRepository.findById(loadTestId)
                .orElseThrow(() -> new NoSuchElementException("Load test not found: " + loadTestId));
        WorkRequest workRequest = workRequestRepository.findByLoadTestId(loadTestId)
                .orElseThrow(() -> new IllegalStateException("Work request missing for load test: " + loadTestId));
        LoadTestResult result = loadTestResultRepository.findById(loadTestId).orElse(null);
        return new LoadTestStatusResponse(
                loadTest.getId(), loadTest.getName(), loadTest.getStatus(),
                loadTest.getCreatedAt(), loadTest.getStartedAt(), loadTest.getEndsAt(), loadTest.getCompletedAt(),
                workRequest.getId(), workRequest.getStatus(), workRequest.getCurrentStep(), loadTest.getFailureReason(),
                result == null ? null : result.getTotalRequests(),
                result == null ? null : result.getSuccessfulRequests(),
                result == null ? null : result.getFailedRequests(),
                result == null ? null : Map.copyOf(result.getStatusCodeCounts()));
    }

    private RequestDefinition toEntity(RequestDefinitionRequest source) {
        RequestDefinition target = new RequestDefinition();
        target.setId(UUID.randomUUID());
        target.setName(source.name());
        target.setHttpMethod(source.method().name());
        target.setUrl(source.url());
        target.setHeaders(source.headers());
        target.setBody(source.body());
        AuthenticationRequest authentication = source.authentication();
        target.setAuthType(authentication == null ? AuthenticationType.NONE : authentication.type());
        if (authentication != null) {
            validateAuthentication(authentication);
            target.setAuthSecretReference(authentication.secretReference());
            target.setApiKeyHeaderName(authentication.apiKeyHeaderName());
            target.setApiKeyLocation(authentication.apiKeyLocation());
        }
        return target;
    }

    private void validateAuthentication(AuthenticationRequest authentication) {
        if (authentication.type() == AuthenticationType.NONE) return;
        if (authentication.secretReference() == null || authentication.secretReference().isBlank()) {
            throw new IllegalArgumentException("Authentication type " + authentication.type() + " requires secretReference");
        }
        if (authentication.type() == AuthenticationType.API_KEY
                && (authentication.apiKeyHeaderName() == null || authentication.apiKeyHeaderName().isBlank())) {
            throw new IllegalArgumentException("API_KEY authentication requires apiKeyHeaderName");
        }
    }

    private void validateTargetUrls(CreateLoadTestRequest request) {
        for (RequestDefinitionRequest definition : request.requests()) {
            URI uri;
            try {
                uri = URI.create(definition.url());
            } catch (IllegalArgumentException exception) {
                throw new IllegalArgumentException("Invalid request URL: " + definition.url());
            }
            if (!uri.isAbsolute() || !("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))) {
                throw new IllegalArgumentException("Request URL must be an absolute HTTP or HTTPS URL: " + definition.url());
            }
        }
    }

    private int calculateWorkerCount(int requestsPerMinute) {
        int perWorkerCapacity = workerCapacityProperties.getMaxRequestsPerMinute();
        int requiredWorkers = (int) ((requestsPerMinute + (long) perWorkerCapacity - 1) / perWorkerCapacity);
        if (requiredWorkers > workerCapacityProperties.getMaxWorkersPerTest()) {
            throw new IllegalArgumentException(
                    "Requested RPM needs " + requiredWorkers + " workers, exceeding the configured maximum of "
                            + workerCapacityProperties.getMaxWorkersPerTest());
        }
        return requiredWorkers;
    }
}
