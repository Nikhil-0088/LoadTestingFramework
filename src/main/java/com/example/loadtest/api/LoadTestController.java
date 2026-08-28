package com.example.loadtest.api;

import com.example.loadtest.api.dto.CreateLoadTestRequest;
import com.example.loadtest.api.dto.CreateLoadTestResponse;
import com.example.loadtest.api.dto.LoadTestStatusResponse;
import com.example.loadtest.service.LoadTestService;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/load-tests")
@RequiredArgsConstructor
public class LoadTestController {
    private final LoadTestService loadTestService;

    @PostMapping
    public ResponseEntity<CreateLoadTestResponse> create(@Valid @RequestBody CreateLoadTestRequest request) {
        CreateLoadTestResponse response = loadTestService.create(request);
        return ResponseEntity.accepted()
                .location(URI.create("/api/v1/load-tests/" + response.loadTestId()))
                .body(response);
    }

    @GetMapping("/{loadTestId}")
    public LoadTestStatusResponse getStatus(@PathVariable UUID loadTestId) {
        return loadTestService.getStatus(loadTestId);
    }
}
