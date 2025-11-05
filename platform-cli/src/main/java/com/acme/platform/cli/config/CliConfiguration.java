package com.acme.platform.cli.config;

import io.github.cdimascio.dotenv.Dotenv;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CliConfiguration {
    private static final Logger logger = LoggerFactory.getLogger(CliConfiguration.class);
    private static CliConfiguration instance;
    private final Dotenv dotenv;

    private CliConfiguration() {
        try {
            this.dotenv = Dotenv.configure()
                    .ignoreIfMissing()
                    .load();
            logger.info("Configuration loaded successfully");
        } catch (Exception e) {
            logger.warn("Failed to load .env file, using system environment variables only", e);
            throw new RuntimeException("Failed to initialize configuration", e);
        }
    }

    public static synchronized CliConfiguration getInstance() {
        if (instance == null) {
            instance = new CliConfiguration();
        }
        return instance;
    }

    private String get(String key, String defaultValue) {
        String value = dotenv.get(key);
        return value != null ? value : defaultValue;
    }

    private int getInt(String key, int defaultValue) {
        String value = dotenv.get(key);
        if (value != null) {
            try {
                return Integer.parseInt(value);
            } catch (NumberFormatException e) {
                logger.warn("Invalid integer value for {}: {}, using default: {}", key, value, defaultValue);
            }
        }
        return defaultValue;
    }

    // Database Configuration
    public String getDbHost() {
        return get("DB_HOST", "localhost");
    }

    public int getDbPort() {
        return getInt("DB_PORT", 5432);
    }

    public String getDbName() {
        return get("DB_NAME", "reliable");
    }

    public String getDbUser() {
        return get("DB_USER", "postgres");
    }

    public String getDbPassword() {
        return get("DB_PASSWORD", "postgres");
    }

    public int getDbMaxPoolSize() {
        return getInt("DB_MAX_POOL_SIZE", 10);
    }

    public String getDbUrl() {
        return String.format("jdbc:postgresql://%s:%d/%s", getDbHost(), getDbPort(), getDbName());
    }

    // API Configuration
    public String getApiBaseUrl() {
        return get("API_BASE_URL", "http://localhost:8080");
    }

    public int getApiTimeoutSeconds() {
        return getInt("API_TIMEOUT_SECONDS", 30);
    }

    // Kafka Configuration
    public String getKafkaBootstrapServers() {
        return get("KAFKA_BOOTSTRAP_SERVERS", "localhost:9092");
    }

    public String getKafkaConsumerGroup() {
        return get("KAFKA_CONSUMER_GROUP", "platform-cli");
    }

    // RabbitMQ Configuration
    public String getRabbitmqHost() {
        return get("RABBITMQ_HOST", "localhost");
    }

    public int getRabbitmqPort() {
        return getInt("RABBITMQ_PORT", 5672);
    }

    public String getRabbitmqUser() {
        return get("RABBITMQ_USER", "guest");
    }

    public String getRabbitmqPassword() {
        return get("RABBITMQ_PASSWORD", "guest");
    }

    public String getRabbitmqVhost() {
        return get("RABBITMQ_VHOST", "/");
    }

    // Docker Configuration
    public String getDockerHost() {
        return get("DOCKER_HOST", "unix:///var/run/docker.sock");
    }

    // CLI Configuration
    public int getCliPageSize() {
        return getInt("CLI_PAGE_SIZE", 20);
    }

    public String getCliDefaultIdempotencyPrefix() {
        return get("CLI_DEFAULT_IDEMPOTENCY_PREFIX", "cli-request");
    }
}
