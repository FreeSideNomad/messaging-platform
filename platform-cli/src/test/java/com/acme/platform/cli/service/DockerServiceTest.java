package com.acme.platform.cli.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DockerServiceTest {

    @Test
    void testGetInstance_returnsSingleton() {
        DockerService instance1 = DockerService.getInstance();
        DockerService instance2 = DockerService.getInstance();

        assertThat(instance1).isSameAs(instance2);
    }

    @Test
    void testContainerInfo_construction() {
        DockerService.ContainerInfo containerInfo = new DockerService.ContainerInfo(
                "abc123def456",
                "my-container",
                "nginx:latest",
                "running",
                "Up 2 hours"
        );

        assertThat(containerInfo.getId()).isEqualTo("abc123def456");
        assertThat(containerInfo.getName()).isEqualTo("my-container");
        assertThat(containerInfo.getImage()).isEqualTo("nginx:latest");
        assertThat(containerInfo.getState()).isEqualTo("running");
        assertThat(containerInfo.getStatus()).isEqualTo("Up 2 hours");
    }

    @Test
    void testContainerInfo_withStoppedState() {
        DockerService.ContainerInfo containerInfo = new DockerService.ContainerInfo(
                "xyz789",
                "stopped-container",
                "postgres:15",
                "exited",
                "Exited (0) 10 minutes ago"
        );

        assertThat(containerInfo.getState()).isEqualTo("exited");
        assertThat(containerInfo.getStatus()).contains("Exited");
    }

    @Test
    void testContainerStats_construction() {
        DockerService.ContainerStats stats = new DockerService.ContainerStats(
                "my-container",
                15.5,
                524288000L,
                2147483648L,
                24.4
        );

        assertThat(stats.getContainer()).isEqualTo("my-container");
        assertThat(stats.getCpuPercent()).isEqualTo(15.5);
        assertThat(stats.getMemoryUsage()).isEqualTo(524288000L);
        assertThat(stats.getMemoryLimit()).isEqualTo(2147483648L);
        assertThat(stats.getMemoryPercent()).isEqualTo(24.4);
    }

    @Test
    void testContainerStats_withZeroCpu() {
        DockerService.ContainerStats stats = new DockerService.ContainerStats(
                "idle-container",
                0.0,
                100000L,
                1000000L,
                10.0
        );

        assertThat(stats.getCpuPercent()).isEqualTo(0.0);
    }

    @Test
    void testContainerStats_withHighCpu() {
        DockerService.ContainerStats stats = new DockerService.ContainerStats(
                "busy-container",
                95.8,
                1500000000L,
                2000000000L,
                75.0
        );

        assertThat(stats.getCpuPercent()).isGreaterThan(90.0);
        assertThat(stats.getMemoryPercent()).isGreaterThan(70.0);
    }

    @Test
    void testContainerStats_memoryCalculation() {
        long memUsage = 1073741824L; // 1GB
        long memLimit = 2147483648L; // 2GB
        double expectedPercent = 50.0;

        DockerService.ContainerStats stats = new DockerService.ContainerStats(
                "test-container",
                10.0,
                memUsage,
                memLimit,
                expectedPercent
        );

        assertThat(stats.getMemoryPercent()).isEqualTo(expectedPercent);
    }

    @Test
    void testContainerStats_withZeroMemory() {
        DockerService.ContainerStats stats = new DockerService.ContainerStats(
                "empty-container",
                0.0,
                0L,
                1000000L,
                0.0
        );

        assertThat(stats.getMemoryUsage()).isEqualTo(0L);
        assertThat(stats.getMemoryPercent()).isEqualTo(0.0);
    }

    @Test
    void testContainerInfo_allFields() {
        DockerService.ContainerInfo info = new DockerService.ContainerInfo(
                "1234567890ab",
                "test-postgres",
                "postgres:15-alpine",
                "running",
                "Up 3 days"
        );

        // Verify all fields are accessible
        assertThat(info.getId()).isNotNull();
        assertThat(info.getName()).isNotNull();
        assertThat(info.getImage()).isNotNull();
        assertThat(info.getState()).isNotNull();
        assertThat(info.getStatus()).isNotNull();
    }

    @Test
    void testContainerStats_allFields() {
        DockerService.ContainerStats stats = new DockerService.ContainerStats(
                "container-1",
                25.5,
                536870912L,
                1073741824L,
                50.0
        );

        // Verify all fields are accessible
        assertThat(stats.getContainer()).isNotNull();
        assertThat(stats.getCpuPercent()).isNotNegative();
        assertThat(stats.getMemoryUsage()).isNotNegative();
        assertThat(stats.getMemoryLimit()).isNotNegative();
        assertThat(stats.getMemoryPercent()).isNotNegative();
    }
}
