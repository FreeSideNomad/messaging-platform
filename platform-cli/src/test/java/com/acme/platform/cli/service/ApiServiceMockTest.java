package com.acme.platform.cli.service;

import okhttp3.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ApiServiceMockTest {

    @TempDir
    Path tempDir;
    @Mock
    private OkHttpClient client;
    @Mock
    private Call call;
    @Mock
    private Response response;
    @Mock
    private ResponseBody responseBody;
    private ApiService apiService;

    @BeforeEach
    void setUp() throws Exception {
        apiService = ApiService.getInstance();

        // Use reflection to inject mock client
        Field clientField = ApiService.class.getDeclaredField("client");
        clientField.setAccessible(true);
        clientField.set(apiService, client);
    }

    @Test
    void testExecuteCommand_successful() throws Exception {
        // Arrange
        File payloadFile = createPayloadFile("{\"orderId\": \"123\", \"amount\": 100.50}");
        String commandName = "createOrder";

        when(response.code()).thenReturn(200);
        when(response.isSuccessful()).thenReturn(true);
        when(response.body()).thenReturn(responseBody);
        when(responseBody.string()).thenReturn("{\"status\": \"success\", \"orderId\": \"123\"}");

        when(call.execute()).thenReturn(response);
        when(client.newCall(any(Request.class))).thenReturn(call);

        // Act
        ApiService.ApiResponse result = apiService.executeCommand(commandName, payloadFile, "test");

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.getStatusCode()).isEqualTo(200);
        assertThat(result.isSuccessful()).isTrue();
        assertThat(result.getBody()).contains("success");
        assertThat(result.getIdempotencyKey()).isNotNull();
        assertThat(result.getIdempotencyKey()).startsWith("test-");

        verify(client).newCall(any(Request.class));
        verify(call).execute();
    }

    @Test
    void testExecuteCommand_withDefaultIdempotencyPrefix() throws Exception {
        // Arrange
        File payloadFile = createPayloadFile("{\"orderId\": \"123\"}");

        when(response.code()).thenReturn(200);
        when(response.isSuccessful()).thenReturn(true);
        when(response.body()).thenReturn(responseBody);
        when(responseBody.string()).thenReturn("{}");

        when(call.execute()).thenReturn(response);
        when(client.newCall(any(Request.class))).thenReturn(call);

        // Act
        ApiService.ApiResponse result = apiService.executeCommand("testCommand", payloadFile, null);

        // Assert
        assertThat(result.getIdempotencyKey()).isNotNull();
        assertThat(result.getIdempotencyKey()).startsWith("cli-request-"); // Default prefix from config
    }

    @Test
    void testExecuteCommand_withInvalidJson_throwsException() throws Exception {
        // Arrange
        File payloadFile = createPayloadFile("invalid json {{{");

        // Act & Assert
        assertThatThrownBy(() -> apiService.executeCommand("testCommand", payloadFile, "test"))
                .isInstanceOf(IOException.class);

        verify(client, never()).newCall(any(Request.class));
    }

    @Test
    void testExecuteCommand_withErrorResponse() throws Exception {
        // Arrange
        File payloadFile = createPayloadFile("{\"orderId\": \"123\"}");

        when(response.code()).thenReturn(400);
        when(response.isSuccessful()).thenReturn(false);
        when(response.body()).thenReturn(responseBody);
        when(responseBody.string()).thenReturn("{\"error\": \"Invalid request\"}");

        when(call.execute()).thenReturn(response);
        when(client.newCall(any(Request.class))).thenReturn(call);

        // Act
        ApiService.ApiResponse result = apiService.executeCommand("testCommand", payloadFile, "test");

        // Assert
        assertThat(result.getStatusCode()).isEqualTo(400);
        assertThat(result.isSuccessful()).isFalse();
        assertThat(result.getBody()).contains("Invalid request");
    }

    @Test
    void testExecuteCommand_withServerError() throws Exception {
        // Arrange
        File payloadFile = createPayloadFile("{\"orderId\": \"123\"}");

        when(response.code()).thenReturn(500);
        when(response.isSuccessful()).thenReturn(false);
        when(response.body()).thenReturn(responseBody);
        when(responseBody.string()).thenReturn("{\"error\": \"Internal server error\"}");

        when(call.execute()).thenReturn(response);
        when(client.newCall(any(Request.class))).thenReturn(call);

        // Act
        ApiService.ApiResponse result = apiService.executeCommand("testCommand", payloadFile, "test");

        // Assert
        assertThat(result.getStatusCode()).isEqualTo(500);
        assertThat(result.isSuccessful()).isFalse();
    }

    @Test
    void testExecuteCommand_withEmptyResponseBody() throws Exception {
        // Arrange
        File payloadFile = createPayloadFile("{\"orderId\": \"123\"}");

        when(response.code()).thenReturn(204);
        when(response.isSuccessful()).thenReturn(true);
        when(response.body()).thenReturn(null);

        when(call.execute()).thenReturn(response);
        when(client.newCall(any(Request.class))).thenReturn(call);

        // Act
        ApiService.ApiResponse result = apiService.executeCommand("testCommand", payloadFile, "test");

        // Assert
        assertThat(result.getStatusCode()).isEqualTo(204);
        assertThat(result.getBody()).isEmpty();
    }

    @Test
    void testGet_successful() throws Exception {
        // Arrange
        String endpoint = "/api/orders/123";

        when(response.code()).thenReturn(200);
        when(response.isSuccessful()).thenReturn(true);
        when(response.body()).thenReturn(responseBody);
        when(responseBody.string()).thenReturn("{\"orderId\": \"123\", \"status\": \"completed\"}");

        when(call.execute()).thenReturn(response);
        when(client.newCall(any(Request.class))).thenReturn(call);

        // Act
        ApiService.ApiResponse result = apiService.get(endpoint);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.getStatusCode()).isEqualTo(200);
        assertThat(result.isSuccessful()).isTrue();
        assertThat(result.getBody()).contains("orderId");
        assertThat(result.getIdempotencyKey()).isNull(); // GET doesn't have idempotency key

        verify(client).newCall(any(Request.class));
    }

    @Test
    void testGet_withNotFound() throws Exception {
        // Arrange
        String endpoint = "/api/orders/999";

        when(response.code()).thenReturn(404);
        when(response.isSuccessful()).thenReturn(false);
        when(response.body()).thenReturn(responseBody);
        when(responseBody.string()).thenReturn("{\"error\": \"Not found\"}");

        when(call.execute()).thenReturn(response);
        when(client.newCall(any(Request.class))).thenReturn(call);

        // Act
        ApiService.ApiResponse result = apiService.get(endpoint);

        // Assert
        assertThat(result.getStatusCode()).isEqualTo(404);
        assertThat(result.isSuccessful()).isFalse();
        assertThat(result.getBody()).contains("Not found");
    }

    @Test
    void testGet_withEmptyResponseBody() throws Exception {
        // Arrange
        String endpoint = "/api/health";

        when(response.code()).thenReturn(200);
        when(response.isSuccessful()).thenReturn(true);
        when(response.body()).thenReturn(null);

        when(call.execute()).thenReturn(response);
        when(client.newCall(any(Request.class))).thenReturn(call);

        // Act
        ApiService.ApiResponse result = apiService.get(endpoint);

        // Assert
        assertThat(result.getStatusCode()).isEqualTo(200);
        assertThat(result.getBody()).isEmpty();
    }

    @Test
    void testGet_withNetworkError_throwsException() throws Exception {
        // Arrange
        when(call.execute()).thenThrow(new IOException("Network error"));
        when(client.newCall(any(Request.class))).thenReturn(call);

        // Act & Assert
        assertThatThrownBy(() -> apiService.get("/api/test"))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("Network error");
    }

    @Test
    void testExecuteCommand_generatesUniqueIdempotencyKeys() throws Exception {
        // Arrange
        File payloadFile = createPayloadFile("{\"test\": \"data\"}");

        when(response.code()).thenReturn(200);
        when(response.isSuccessful()).thenReturn(true);
        when(response.body()).thenReturn(responseBody);
        when(responseBody.string()).thenReturn("{}");

        when(call.execute()).thenReturn(response);
        when(client.newCall(any(Request.class))).thenReturn(call);

        // Act
        ApiService.ApiResponse result1 = apiService.executeCommand("test", payloadFile, "prefix");
        Thread.sleep(10); // Ensure different timestamps
        ApiService.ApiResponse result2 = apiService.executeCommand("test", payloadFile, "prefix");

        // Assert
        assertThat(result1.getIdempotencyKey()).isNotEqualTo(result2.getIdempotencyKey());
        assertThat(result1.getIdempotencyKey()).startsWith("prefix-");
        assertThat(result2.getIdempotencyKey()).startsWith("prefix-");
    }

    @Test
    void testExecuteCommand_includesIdempotencyKeyInRequest() throws Exception {
        // Arrange
        File payloadFile = createPayloadFile("{\"test\": \"data\"}");

        when(response.code()).thenReturn(200);
        when(response.isSuccessful()).thenReturn(true);
        when(response.body()).thenReturn(responseBody);
        when(responseBody.string()).thenReturn("{}");

        when(call.execute()).thenReturn(response);
        when(client.newCall(any(Request.class))).thenAnswer(invocation -> {
            Request request = invocation.getArgument(0);
            // Verify the request has the idempotency key header
            assertThat(request.header("X-Idempotency-Key")).isNotNull();
            assertThat(request.header("X-Idempotency-Key")).startsWith("test-");
            return call;
        });

        // Act
        apiService.executeCommand("testCommand", payloadFile, "test");

        // Assert
        verify(client).newCall(any(Request.class));
    }

    private File createPayloadFile(String content) throws IOException {
        Path file = tempDir.resolve("payload.json");
        Files.writeString(file, content);
        return file.toFile();
    }
}
