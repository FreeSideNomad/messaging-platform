package com.acme.reliable.config;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** Unit tests for MessagingConfig */
class MessagingConfigTest {

  @Nested
  @DisplayName("MessagingConfig Tests")
  class MessagingConfigTests {

    @Test
    @DisplayName("should have default queue naming configuration")
    void testDefaultQueueNaming() {
      MessagingConfig config = new MessagingConfig();

      assertThat(config.getQueueNaming()).isNotNull();
      assertThat(config.getQueueNaming().getCommandPrefix()).isEqualTo("APP.CMD.");
      assertThat(config.getQueueNaming().getQueueSuffix()).isEqualTo(".Q");
      assertThat(config.getQueueNaming().getReplyQueue()).isEqualTo("APP.CMD.REPLY.Q");
    }

    @Test
    @DisplayName("should have default topic naming configuration")
    void testDefaultTopicNaming() {
      MessagingConfig config = new MessagingConfig();

      assertThat(config.getTopicNaming()).isNotNull();
      assertThat(config.getTopicNaming().getEventPrefix()).isEqualTo("events.");
    }

    @Test
    @DisplayName("should allow setting custom queue naming")
    void testSetQueueNaming() {
      MessagingConfig config = new MessagingConfig();
      MessagingConfig.QueueNaming customNaming = new MessagingConfig.QueueNaming();
      customNaming.setCommandPrefix("CUSTOM.");

      config.setQueueNaming(customNaming);

      assertThat(config.getQueueNaming()).isSameAs(customNaming);
      assertThat(config.getQueueNaming().getCommandPrefix()).isEqualTo("CUSTOM.");
    }

    @Test
    @DisplayName("should allow setting custom topic naming")
    void testSetTopicNaming() {
      MessagingConfig config = new MessagingConfig();
      MessagingConfig.TopicNaming customNaming = new MessagingConfig.TopicNaming();
      customNaming.setEventPrefix("custom.events.");

      config.setTopicNaming(customNaming);

      assertThat(config.getTopicNaming()).isSameAs(customNaming);
      assertThat(config.getTopicNaming().getEventPrefix()).isEqualTo("custom.events.");
    }
  }

  @Nested
  @DisplayName("QueueNaming Tests")
  class QueueNamingTests {

    @Test
    @DisplayName("buildCommandQueue - should build queue name with defaults")
    void testBuildCommandQueueDefaults() {
      MessagingConfig.QueueNaming naming = new MessagingConfig.QueueNaming();

      String queueName = naming.buildCommandQueue("CreateUser");

      assertThat(queueName).isEqualTo("APP.CMD.CREATEUSER.Q");
    }

    @Test
    @DisplayName("buildCommandQueue - should convert to uppercase")
    void testBuildCommandQueueUppercase() {
      MessagingConfig.QueueNaming naming = new MessagingConfig.QueueNaming();

      String queueName = naming.buildCommandQueue("deleteAccount");

      assertThat(queueName).isEqualTo("APP.CMD.DELETEACCOUNT.Q");
    }

    @Test
    @DisplayName("buildCommandQueue - should use custom prefix and suffix")
    void testBuildCommandQueueCustom() {
      MessagingConfig.QueueNaming naming = new MessagingConfig.QueueNaming();
      naming.setCommandPrefix("PROD.COMMAND.");
      naming.setQueueSuffix(".QUEUE");

      String queueName = naming.buildCommandQueue("ProcessPayment");

      assertThat(queueName).isEqualTo("PROD.COMMAND.PROCESSPAYMENT.QUEUE");
    }

    @Test
    @DisplayName("setCommandPrefix - should update prefix")
    void testSetCommandPrefix() {
      MessagingConfig.QueueNaming naming = new MessagingConfig.QueueNaming();

      naming.setCommandPrefix("TEST.CMD.");

      assertThat(naming.getCommandPrefix()).isEqualTo("TEST.CMD.");
      assertThat(naming.buildCommandQueue("Test")).startsWith("TEST.CMD.");
    }

    @Test
    @DisplayName("setQueueSuffix - should update suffix")
    void testSetQueueSuffix() {
      MessagingConfig.QueueNaming naming = new MessagingConfig.QueueNaming();

      naming.setQueueSuffix(".QUEUE");

      assertThat(naming.getQueueSuffix()).isEqualTo(".QUEUE");
      assertThat(naming.buildCommandQueue("Test")).endsWith(".QUEUE");
    }

    @Test
    @DisplayName("setReplyQueue - should update reply queue")
    void testSetReplyQueue() {
      MessagingConfig.QueueNaming naming = new MessagingConfig.QueueNaming();

      naming.setReplyQueue("CUSTOM.REPLY.QUEUE");

      assertThat(naming.getReplyQueue()).isEqualTo("CUSTOM.REPLY.QUEUE");
    }

