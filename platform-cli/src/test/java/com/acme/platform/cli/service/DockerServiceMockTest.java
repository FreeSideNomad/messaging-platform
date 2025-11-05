package com.acme.platform.cli.service;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.*;
import com.github.dockerjava.api.model.Container;
import com.github.dockerjava.api.model.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DockerServiceMockTest {

    @Mock
    private DockerClient dockerClient;

    @Mock
    private ListContainersCmd listContainersCmd;

    @Mock
    private ExecCreateCmd execCreateCmd;

    @Mock
    private ExecStartCmd execStartCmd;

    @Mock
    private LogContainerCmd logContainerCmd;

    @Mock
    private StatsCmd statsCmd;

    @Mock
    private PingCmd pingCmd;

    private DockerService dockerService;

    @BeforeEach
    void setUp() throws Exception {
        dockerService = DockerService.getInstance();

        // Use reflection to inject mock docker client
        Field dockerClientField = DockerService.class.getDeclaredField("dockerClient");
        dockerClientField.setAccessible(true);
        dockerClientField.set(dockerService, dockerClient);
    }

    @Test
    void testListContainers_returnsRunningContainers() {
        // Arrange
        Container container1 = mock(Container.class);
        when(container1.getId()).thenReturn("abc123def456");
        when(container1.getNames()).thenReturn(new String[]{"/test-container"});
        when(container1.getImage()).thenReturn("postgres:15");
        when(container1.getState()).thenReturn("running");
        when(container1.getStatus()).thenReturn("Up 2 hours");

        Container container2 = mock(Container.class);
        when(container2.getId()).thenReturn("xyz789uvw012");
        when(container2.getNames()).thenReturn(new String[]{"/kafka-broker"});
        when(container2.getImage()).thenReturn("kafka:3.6");
        when(container2.getState()).thenReturn("running");
        when(container2.getStatus()).thenReturn("Up 1 hour");

        when(listContainersCmd.withShowAll(false)).thenReturn(listContainersCmd);
        when(listContainersCmd.exec()).thenReturn(Arrays.asList(container1, container2));
        when(dockerClient.listContainersCmd()).thenReturn(listContainersCmd);

        // Act
        List<DockerService.ContainerInfo> containers = dockerService.listContainers(false);

        // Assert
        assertThat(containers).hasSize(2);

        DockerService.ContainerInfo info1 = containers.get(0);
        assertThat(info1.getId()).isEqualTo("abc123def456");
        assertThat(info1.getName()).isEqualTo("test-container");
        assertThat(info1.getImage()).isEqualTo("postgres:15");
        assertThat(info1.getState()).isEqualTo("running");
        assertThat(info1.getStatus()).isEqualTo("Up 2 hours");

        DockerService.ContainerInfo info2 = containers.get(1);
        assertThat(info2.getId()).isEqualTo("xyz789uvw012");
        assertThat(info2.getName()).isEqualTo("kafka-broker");

        verify(listContainersCmd).withShowAll(false);
        verify(listContainersCmd).exec();
    }

    @Test
    void testListContainers_withAllFlag_returnsAllContainers() {
        // Arrange
        Container container = mock(Container.class);
        when(container.getId()).thenReturn("stopped123456");
        when(container.getNames()).thenReturn(new String[]{"/stopped-container"});
        when(container.getImage()).thenReturn("nginx:latest");
        when(container.getState()).thenReturn("exited");
        when(container.getStatus()).thenReturn("Exited (0) 5 minutes ago");

        when(listContainersCmd.withShowAll(true)).thenReturn(listContainersCmd);
        when(listContainersCmd.exec()).thenReturn(Collections.singletonList(container));
        when(dockerClient.listContainersCmd()).thenReturn(listContainersCmd);

        // Act
        List<DockerService.ContainerInfo> containers = dockerService.listContainers(true);

        // Assert
        assertThat(containers).hasSize(1);
        assertThat(containers.get(0).getState()).isEqualTo("exited");
        verify(listContainersCmd).withShowAll(true);
    }

    @Test
    void testListContainers_withNoContainers_returnsEmptyList() {
        // Arrange
        when(listContainersCmd.withShowAll(anyBoolean())).thenReturn(listContainersCmd);
        when(listContainersCmd.exec()).thenReturn(Collections.emptyList());
        when(dockerClient.listContainersCmd()).thenReturn(listContainersCmd);

        // Act
        List<DockerService.ContainerInfo> containers = dockerService.listContainers(false);

        // Assert
        assertThat(containers).isEmpty();
    }

    @Test
    void testExecuteCommand_successful() throws Exception {
        // Arrange
        String containerName = "test-container";
        String command = "ls -la";

        Container container = mock(Container.class);
        when(container.getId()).thenReturn("abc123");
        when(container.getNames()).thenReturn(new String[]{"/test-container"});

        when(listContainersCmd.withShowAll(true)).thenReturn(listContainersCmd);
        when(listContainersCmd.exec()).thenReturn(Collections.singletonList(container));
        when(dockerClient.listContainersCmd()).thenReturn(listContainersCmd);

        ExecCreateCmdResponse execResponse = mock(ExecCreateCmdResponse.class);
        when(execResponse.getId()).thenReturn("exec123");

        when(execCreateCmd.withAttachStdout(true)).thenReturn(execCreateCmd);
        when(execCreateCmd.withAttachStderr(true)).thenReturn(execCreateCmd);
        when(execCreateCmd.withCmd(anyString(), anyString(), anyString())).thenReturn(execCreateCmd);
        when(execCreateCmd.exec()).thenReturn(execResponse);
        when(dockerClient.execCreateCmd("abc123")).thenReturn(execCreateCmd);

        when(execStartCmd.exec(any())).thenReturn(mock(com.github.dockerjava.core.command.ExecStartResultCallback.class));
        when(dockerClient.execStartCmd("exec123")).thenReturn(execStartCmd);

        // Act
        String result = dockerService.executeCommand(containerName, command);

        // Assert
        assertThat(result).isNotNull();
        verify(dockerClient).execCreateCmd("abc123");
        verify(execCreateCmd).withCmd("/bin/sh", "-c", command);
    }

    @Test
    void testExecuteCommand_containerNotFound_throwsException() {
        // Arrange
        when(listContainersCmd.withShowAll(true)).thenReturn(listContainersCmd);
        when(listContainersCmd.exec()).thenReturn(Collections.emptyList());
        when(dockerClient.listContainersCmd()).thenReturn(listContainersCmd);

        // Act & Assert
        assertThatThrownBy(() -> dockerService.executeCommand("non-existent", "ls"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Container not found");
    }

    @Test
    void testGetContainerLogs_successful() throws Exception {
        // Arrange
        String containerName = "test-container";

        Container container = mock(Container.class);
        when(container.getId()).thenReturn("abc123");
        when(container.getNames()).thenReturn(new String[]{"/test-container"});

        when(listContainersCmd.withShowAll(true)).thenReturn(listContainersCmd);
        when(listContainersCmd.exec()).thenReturn(Collections.singletonList(container));
        when(dockerClient.listContainersCmd()).thenReturn(listContainersCmd);

        when(logContainerCmd.withStdOut(true)).thenReturn(logContainerCmd);
        when(logContainerCmd.withStdErr(true)).thenReturn(logContainerCmd);
        when(logContainerCmd.withTail(anyInt())).thenReturn(logContainerCmd);
        when(logContainerCmd.exec(any())).thenReturn(mock(com.github.dockerjava.core.command.LogContainerResultCallback.class));
        when(dockerClient.logContainerCmd("abc123")).thenReturn(logContainerCmd);

        // Act
        String logs = dockerService.getContainerLogs(containerName, 50);

        // Assert
        assertThat(logs).isNotNull();
        verify(dockerClient).logContainerCmd("abc123");
        verify(logContainerCmd).withTail(50);
    }

    @Test
    void testGetContainerLogs_withNullTail_usesDefault() throws Exception {
        // Arrange
        Container container = mock(Container.class);
        when(container.getId()).thenReturn("abc123");
        when(container.getNames()).thenReturn(new String[]{"/test-container"});

        when(listContainersCmd.withShowAll(true)).thenReturn(listContainersCmd);
        when(listContainersCmd.exec()).thenReturn(Collections.singletonList(container));
        when(dockerClient.listContainersCmd()).thenReturn(listContainersCmd);

        when(logContainerCmd.withStdOut(true)).thenReturn(logContainerCmd);
        when(logContainerCmd.withStdErr(true)).thenReturn(logContainerCmd);
        when(logContainerCmd.withTail(anyInt())).thenReturn(logContainerCmd);
        when(logContainerCmd.exec(any())).thenReturn(mock(com.github.dockerjava.core.command.LogContainerResultCallback.class));
        when(dockerClient.logContainerCmd("abc123")).thenReturn(logContainerCmd);

        // Act
        dockerService.getContainerLogs("test-container", null);

        // Assert
        verify(logContainerCmd).withTail(100); // Default value
    }

    @Test
    void testGetContainerLogs_containerNotFound_throwsException() {
        // Arrange
        when(listContainersCmd.withShowAll(true)).thenReturn(listContainersCmd);
        when(listContainersCmd.exec()).thenReturn(Collections.emptyList());
        when(dockerClient.listContainersCmd()).thenReturn(listContainersCmd);

        // Act & Assert
        assertThatThrownBy(() -> dockerService.getContainerLogs("non-existent", 100))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Container not found");
    }

    @Test
    void testGetContainerStats_containerNotFound_throwsException() {
        // Arrange
        when(listContainersCmd.withShowAll(true)).thenReturn(listContainersCmd);
        when(listContainersCmd.exec()).thenReturn(Collections.emptyList());
        when(dockerClient.listContainersCmd()).thenReturn(listContainersCmd);

        // Act & Assert
        assertThatThrownBy(() -> dockerService.getContainerStats("non-existent"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Container not found");
    }

    @Test
    void testTestConnection_successful() {
        // Arrange
        doNothing().when(pingCmd).exec();
        when(dockerClient.pingCmd()).thenReturn(pingCmd);

        // Act & Assert
        assertThatCode(() -> dockerService.testConnection()).doesNotThrowAnyException();
        verify(dockerClient).pingCmd();
        verify(pingCmd).exec();
    }

    @Test
    void testClose_closesDockerClient() throws Exception {
        // Act
        dockerService.close();

        // Assert
        verify(dockerClient).close();
    }

    @Test
    void testClose_withException_handlesGracefully() throws Exception {
        // Arrange
        doThrow(new RuntimeException("Close failed")).when(dockerClient).close();

        // Act & Assert - should not throw exception
        assertThatCode(() -> dockerService.close()).doesNotThrowAnyException();
        verify(dockerClient).close();
    }

    @Test
    void testFindContainerId_byShortId() throws Exception {
        // Arrange
        Container container = mock(Container.class);
        when(container.getId()).thenReturn("abc123def456");
        when(container.getNames()).thenReturn(new String[]{"/test"});

        when(listContainersCmd.withShowAll(true)).thenReturn(listContainersCmd);
        when(listContainersCmd.exec()).thenReturn(Collections.singletonList(container));
        when(dockerClient.listContainersCmd()).thenReturn(listContainersCmd);

        // Act - should find by partial ID match
        List<DockerService.ContainerInfo> result = dockerService.listContainers(true);

        // Assert
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getId()).startsWith("abc123");
    }

    @Test
    void testFindContainerId_byName() throws Exception {
        // Arrange
        Container container = mock(Container.class);
        when(container.getId()).thenReturn("xyz789012345");
        when(container.getNames()).thenReturn(new String[]{"/my-container", "/alias"});
        when(container.getImage()).thenReturn("test:latest");
        when(container.getState()).thenReturn("running");
        when(container.getStatus()).thenReturn("Up");

        when(listContainersCmd.withShowAll(true)).thenReturn(listContainersCmd);
        when(listContainersCmd.exec()).thenReturn(Collections.singletonList(container));
        when(dockerClient.listContainersCmd()).thenReturn(listContainersCmd);

        // Act
        List<DockerService.ContainerInfo> result = dockerService.listContainers(true);

        // Assert
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getName()).isEqualTo("my-container"); // First name without slash
    }


    @Test
    void testListContainers_handlesTruncatedContainerId() {
        // Arrange
        Container container = mock(Container.class);
        when(container.getId()).thenReturn("verylongcontainerid123456789");
        when(container.getNames()).thenReturn(new String[]{"/test"});
        when(container.getImage()).thenReturn("test:latest");
        when(container.getState()).thenReturn("running");
        when(container.getStatus()).thenReturn("Up");

        when(listContainersCmd.withShowAll(false)).thenReturn(listContainersCmd);
        when(listContainersCmd.exec()).thenReturn(Collections.singletonList(container));
        when(dockerClient.listContainersCmd()).thenReturn(listContainersCmd);

        // Act
        List<DockerService.ContainerInfo> result = dockerService.listContainers(false);

        // Assert
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getId()).hasSize(12); // Should be truncated to 12 chars
        assertThat(result.get(0).getId()).isEqualTo("verylongcont");
    }

}
