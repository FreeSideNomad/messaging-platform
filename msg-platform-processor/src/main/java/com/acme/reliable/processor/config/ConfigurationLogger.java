package com.acme.reliable.processor.config;

import com.acme.reliable.config.TimeoutConfig;
import com.acme.reliable.config.MessagingConfig;
import io.micronaut.context.annotation.Property;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.event.ApplicationEventListener;
import io.micronaut.runtime.server.event.ServerStartupEvent;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Logs effective configuration on application startup for visibility and troubleshooting.
 * Disabled in test environment to avoid configuration errors.
 */
@Singleton
@Requires(notEnv = "test")
public class ConfigurationLogger implements ApplicationEventListener<ServerStartupEvent> {

    private static final Logger LOG = LoggerFactory.getLogger(ConfigurationLogger.class);

    private final TimeoutConfig timeoutConfig;
    private final MessagingConfig messagingConfig;

    @Property(name = "micronaut.server.port")
    private int serverPort;

    @Property(name = "datasources.default.url")
    private String datasourceUrl;

    @Property(name = "datasources.default.username")
    private String datasourceUsername;

    @Property(name = "datasources.default.maximum-pool-size")
    private int maxPoolSize;

    @Property(name = "datasources.default.minimum-idle")
    private int minIdle;

    @Property(name = "micronaut.server.netty.worker.threads")
    private int workerThreads;

    @Property(name = "micronaut.server.netty.parent.threads")
    private int parentThreads;

    @Property(name = "kafka.bootstrap.servers")
    private String kafkaBootstrapServers;

    @Property(name = "jms.consumers.enabled")
    private boolean jmsConsumersEnabled;

    public ConfigurationLogger(TimeoutConfig timeoutConfig, MessagingConfig messagingConfig) {
        this.timeoutConfig = timeoutConfig;
        this.messagingConfig = messagingConfig;
    }

    @Override
    public void onApplicationEvent(ServerStartupEvent event) {
        LOG.info("═══════════════════════════════════════════════════════════════════════════════");
        LOG.info("                         EFFECTIVE CONFIGURATION                                ");
        LOG.info("═══════════════════════════════════════════════════════════════════════════════");
        LOG.info("");

        // Server Configuration
        LOG.info("━━━ Server Configuration ━━━");
        LOG.info("  Port:               {} (HTTP endpoint listening port)", serverPort);
        LOG.info("  Worker Threads:     {} (Netty I/O worker threads for handling requests)", workerThreads);
        LOG.info("  Parent Threads:     {} (Netty boss threads for accepting connections)", parentThreads);
        LOG.info("");

        // Database Configuration
        LOG.info("━━━ Database Configuration ━━━");
        LOG.info("  JDBC URL:           {} (PostgreSQL connection string)", datasourceUrl);
        LOG.info("  Username:           {} (Database user)", datasourceUsername);
        LOG.info("  Max Pool Size:      {} (HikariCP maximum connections)", maxPoolSize);
        LOG.info("  Min Idle:           {} (HikariCP minimum idle connections)", minIdle);
        LOG.info("");

        // Timeout Configuration
        LOG.info("━━━ Timeout & Retry Configuration ━━━");
        LOG.info("  Command Lease:      {} (How long a command is locked for processing)", timeoutConfig.getCommandLease());
        LOG.info("  Max Backoff:        {} (Maximum retry backoff for outbox publishing)", timeoutConfig.getMaxBackoff());
        LOG.info("  Sync Wait:          {} (HTTP response wait time; 0s = fully async)", timeoutConfig.getSyncWait());
        LOG.info("  Async Mode:         {} (Returns HTTP 202 immediately if true)", timeoutConfig.isAsync());
        LOG.info("  Outbox Sweep:       {} (How often to check for unpublished outbox entries)", timeoutConfig.getOutboxSweepInterval());
        LOG.info("  Outbox Batch Size:  {} (Number of outbox entries processed per sweep)", timeoutConfig.getOutboxBatchSize());
        LOG.info("  Claim Timeout:      {} (Timeout to reclaim stuck CLAIMED outbox messages)", timeoutConfig.getOutboxClaimTimeout());
        LOG.info("");

        // Messaging Configuration
        LOG.info("━━━ Messaging Configuration ━━━");
        LOG.info("  JMS Consumers:      {} (IBM MQ message listeners enabled)", jmsConsumersEnabled ? "ENABLED" : "DISABLED");
        LOG.info("  Command Prefix:     {} (IBM MQ command queue prefix)", messagingConfig.getQueueNaming().getCommandPrefix());
        LOG.info("  Queue Suffix:       {} (IBM MQ queue name suffix)", messagingConfig.getQueueNaming().getQueueSuffix());
        LOG.info("  Reply Queue:        {} (IBM MQ reply queue name)", messagingConfig.getQueueNaming().getReplyQueue());
        LOG.info("  Event Prefix:       {} (Kafka topic prefix for events)", messagingConfig.getTopicNaming().getEventPrefix());
        LOG.info("  Kafka Servers:      {} (Kafka bootstrap servers)", kafkaBootstrapServers);
        LOG.info("");

        // Performance Summary
        LOG.info("━━━ Performance Profile ━━━");
        String profile = determinePerformanceProfile();
        LOG.info("  Profile:            {}", profile);
        LOG.info("  Expected TPS:       {}", estimateTPS());
        LOG.info("");

        LOG.info("═══════════════════════════════════════════════════════════════════════════════");
        LOG.info("                      APPLICATION READY FOR TRAFFIC                             ");
        LOG.info("═══════════════════════════════════════════════════════════════════════════════");
        LOG.info("");
    }

    private String determinePerformanceProfile() {
        if (maxPoolSize >= 1000 && workerThreads >= 500) {
            return "ULTRA HIGH THROUGHPUT (1000+ TPS)";
        } else if (maxPoolSize >= 400 && workerThreads >= 200) {
            return "HIGH THROUGHPUT (400-1000 TPS)";
        } else if (maxPoolSize >= 100 && workerThreads >= 100) {
            return "MEDIUM THROUGHPUT (100-400 TPS)";
        } else {
            return "STANDARD (< 100 TPS)";
        }
    }

    private String estimateTPS() {
        // Rough estimate based on connection pool size
        // Each request needs ~1-2 connections (command + outbox)
        int estimatedTPS = maxPoolSize / 4; // Conservative estimate
        if (estimatedTPS > 1000) {
            return "1000+ TPS";
        } else if (estimatedTPS > 500) {
            return "500-1000 TPS";
        } else if (estimatedTPS > 100) {
            return "100-500 TPS";
        } else {
            return "50-100 TPS";
        }
    }
}
