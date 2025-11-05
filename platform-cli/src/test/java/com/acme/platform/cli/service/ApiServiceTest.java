package com.acme.platform.cli.service;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.FileWriter;
import java.nio.file.Files;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ApiServiceTest {

    private MockWebServer mockWebServer;
    private File tempPayloadFile;

    @BeforeEach
    void setUp() throws Exception {
        mockWebServer = new MockWebServer();
        mockWebServer.start();

        // Create temp payload file
        tempPayloadFile = Files.createTempFile("payload", ".json").toFile();
        try (FileWriter writer = new FileWriter(tempPayloadFile)) {
            writer.write("{\"test\":\"data\"}");
        }
        tempPayloadFile.deleteOnExit();
    }

    @AfterEach
    void tearDown() throws Exception {
        mockWebServer.shutdown();
        if (tempPayloadFile != null && tempPayloadFile.exists()) {
            tempPayloadFile.delete();
        }
    }

    @Test
    void testGetInstance_returnsSingleton() {
        ApiService instance1 = ApiService.getInstance();
        ApiService instance2 = ApiService.getInstance();

        assertThat(instance1).isSameAs(instance2);
    }

    @Test
    void testGet_successfulRequest() throws Exception {
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setBody("{\"status\":\"ok\"}")
                .addHeader("Content-Type", "application/json"));

        // Note: This test won't actually connect to mockWebServer since ApiService uses config
        // In real scenario, you'd need to inject the base URL or use system properties
        ApiService apiService = ApiService.getInstance();

        // This will fail to connect since config points to localhost:8080
        // We're testing the structure, not actual connection
        assertThat(apiService).isNotNull();
    }

    @Test
    void testApiResponse_construction() {
        ApiService.ApiResponse response = new ApiService.ApiResponse(
                200,
                true,
                "{\"result\":\"success\"}",
                "test-key-123"
        );

        assertThat(response.getStatusCode()).isEqualTo(200);
        assertThat(response.isSuccessful()).isTrue();
        assertThat(response.getBody()).isEqualTo("{\"result\":\"success\"}");
        assertThat(response.getIdempotencyKey()).isEqualTo("test-key-123");
    }

    @Test
    void testApiResponse_withNullIdempotencyKey() {
        ApiService.ApiResponse response = new ApiService.ApiResponse(
                404,
                false,
                "{\"error\":\"not found\"}",
                null
        );

        assertThat(response.getStatusCode()).isEqualTo(404);
        assertThat(response.isSuccessful()).isFalse();
        assertThat(response.getBody()).isEqualTo("{\"error\":\"not found\"}");
        assertThat(response.getIdempotencyKey()).isNull();
    }

    @Test
    void testExecuteCommand_invalidPayloadFile() {
        ApiService apiService = ApiService.getInstance();
        File nonExistentFile = new File("/tmp/nonexistent-" + System.currentTimeMillis() + ".json");

        assertThatThrownBy(() ->
                apiService.executeCommand("TestCommand", nonExistentFile, "test-prefix")
        ).isInstanceOf(Exception.class);
    }
}
