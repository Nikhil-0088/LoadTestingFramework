package com.example.loadtest.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
class LoadTestControllerIntegrationTest {
    @Autowired private MockMvc mockMvc;

    @Test
    void createsLoadTestAndReturnsItsInitialStatus() throws Exception {
        String request = """
                {
                  "name": "Example API test",
                  "durationSeconds": 60,
                  "requestsPerMinute": 120,
                  "requests": [{
                    "name": "health",
                    "method": "GET",
                    "url": "https://example.com/health",
                    "headers": {"Accept": "application/json"},
                    "authentication": {"type": "NONE"}
                  }]
                }
                """;

        MvcResult createResult = mockMvc.perform(post("/api/v1/load-tests")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.loadTestStatus").value("ACCEPTED"))
                .andExpect(jsonPath("$.workRequestStatus").value("ACCEPTED"))
                .andReturn();

        String loadTestId = createResult.getResponse().getContentAsString()
                .replaceAll(".*\\\"loadTestId\\\":\\\"([^\\\"]+)\\\".*", "$1");

        mockMvc.perform(get("/api/v1/load-tests/{loadTestId}", loadTestId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.loadTestStatus").value("ACCEPTED"))
                .andExpect(jsonPath("$.workRequestStatus").value("ACCEPTED"))
                .andExpect(jsonPath("$.currentStep").value("VALIDATE"));
    }

    @Test
    void returnsFieldErrorsForInvalidRequest() throws Exception {
        mockMvc.perform(post("/api/v1/load-tests")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\",\"durationSeconds\":0,\"requestsPerMinute\":0,\"requests\":[]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors.name").exists())
                .andExpect(jsonPath("$.fieldErrors.durationSeconds").exists())
                .andExpect(jsonPath("$.fieldErrors.requestsPerMinute").exists());
    }
}
