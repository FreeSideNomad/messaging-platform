package com.acme.reliable.e2e;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.TestInstance;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.*;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.TimeoutException;

/**
 * Base class for E2E tests that provides common infrastructure for testing the full API → Worker
 * → Database flow with worker processing.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Tag("e2e")
public abstract class WorkerE2ETestBase {

    protected static final String API_BASE_URL = "http://localhost:8080";
    protected static final String DB_URL = "jdbc:postgresql://localhost:5432/reliable";
    protected static final String DB_USER = "postgres";
    protected static final String DB_PASSWORD = "postgres";
    protected HttpClient httpClient;
    protected Connection dbConnection;

    @BeforeAll
    void setUp() throws Exception {
        // Setup HTTP client first (needed for waitForService)
        httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

        // Wait for API service
        System.out.println("Waiting for API service to be ready...");
        waitForService(API_BASE_URL + "/commands/health", Duration.ofMinutes(3));
        System.out.println("API service is ready!");

        // Setup DB connection
        System.out.println("Connecting to database...");
        dbConnection = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
        System.out.println("Database connection established!");

        // Clean test data
        cleanupTestData();
    }

    @AfterAll
    void tearDown() throws Exception {
        if (dbConnection != null && !dbConnection.isClosed()) {
            dbConnection.close();
        }
    }

    protected void waitForService(String url, Duration timeout) throws Exception {
        Instant deadline = Instant.now().plus(timeout);
        Exception lastException = null;

        while (Instant.now().isBefore(deadline)) {
            try {
                HttpRequest request =
                        HttpRequest.newBuilder()
                                .uri(URI.create(url))
                                .GET()
                                .timeout(Duration.ofSeconds(5))
                                .build();
                HttpResponse<String> response =
                        httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() == 200) {
                    return;
                }
            } catch (Exception e) {
                lastException = e;
                Thread.sleep(5000);
            }
        }
        throw new TimeoutException(
                "Service not available: "
                        + url
                        + (lastException != null ? " - Last error: " + lastException.getMessage() : ""));
    }

    protected HttpResponse<String> submitCommand(
            String commandName, String idempotencyKey, String payload) throws Exception {
        HttpRequest request =
                HttpRequest.newBuilder()
                        .uri(URI.create(API_BASE_URL + "/commands/" + commandName))
                        .header("Content-Type", "application/json")
                        .header("Idempotency-Key", idempotencyKey)
                        .POST(HttpRequest.BodyPublishers.ofString(payload))
                        .timeout(Duration.ofSeconds(10))
                        .build();

        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }

    protected void cleanupTestData() throws Exception {
        System.out.println("Cleaning up test data...");
        try (Statement stmt = dbConnection.createStatement()) {
            stmt.execute("DELETE FROM platform.command WHERE idempotency_key LIKE 'e2e-test-%'");
            stmt.execute("DELETE FROM platform.outbox WHERE key LIKE 'e2e-test-%'");
            stmt.execute("DELETE FROM platform.inbox WHERE message_id LIKE 'e2e-test-%'");
            stmt.execute("DELETE FROM platform.command_dlq WHERE command_name LIKE 'e2e-test-%'");
        }
        System.out.println("Test data cleaned up!");
    }

    protected void waitForCommandStatus(UUID commandId, String expectedStatus, Duration timeout)
            throws Exception {
        Instant deadline = Instant.now().plus(timeout);
        while (Instant.now().isBefore(deadline)) {
            try (PreparedStatement ps =
                         dbConnection.prepareStatement("SELECT status FROM platform.command WHERE id = ?::uuid")) {
                ps.setString(1, commandId.toString());
                ResultSet rs = ps.executeQuery();
                if (rs.next()) {
                    String status = rs.getString("status");
                    if (expectedStatus.equals(status)) {
                        return;
                    }
                }
            }
            Thread.sleep(500);
        }
        throw new AssertionError(
                "Command " + commandId + " did not reach expected status: " + expectedStatus);
    }

    protected int countCommands(String statusFilter) throws Exception {
        String sql =
                statusFilter == null
                        ? "SELECT COUNT(*) FROM platform.command WHERE idempotency_key LIKE 'e2e-test-%'"
                        : "SELECT COUNT(*) FROM platform.command WHERE idempotency_key LIKE 'e2e-test-%' AND status = ?";

        try (PreparedStatement ps = dbConnection.prepareStatement(sql)) {
            if (statusFilter != null) {
                ps.setString(1, statusFilter);
            }
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                return rs.getInt(1);
            }
        }
        return 0;
    }
}