    @Test
    @DisplayName("buildCommandQueue - should handle empty command name")
    void testBuildCommandQueueEmpty() {
      MessagingConfig.QueueNaming naming = new MessagingConfig.QueueNaming();

      String queueName = naming.buildCommandQueue("");

      assertThat(queueName).isEqualTo("APP.CMD..Q");
    }

    @Test
    @DisplayName("buildCommandQueue - should handle special characters")
    void testBuildCommandQueueSpecialChars() {
      MessagingConfig.QueueNaming naming = new MessagingConfig.QueueNaming();

      String queueName = naming.buildCommandQueue("Create-User_V2");

      assertThat(queueName).isEqualTo("APP.CMD.CREATE-USER_V2.Q");
    }
  }

  @Nested
  @DisplayName("TopicNaming Tests")
  class TopicNamingTests {

    @Test
    @DisplayName("buildEventTopic - should build topic name with defaults")
    void testBuildEventTopicDefaults() {
      MessagingConfig.TopicNaming naming = new MessagingConfig.TopicNaming();

      String topicName = naming.buildEventTopic("UserCreated");

      assertThat(topicName).isEqualTo("events.UserCreated");
    }

    @Test
    @DisplayName("buildEventTopic - should preserve case")
    void testBuildEventTopicCase() {
      MessagingConfig.TopicNaming naming = new MessagingConfig.TopicNaming();

      String topicName = naming.buildEventTopic("accountDeleted");

      assertThat(topicName).isEqualTo("events.accountDeleted");
    }

    @Test
    @DisplayName("buildEventTopic - should use custom prefix")
    void testBuildEventTopicCustomPrefix() {
      MessagingConfig.TopicNaming naming = new MessagingConfig.TopicNaming();
      naming.setEventPrefix("prod.events.");

      String topicName = naming.buildEventTopic("OrderPlaced");

      assertThat(topicName).isEqualTo("prod.events.OrderPlaced");
    }

    @Test
    @DisplayName("setEventPrefix - should update prefix")
    void testSetEventPrefix() {
      MessagingConfig.TopicNaming naming = new MessagingConfig.TopicNaming();

      naming.setEventPrefix("custom.topic.");

      assertThat(naming.getEventPrefix()).isEqualTo("custom.topic.");
      assertThat(naming.buildEventTopic("Test")).startsWith("custom.topic.");
    }

    @Test
    @DisplayName("buildEventTopic - should handle empty event name")
    void testBuildEventTopicEmpty() {
      MessagingConfig.TopicNaming naming = new MessagingConfig.TopicNaming();

      String topicName = naming.buildEventTopic("");

      assertThat(topicName).isEqualTo("events.");
    }

    @Test
    @DisplayName("buildEventTopic - should handle special characters")
    void testBuildEventTopicSpecialChars() {
      MessagingConfig.TopicNaming naming = new MessagingConfig.TopicNaming();

      String topicName = naming.buildEventTopic("User.Created-v2");

      assertThat(topicName).isEqualTo("events.User.Created-v2");
    }
  }

  @Nested
  @DisplayName("Integration Tests")
  class IntegrationTests {

    @Test
    @DisplayName("should work together with custom configuration")
    void testFullCustomConfiguration() {
      MessagingConfig config = new MessagingConfig();

      // Customize queue naming
      config.getQueueNaming().setCommandPrefix("PROD.CMD.");
      config.getQueueNaming().setQueueSuffix(".QUEUE");
      config.getQueueNaming().setReplyQueue("PROD.REPLY.QUEUE");

      // Customize topic naming
      config.getTopicNaming().setEventPrefix("prod.events.");

      // Verify queue naming
      assertThat(config.getQueueNaming().buildCommandQueue("CreateUser"))
          .isEqualTo("PROD.CMD.CREATEUSER.QUEUE");
      assertThat(config.getQueueNaming().getReplyQueue()).isEqualTo("PROD.REPLY.QUEUE");

      // Verify topic naming
      assertThat(config.getTopicNaming().buildEventTopic("UserCreated"))
          .isEqualTo("prod.events.UserCreated");
    }

    @Test
    @DisplayName("should support multiple instances with different configs")
    void testMultipleInstances() {
      MessagingConfig devConfig = new MessagingConfig();
      devConfig.getQueueNaming().setCommandPrefix("DEV.CMD.");

      MessagingConfig prodConfig = new MessagingConfig();
      prodConfig.getQueueNaming().setCommandPrefix("PROD.CMD.");

      assertThat(devConfig.getQueueNaming().buildCommandQueue("Test")).isEqualTo("DEV.CMD.TEST.Q");
      assertThat(prodConfig.getQueueNaming().buildCommandQueue("Test"))
          .isEqualTo("PROD.CMD.TEST.Q");
    }
  }
}
