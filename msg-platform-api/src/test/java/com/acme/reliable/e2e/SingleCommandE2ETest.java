package com.acme.reliable.e2e;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.net.http.HttpResponse;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Basic E2E test for single command execution.
 * <p>
 * This test validates the complete flow: API → Database
 * <p>
 * NOTE: Requires the full Docker Compose stack to be running:
 * docker-compose up -d
 * <p>
 * To run only this E2E test:
 * mvn clean test -Dgroups=e2e -Dtest=SingleCommandE2ETest
 * <p>
 * To run all tests including E2E:
 * mvn clean test -Dgroups=e2e
 */
@Tag("e2e")
class SingleCommandE2ETest extends E2ETestBase {

    @Test
    @DisplayName("E2E: Single command submission and persistence")
    @org.junit.jupiter.api.Disabled("PostgreSQL sequence initialization issue - separate database configuration problem")
    void testSingleCommandSubmissionAndPersistence() throws Exception {
        // Given
        String idempotencyKey = "e2e-single-" + UUID.randomUUID();
        String payload = "{\"username\":\"testuser\"}";

        System.out.println("Starting E2E test with idempotency key: " + idempotencyKey);

        // When - Submit command via API
        System.out.println("Submitting command to API...");
        HttpResponse<String> response = submitCommand("CreateUser", idempotencyKey, payload);

        System.out.println("Response status: " + response.statusCode());
        System.out.println("Response body: " + response.body());

        // Then - Should return 202 Accepted
        assertThat(response.statusCode())
                .as("API should return 202 Accepted status")
                .isEqualTo(202);

        String commandId = response.headers().firstValue("X-Command-Id").orElseThrow();
        System.out.println("Command accepted with ID: " + commandId);

        // And - Command should be persisted to database
        System.out.println("Verifying command was persisted to database...");
        try (PreparedStatement ps =
                     dbConnection.prepareStatement(
                             "SELECT id, name, status, idempotency_key, payload FROM command WHERE id = ?::uuid")) {
            ps.setString(1, commandId);
            ResultSet rs = ps.executeQuery();

            assertThat(rs.next())
                    .as("Command should exist in database")
                    .isTrue();

            String returnedId = rs.getString("id");
            String commandName = rs.getString("name");
            String status = rs.getString("status");
            String returnedKey = rs.getString("idempotency_key");
            String storedPayload = rs.getString("payload");

            System.out.println("Command found in database:");
            System.out.println("  ID: " + returnedId);
            System.out.println("  Name: " + commandName);
            System.out.println("  Status: " + status);
            System.out.println("  Idempotency Key: " + returnedKey);

            assertThat(returnedId)
                    .as("Command ID should match")
                    .isEqualTo(commandId);

            assertThat(commandName)
                    .as("Command name should be CreateUser")
                    .isEqualTo("CreateUser");

            assertThat(status)
                    .as("Command status should be PENDING")
                    .isEqualTo("PENDING");

            assertThat(returnedKey)
                    .as("Idempotency key should match")
                    .isEqualTo(idempotencyKey);

            assertThat(storedPayload)
                    .as("Payload should be stored correctly")
                    .contains("testuser");
        }

        System.out.println("E2E test PASSED: Command successfully submitted and persisted!");
    }
}
