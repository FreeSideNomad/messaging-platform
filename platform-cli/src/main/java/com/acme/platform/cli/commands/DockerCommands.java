package com.acme.platform.cli.commands;

import com.acme.platform.cli.service.DockerService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Command(
        name = "docker",
        description = "Docker container management operations",
        subcommands = {
                DockerCommands.ListContainers.class,
                DockerCommands.ExecCommand.class,
                DockerCommands.GetLogs.class,
                DockerCommands.GetStats.class
        }
)
public class DockerCommands {

    @Command(name = "list", description = "List Docker containers")
    static class ListContainers implements Runnable {
        @Option(names = {"-a", "--all"}, description = "Show all containers (default shows just running)")
        private boolean all = false;

        @Option(names = {"-f", "--format"}, description = "Output format: table or json (default: table)", defaultValue = "table")
        private String format;

        @Override
        public void run() {
            try (DockerService dockerService = DockerService.getInstance()) {
                List<DockerService.ContainerInfo> containers = dockerService.listContainers(all);

                if ("json".equalsIgnoreCase(format)) {
                    printJson(containers);
                } else {
                    printTable(containers);
                }
            } catch (Exception e) {
                System.err.println("Error listing containers: " + e.getMessage());
                System.exit(1);
            }
        }

        private void printJson(List<DockerService.ContainerInfo> containers) {
            try {
                ObjectMapper mapper = new ObjectMapper();
                mapper.enable(SerializationFeature.INDENT_OUTPUT);

                List<Map<String, Object>> containerList = containers.stream()
                        .map(c -> {
                            Map<String, Object> containerMap = new java.util.HashMap<>();
                            containerMap.put("id", c.getId());
                            containerMap.put("name", c.getName());
                            containerMap.put("image", c.getImage());
                            containerMap.put("state", c.getState());
                            containerMap.put("status", c.getStatus());
                            return containerMap;
                        })
                        .collect(Collectors.toList());

                Map<String, Object> result = new java.util.HashMap<>();
                result.put("containers", containerList);
                System.out.println(mapper.writeValueAsString(result));
            } catch (Exception e) {
                System.err.println("Error formatting JSON: " + e.getMessage());
            }
        }

        private void printTable(List<DockerService.ContainerInfo> containers) {
            if (containers.isEmpty()) {
                System.out.println("No containers found.");
                return;
            }

            System.out.println("Docker Containers");
            System.out.println("=".repeat(120));
            System.out.printf("%-15s %-25s %-30s %-12s %-30s%n",
                    "Container ID", "Name", "Image", "State", "Status");
            System.out.println("-".repeat(120));

            for (DockerService.ContainerInfo container : containers) {
                String name = container.getName();
                if (name.length() > 25) name = name.substring(0, 22) + "...";

                String image = container.getImage();
                if (image.length() > 30) image = image.substring(0, 27) + "...";

                String status = container.getStatus();
                if (status.length() > 30) status = status.substring(0, 27) + "...";

                System.out.printf("%-15s %-25s %-30s %-12s %-30s%n",
                        container.getId(),
                        name,
                        image,
                        container.getState(),
                        status);
            }

            System.out.println("=".repeat(120));
            System.out.println("Total containers: " + containers.size());
        }
    }

    @Command(name = "exec", description = "Execute a command in a Docker container")
    static class ExecCommand implements Runnable {
        @Parameters(index = "0", description = "Container name or ID")
        private String container;

        @Parameters(index = "1", description = "Command to execute")
        private String command;

        @Option(names = {"-f", "--format"}, description = "Output format: text or json (default: text)", defaultValue = "text")
        private String format;

        @Override
        public void run() {
            try (DockerService dockerService = DockerService.getInstance()) {
                String output = dockerService.executeCommand(container, command);

                if ("json".equalsIgnoreCase(format)) {
                    ObjectMapper mapper = new ObjectMapper();
                    mapper.enable(SerializationFeature.INDENT_OUTPUT);
                    System.out.println(mapper.writeValueAsString(Map.of(
                            "container", container,
                            "command", command,
                            "output", output
                    )));
                } else {
                    System.out.println("Container: " + container);
                    System.out.println("Command: " + command);
                    System.out.println("=".repeat(60));
                    System.out.println(output);
                }
            } catch (Exception e) {
                System.err.println("Error executing command: " + e.getMessage());
                System.exit(1);
            }
        }
    }

    @Command(name = "logs", description = "Get logs from a Docker container")
    static class GetLogs implements Runnable {
        @Parameters(index = "0", description = "Container name or ID")
        private String container;

        @Option(names = {"-n", "--tail"}, description = "Number of lines to show from the end of the logs (default: 100)", defaultValue = "100")
        private Integer tail;

        @Option(names = {"-f", "--format"}, description = "Output format: text or json (default: text)", defaultValue = "text")
        private String format;

        @Override
        public void run() {
            try (DockerService dockerService = DockerService.getInstance()) {
                String logs = dockerService.getContainerLogs(container, tail);

                if ("json".equalsIgnoreCase(format)) {
                    ObjectMapper mapper = new ObjectMapper();
                    mapper.enable(SerializationFeature.INDENT_OUTPUT);
                    System.out.println(mapper.writeValueAsString(Map.of(
                            "container", container,
                            "logs", logs
                    )));
                } else {
                    System.out.println("Container: " + container);
                    System.out.println("=".repeat(60));
                    System.out.println(logs);
                }
            } catch (Exception e) {
                System.err.println("Error getting logs: " + e.getMessage());
                System.exit(1);
            }
        }
    }

    @Command(name = "stats", description = "Display resource usage statistics for a container")
    static class GetStats implements Runnable {
        @Parameters(index = "0", description = "Container name or ID")
        private String container;

        @Option(names = {"-f", "--format"}, description = "Output format: table or json (default: table)", defaultValue = "table")
        private String format;

        @Override
        public void run() {
            try (DockerService dockerService = DockerService.getInstance()) {
                DockerService.ContainerStats stats = dockerService.getContainerStats(container);

                if ("json".equalsIgnoreCase(format)) {
                    ObjectMapper mapper = new ObjectMapper();
                    mapper.enable(SerializationFeature.INDENT_OUTPUT);

                    Map<String, Object> statsMap = Map.of(
                            "container", stats.getContainer(),
                            "cpuPercent", String.format("%.2f", stats.getCpuPercent()),
                            "memoryUsage", formatBytes(stats.getMemoryUsage()),
                            "memoryLimit", formatBytes(stats.getMemoryLimit()),
                            "memoryPercent", String.format("%.2f", stats.getMemoryPercent())
                    );

                    System.out.println(mapper.writeValueAsString(statsMap));
                } else {
                    System.out.println("Container Stats: " + stats.getContainer());
                    System.out.println("=".repeat(60));
                    System.out.printf("CPU Usage:     %.2f%%%n", stats.getCpuPercent());
                    System.out.printf("Memory Usage:  %s / %s (%.2f%%)%n",
                            formatBytes(stats.getMemoryUsage()),
                            formatBytes(stats.getMemoryLimit()),
                            stats.getMemoryPercent());
                    System.out.println("=".repeat(60));
                }
            } catch (Exception e) {
                System.err.println("Error getting container stats: " + e.getMessage());
                System.exit(1);
            }
        }

        private String formatBytes(long bytes) {
            if (bytes < 1024) return bytes + " B";
            int exp = (int) (Math.log(bytes) / Math.log(1024));
            char pre = "KMGTPE".charAt(exp - 1);
            return String.format("%.2f %siB", bytes / Math.pow(1024, exp), pre);
        }
    }
}
