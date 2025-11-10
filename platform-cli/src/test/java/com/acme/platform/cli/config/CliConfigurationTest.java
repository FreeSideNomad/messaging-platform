package com.acme.platform.cli.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;

import static org.assertj.core.api.Assertions.assertThat;

class CliConfigurationTest {

    private CliConfiguration config;

    @BeforeEach
    void setUp() {
        config = CliConfiguration.getInstance();
    }

    @Test
    void testGetInstance_returnsSingleton() {
        CliConfiguration instance1 = CliConfiguration.getInstance();
        CliConfiguration instance2 = CliConfiguration.getInstance();

        assertThat(instance1).isSameAs(instance2);
    }

    @Test
    void testGetDbHost_returnsValue() {
        String dbHost = config.getDbHost();
        assertThat(dbHost).isNotNull();
    }

    @Test
    void testGetDbPort_returnsValue() {
        int dbPort = config.getDbPort();
        assertThat(dbPort).isGreaterThan(0);
    }

    @Test
    void testGetDbName_returnsValue() {
        String dbName = config.getDbName();
        assertThat(dbName).isNotNull();
    }

    @Test
    void testGetDbUser_returnsValue() {
        String dbUser = config.getDbUser();
        assertThat(dbUser).isNotNull();
    }

    @Test
    void testGetDbPassword_returnsValue() {
        String dbPassword = config.getDbPassword();
        assertThat(dbPassword).isNotNull();
    }

    @Test
    void testGetDbMaxPoolSize_returnsValue() {
        int maxPoolSize = config.getDbMaxPoolSize();
        assertThat(maxPoolSize).isGreaterThan(0);
    }

    @Test
    void testGetDbUrl_returnsFormattedUrl() {
        String dbUrl = config.getDbUrl();
        assertThat(dbUrl)
                .isNotNull()
                .startsWith("jdbc:postgresql://")
                .contains(config.getDbHost())
                .contains(String.valueOf(config.getDbPort()))
                .contains(config.getDbName());
    }

    @Test
    void testGetApiBaseUrl_returnsValue() {
        String apiBaseUrl = config.getApiBaseUrl();
        assertThat(apiBaseUrl).isNotNull();
    }

    @Test
    void testGetApiTimeoutSeconds_returnsValue() {
        int timeout = config.getApiTimeoutSeconds();
        assertThat(timeout).isGreaterThan(0);
    }

    @Test
    void testGetKafkaBootstrapServers_returnsValue() {
        String servers = config.getKafkaBootstrapServers();
        assertThat(servers).isNotNull();
    }

    @Test
    void testGetKafkaConsumerGroup_returnsValue() {
        String group = config.getKafkaConsumerGroup();
        assertThat(group).isNotNull();
    }

    @Test
    void testGetRabbitmqHost_returnsValue() {
        String host = config.getRabbitmqHost();
        assertThat(host).isNotNull();
    }

    @Test
    void testGetRabbitmqPort_returnsValue() {
        int port = config.getRabbitmqPort();
        assertThat(port).isGreaterThan(0);
    }

    @Test
    void testGetRabbitmqUser_returnsValue() {
        String user = config.getRabbitmqUser();
        assertThat(user).isNotNull();
    }

    @Test
    void testGetRabbitmqPassword_returnsValue() {
        String password = config.getRabbitmqPassword();
        assertThat(password).isNotNull();
    }

    @Test
    void testGetRabbitmqVhost_returnsValue() {
        String vhost = config.getRabbitmqVhost();
        assertThat(vhost).isNotNull();
    }

    @Test
    void testGetDockerHost_returnsValue() {
        String dockerHost = config.getDockerHost();
        assertThat(dockerHost).isNotNull();
    }

    @Test
    void testGetCliPageSize_returnsValue() {
        int pageSize = config.getCliPageSize();
        assertThat(pageSize).isGreaterThan(0);
    }

    @Test
    void testGetCliDefaultIdempotencyPrefix_returnsValue() {
        String prefix = config.getCliDefaultIdempotencyPrefix();
        assertThat(prefix).isNotNull();
    }
}
