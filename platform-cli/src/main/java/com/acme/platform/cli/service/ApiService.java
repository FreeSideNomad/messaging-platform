package com.acme.platform.cli.service;

import com.acme.platform.cli.config.CliConfiguration;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.time.Duration;
import java.util.Map;

public class ApiService {
    private static final Logger logger = LoggerFactory.getLogger(ApiService.class);
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
    private static ApiService instance;
    private final OkHttpClient client;
    private final CliConfiguration config;
    private final ObjectMapper objectMapper;

    private ApiService() {
        this.config = CliConfiguration.getInstance();
        this.client = new OkHttpClient.Builder()
                .connectTimeout(Duration.ofSeconds(config.getApiTimeoutSeconds()))
                .readTimeout(Duration.ofSeconds(config.getApiTimeoutSeconds()))
                .writeTimeout(Duration.ofSeconds(config.getApiTimeoutSeconds()))
                .build();
        this.objectMapper = new ObjectMapper();
        logger.info("API service initialized with base URL: {}", config.getApiBaseUrl());
    }

    public static synchronized ApiService getInstance() {
        if (instance == null) {
            instance = new ApiService();
        }
        return instance;
    }

    public ApiResponse executeCommand(String commandName, File payloadFile, String idempotencyPrefix) throws IOException {
        // Read payload from file
        String payloadJson = Files.readString(payloadFile.toPath());

        // Parse payload to validate JSON
        Map<String, Object> payload = objectMapper.readValue(payloadJson, Map.class);

        // Generate idempotency key
        String idempotencyKey = generateIdempotencyKey(
                idempotencyPrefix != null ? idempotencyPrefix : config.getCliDefaultIdempotencyPrefix()
        );

        logger.info("Executing command: {} with idempotency key: {}", commandName, idempotencyKey);

        // Build request
        String url = config.getApiBaseUrl() + "/api/commands/" + commandName;
        RequestBody body = RequestBody.create(payloadJson, JSON);
        Request request = new Request.Builder()
                .url(url)
                .post(body)
                .addHeader("Content-Type", "application/json")
                .addHeader("X-Idempotency-Key", idempotencyKey)
                .build();

        // Execute request
        try (Response response = client.newCall(request).execute()) {
            String responseBody = response.body() != null ? response.body().string() : "";

            return new ApiResponse(
                    response.code(),
                    response.isSuccessful(),
                    responseBody,
                    idempotencyKey
            );
        }
    }

    public ApiResponse get(String endpoint) throws IOException {
        String url = config.getApiBaseUrl() + endpoint;
        Request request = new Request.Builder()
                .url(url)
                .get()
                .build();

        try (Response response = client.newCall(request).execute()) {
            String responseBody = response.body() != null ? response.body().string() : "";
            return new ApiResponse(
                    response.code(),
                    response.isSuccessful(),
                    responseBody,
                    null
            );
        }
    }

    private String generateIdempotencyKey(String prefix) {
        return String.format("%s-%d", prefix, System.currentTimeMillis());
    }

    public static class ApiResponse {
        private final int statusCode;
        private final boolean successful;
        private final String body;
        private final String idempotencyKey;

        public ApiResponse(int statusCode, boolean successful, String body, String idempotencyKey) {
            this.statusCode = statusCode;
            this.successful = successful;
            this.body = body;
            this.idempotencyKey = idempotencyKey;
        }

        public int getStatusCode() {
            return statusCode;
        }

        public boolean isSuccessful() {
            return successful;
        }

        public String getBody() {
            return body;
        }

        public String getIdempotencyKey() {
            return idempotencyKey;
        }
    }
}
