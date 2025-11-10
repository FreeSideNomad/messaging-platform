package com.acme.reliable.processor.services;

import com.acme.reliable.repository.DlqRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

@DisplayName("DlqServiceImpl Tests")
class DlqServiceImplTest {

    private DlqServiceImpl dlqService;
    private DlqRepository mockRepository;

    @BeforeEach
    void setup() {
        mockRepository = mock(DlqRepository.class);
        dlqService = new DlqServiceImpl(mockRepository);
    }

    @Nested
    @DisplayName("park Tests")
    class ParkTests {

        @Test
        @DisplayName("should park failed command to DLQ with all parameters")
        void testPark_WithAllParameters() {
            UUID commandId = UUID.randomUUID();
            String commandName = "CreateOrder";
            String businessKey = "order-123";
            String payload = "{\"orderId\":\"ORD-001\"}";
            String failedStatus = "FAILED";
            String errorClass = "java.util.concurrent.TimeoutException";
            String errorMessage = "Payment gateway timeout after 3 retries";
            int attempts = 3;
            String parkedBy = "OutboxSweeper";

            dlqService.park(commandId, commandName, businessKey, payload, failedStatus, errorClass, errorMessage, attempts, parkedBy);

            verify(mockRepository).insertDlqEntry(
                    eq(commandId),
                    eq(commandName),
                    eq(businessKey),
                    eq(payload),
                    eq(failedStatus),
                    eq(errorClass),
                    eq(errorMessage),
                    eq(attempts),
                    eq(parkedBy)
            );
        }

        @Test
        @DisplayName("should park command with minimal error information")
        void testPark_WithMinimalError() {
            UUID commandId = UUID.randomUUID();
            String commandName = "DeleteUser";
            String businessKey = "delete-user-456";
            String payload = "{}";
            String failedStatus = "TIMED_OUT";
            String errorClass = "java.lang.Exception";
            String errorMessage = "Unknown error";
            int attempts = 0;
            String parkedBy = "Processor";

            dlqService.park(commandId, commandName, businessKey, payload, failedStatus, errorClass, errorMessage, attempts, parkedBy);

            verify(mockRepository).insertDlqEntry(
                    commandId,
                    commandName,
                    businessKey,
                    payload,
                    failedStatus,
                    errorClass,
                    errorMessage,
                    attempts,
                    parkedBy
            );
        }

        @Test
        @DisplayName("should park command with large payloads")
        void testPark_WithLargePayload() {
            UUID commandId = UUID.randomUUID();
            String commandName = "UploadDocument";
            String businessKey = "doc-789";
            String largePayload = "{\"document\":\"" + "x".repeat(10000) + "\"}";
            String failedStatus = "FAILED";
            String errorClass = "java.io.IOException";
            String errorMessage = "Processing failed";
            int attempts = 1;
            String parkedBy = "DocumentProcessor";

            dlqService.park(commandId, commandName, businessKey, largePayload, failedStatus, errorClass, errorMessage, attempts, parkedBy);

            verify(mockRepository).insertDlqEntry(
                    eq(commandId),
                    eq(commandName),
                    eq(businessKey),
                    eq(largePayload),
                    eq(failedStatus),
                    eq(errorClass),
                    eq(errorMessage),
                    eq(attempts),
                    eq(parkedBy)
            );
        }

        @Test
        @DisplayName("should park command with multiple retry attempts")
        void testPark_WithMultipleRetries() {
            UUID commandId = UUID.randomUUID();
            String commandName = "ProcessPayment";
            String businessKey = "payment-123";
            String payload = "{\"amount\":99.99}";
            String failedStatus = "FAILED";
            String errorClass = "java.net.ConnectException";
            String errorMessage = "Service unavailable after 5 retries";
            int attempts = 5;
            String parkedBy = "PaymentProcessor";

            dlqService.park(commandId, commandName, businessKey, payload, failedStatus, errorClass, errorMessage, attempts, parkedBy);

            verify(mockRepository).insertDlqEntry(
                    eq(commandId),
                    eq(commandName),
                    eq(businessKey),
                    eq(payload),
                    eq(failedStatus),
                    eq(errorClass),
                    eq(errorMessage),
                    eq(5),
                    eq(parkedBy)
            );
        }
    }
}
