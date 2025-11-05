package com.acme.platform.cli.service;

import com.acme.platform.cli.config.CliConfiguration;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.ExecCreateCmdResponse;
import com.github.dockerjava.api.model.Container;
import com.github.dockerjava.api.model.Statistics;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientImpl;
import com.github.dockerjava.httpclient5.ApacheDockerHttpClient;
import com.github.dockerjava.transport.DockerHttpClient;
import com.github.dockerjava.core.command.ExecStartResultCallback;
import com.github.dockerjava.core.command.LogContainerResultCallback;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class DockerService implements AutoCloseable {
    private static final Logger logger = LoggerFactory.getLogger(DockerService.class);
    private static DockerService instance;
    private final DockerClient dockerClient;
    private final CliConfiguration config;

    private DockerService() {
        this.config = CliConfiguration.getInstance();

        DefaultDockerClientConfig clientConfig = DefaultDockerClientConfig.createDefaultConfigBuilder()
                .withDockerHost(config.getDockerHost())
                .build();

        DockerHttpClient httpClient = new ApacheDockerHttpClient.Builder()
                .dockerHost(clientConfig.getDockerHost())
                .build();

        this.dockerClient = DockerClientImpl.getInstance(clientConfig, httpClient);
        logger.info("Docker service initialized");
    }

    public static synchronized DockerService getInstance() {
        if (instance == null) {
            instance = new DockerService();
        }
        return instance;
    }

    public List<ContainerInfo> listContainers(boolean all) {
        List<ContainerInfo> containers = new ArrayList<>();
        List<Container> dockerContainers = dockerClient.listContainersCmd()
                .withShowAll(all)
                .exec();

        for (Container container : dockerContainers) {
            containers.add(new ContainerInfo(
                    container.getId().substring(0, 12), // Short ID
                    container.getNames()[0].substring(1), // Remove leading '/'
                    container.getImage(),
                    container.getState(),
                    container.getStatus()
            ));
        }

        return containers;
    }

    public String executeCommand(String containerNameOrId, String command) throws Exception {
        // Find container by name or ID
        String containerId = findContainerId(containerNameOrId);
        if (containerId == null) {
            throw new IllegalArgumentException("Container not found: " + containerNameOrId);
        }

        // Create exec instance
        ExecCreateCmdResponse execCreateCmdResponse = dockerClient.execCreateCmd(containerId)
                .withAttachStdout(true)
                .withAttachStderr(true)
                .withCmd("/bin/sh", "-c", command)
                .exec();

        // Execute and capture output
        ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        ByteArrayOutputStream stderr = new ByteArrayOutputStream();

        ExecStartResultCallback callback = new ExecStartResultCallback(stdout, stderr);

        dockerClient.execStartCmd(execCreateCmdResponse.getId())
                .exec(callback)
                .awaitCompletion(30, TimeUnit.SECONDS);

        String output = stdout.toString();
        String errors = stderr.toString();

        return output + (errors.isEmpty() ? "" : "\nErrors:\n" + errors);
    }

    public String getContainerLogs(String containerNameOrId, Integer tail) throws Exception {
        String containerId = findContainerId(containerNameOrId);
        if (containerId == null) {
            throw new IllegalArgumentException("Container not found: " + containerNameOrId);
        }

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

        LogContainerResultCallback callback = new LogContainerResultCallback() {
            @Override
            public void onNext(com.github.dockerjava.api.model.Frame frame) {
                try {
                    outputStream.write(frame.getPayload());
                } catch (Exception e) {
                    logger.error("Error writing log frame", e);
                }
            }
        };

        dockerClient.logContainerCmd(containerId)
                .withStdOut(true)
                .withStdErr(true)
                .withTail(tail != null ? tail : 100)
                .exec(callback)
                .awaitCompletion(10, TimeUnit.SECONDS);

        return outputStream.toString();
    }

    public ContainerStats getContainerStats(String containerNameOrId) throws Exception {
        String containerId = findContainerId(containerNameOrId);
        if (containerId == null) {
            throw new IllegalArgumentException("Container not found: " + containerNameOrId);
        }

        final Statistics[] statsHolder = new Statistics[1];

        try {
            dockerClient.statsCmd(containerId)
                    .withNoStream(true)
                    .exec(new com.github.dockerjava.core.InvocationBuilder.AsyncResultCallback<Statistics>() {
                        @Override
                        public void onNext(Statistics object) {
                            statsHolder[0] = object;
                        }
                    })
                    .awaitCompletion(10, TimeUnit.SECONDS);
        } catch (Exception e) {
            logger.error("Error getting stats for container: {}", containerNameOrId, e);
            throw new RuntimeException("Failed to get container stats: " + e.getMessage());
        }

        Statistics stats = statsHolder[0];

        if (stats == null) {
            throw new RuntimeException("Failed to get container stats - no data returned");
        }

        // Calculate CPU percentage
        double cpuPercent = 0.0;
        try {
            if (stats.getCpuStats() != null && stats.getPreCpuStats() != null
                    && stats.getCpuStats().getCpuUsage() != null
                    && stats.getPreCpuStats().getCpuUsage() != null) {
                Long cpuDelta = stats.getCpuStats().getCpuUsage().getTotalUsage() -
                        stats.getPreCpuStats().getCpuUsage().getTotalUsage();
                Long systemDelta = stats.getCpuStats().getSystemCpuUsage() -
                        stats.getPreCpuStats().getSystemCpuUsage();
                Integer cpuCount = stats.getCpuStats().getCpuUsage().getPercpuUsage() != null ?
                        stats.getCpuStats().getCpuUsage().getPercpuUsage().size() : 1;

                if (systemDelta > 0 && cpuDelta > 0) {
                    cpuPercent = ((double) cpuDelta / systemDelta) * cpuCount * 100.0;
                }
            }
        } catch (Exception e) {
            logger.warn("Error calculating CPU percentage: {}", e.getMessage());
        }

        // Memory usage
        long memoryUsage = 0;
        long memoryLimit = 0;
        try {
            memoryUsage = stats.getMemoryStats() != null && stats.getMemoryStats().getUsage() != null ?
                    stats.getMemoryStats().getUsage() : 0;
            memoryLimit = stats.getMemoryStats() != null && stats.getMemoryStats().getLimit() != null ?
                    stats.getMemoryStats().getLimit() : 0;
        } catch (Exception e) {
            logger.warn("Error getting memory stats: {}", e.getMessage());
        }

        double memoryPercent = memoryLimit > 0 ? ((double) memoryUsage / memoryLimit) * 100.0 : 0;

        return new ContainerStats(containerNameOrId, cpuPercent, memoryUsage, memoryLimit, memoryPercent);
    }

    private String findContainerId(String nameOrId) {
        List<Container> containers = dockerClient.listContainersCmd()
                .withShowAll(true)
                .exec();

        for (Container container : containers) {
            // Check ID (short or long)
            if (container.getId().startsWith(nameOrId)) {
                return container.getId();
            }
            // Check names
            for (String name : container.getNames()) {
                if (name.substring(1).equals(nameOrId)) { // Remove leading '/'
                    return container.getId();
                }
            }
        }
        return null;
    }

    public void testConnection() {
        dockerClient.pingCmd().exec();
        logger.info("Docker connection test successful");
    }

    @Override
    public void close() {
        if (dockerClient != null) {
            try {
                dockerClient.close();
                logger.info("Docker client closed");
            } catch (Exception e) {
                logger.error("Error closing Docker client", e);
            }
        }
    }

    public static class ContainerInfo {
        private final String id;
        private final String name;
        private final String image;
        private final String state;
        private final String status;

        public ContainerInfo(String id, String name, String image, String state, String status) {
            this.id = id;
            this.name = name;
            this.image = image;
            this.state = state;
            this.status = status;
        }

        public String getId() {
            return id;
        }

        public String getName() {
            return name;
        }

        public String getImage() {
            return image;
        }

        public String getState() {
            return state;
        }

        public String getStatus() {
            return status;
        }
    }

    public static class ContainerStats {
        private final String container;
        private final double cpuPercent;
        private final long memoryUsage;
        private final long memoryLimit;
        private final double memoryPercent;

        public ContainerStats(String container, double cpuPercent, long memoryUsage, long memoryLimit, double memoryPercent) {
            this.container = container;
            this.cpuPercent = cpuPercent;
            this.memoryUsage = memoryUsage;
            this.memoryLimit = memoryLimit;
            this.memoryPercent = memoryPercent;
        }

        public String getContainer() {
            return container;
        }

        public double getCpuPercent() {
            return cpuPercent;
        }

        public long getMemoryUsage() {
            return memoryUsage;
        }

        public long getMemoryLimit() {
            return memoryLimit;
        }

        public double getMemoryPercent() {
            return memoryPercent;
        }
    }
}
