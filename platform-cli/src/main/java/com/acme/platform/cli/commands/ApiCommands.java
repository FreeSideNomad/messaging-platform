package com.acme.platform.cli.commands;

import com.acme.platform.cli.service.ApiService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.io.File;
import java.util.Map;

@Command(
        name = "api",
        description = "API command execution operations",
        subcommands = {
                ApiCommands.Execute.class,
                ApiCommands.Get.class
        }
)
public class ApiCommands {

    @Command(name = "exec", description = "Execute a command via API with JSON payload")
    static class Execute implements Runnable {
        @Parameters(index = "0", description = "Command name to execute")
        private String command;

        @Parameters(index = "1", description = "Path to JSON payload file")
        private File payload;

        @Option(names = {"-i", "--idempotency-prefix"}, description = "Custom idempotency key prefix")
        private String idempotencyPrefix;

        @Option(names = {"-f", "--format"}, description = "Output format: text or json (default: text)", defaultValue = "text")
        private String format;

        @Override
        public void run() {
            try {
                if (!payload.exists()) {
                    System.err.println("Payload file not found: " + payload.getAbsolutePath());
                    System.exit(1);
                }

                ApiService apiService = ApiService.getInstance();
                ApiService.ApiResponse response = apiService.executeCommand(command, payload, idempotencyPrefix);

                if ("json".equalsIgnoreCase(format)) {
                    printJson(response);
                } else {
                    printText(response);
                }

                if (!response.isSuccessful()) {
                    System.exit(1);
                }
            } catch (Exception e) {
                System.err.println("Error executing command: " + e.getMessage());
                e.printStackTrace();
                System.exit(1);
            }
        }

        private void printJson(ApiService.ApiResponse response) {
            try {
                ObjectMapper mapper = new ObjectMapper();
                mapper.enable(SerializationFeature.INDENT_OUTPUT);

                Map<String, Object> output = Map.of(
                        "statusCode", response.getStatusCode(),
                        "successful", response.isSuccessful(),
                        "idempotencyKey", response.getIdempotencyKey() != null ? response.getIdempotencyKey() : "",
                        "response", response.getBody()
                );

                System.out.println(mapper.writeValueAsString(output));
            } catch (Exception e) {
                System.err.println("Error formatting response: " + e.getMessage());
            }
        }

        private void printText(ApiService.ApiResponse response) {
            System.out.println("=".repeat(60));
            System.out.println("API Command Execution Result");
            System.out.println("=".repeat(60));
            System.out.println("Status Code: " + response.getStatusCode());
            System.out.println("Successful: " + (response.isSuccessful() ? "Yes" : "No"));
            if (response.getIdempotencyKey() != null) {
                System.out.println("Idempotency Key: " + response.getIdempotencyKey());
            }
            System.out.println("\nResponse:");
            System.out.println("-".repeat(60));

            // Try to pretty print JSON response
            try {
                ObjectMapper mapper = new ObjectMapper();
                mapper.enable(SerializationFeature.INDENT_OUTPUT);
                Object json = mapper.readValue(response.getBody(), Object.class);
                System.out.println(mapper.writeValueAsString(json));
            } catch (Exception e) {
                // If not valid JSON, print as-is
                System.out.println(response.getBody());
            }
            System.out.println("=".repeat(60));
        }
    }

    @Command(name = "get", description = "Make a GET request to an API endpoint")
    static class Get implements Runnable {
        @Parameters(index = "0", description = "API endpoint path (e.g., /api/health)")
        private String endpoint;

        @Option(names = {"-f", "--format"}, description = "Output format: text or json (default: text)", defaultValue = "text")
        private String format;

        @Override
        public void run() {
            try {
                ApiService apiService = ApiService.getInstance();
                ApiService.ApiResponse response = apiService.get(endpoint);

                if ("json".equalsIgnoreCase(format)) {
                    ObjectMapper mapper = new ObjectMapper();
                    mapper.enable(SerializationFeature.INDENT_OUTPUT);

                    Map<String, Object> output = Map.of(
                            "statusCode", response.getStatusCode(),
                            "successful", response.isSuccessful(),
                            "response", response.getBody()
                    );

                    System.out.println(mapper.writeValueAsString(output));
                } else {
                    System.out.println("Status Code: " + response.getStatusCode());
                    System.out.println("Response:");
                    System.out.println(response.getBody());
                }

                if (!response.isSuccessful()) {
                    System.exit(1);
                }
            } catch (Exception e) {
                System.err.println("Error making GET request: " + e.getMessage());
                System.exit(1);
            }
        }
    }
}
