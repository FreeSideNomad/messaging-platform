package com.acme.reliable.config;

/**
 * Configuration for messaging naming patterns and conventions. Pure POJO - no framework
 * dependencies.
 */
public class MessagingConfig {

  private QueueNaming queueNaming = new QueueNaming();
  private TopicNaming topicNaming = new TopicNaming();

  public QueueNaming getQueueNaming() {
    return queueNaming;
  }

  public void setQueueNaming(QueueNaming queueNaming) {
    this.queueNaming = queueNaming;
  }

  public TopicNaming getTopicNaming() {
    return topicNaming;
  }

  public void setTopicNaming(TopicNaming topicNaming) {
    this.topicNaming = topicNaming;
  }

  public static class QueueNaming {
    private String commandPrefix = "APP.CMD.";
    private String queueSuffix = ".Q";
    private String replyQueue = "APP.CMD.REPLY.Q";

    public String getCommandPrefix() {
      return commandPrefix;
    }

    public void setCommandPrefix(String commandPrefix) {
      this.commandPrefix = commandPrefix;
    }

    public String getQueueSuffix() {
      return queueSuffix;
    }

    public void setQueueSuffix(String queueSuffix) {
      this.queueSuffix = queueSuffix;
    }

    public String getReplyQueue() {
      return replyQueue;
    }

    public void setReplyQueue(String replyQueue) {
      this.replyQueue = replyQueue;
    }

    /**
     * Build a command queue name from a command name. Example: CreateUser -> APP.CMD.CREATEUSER.Q
     * (IBM MQ uses uppercase)
     */
    public String buildCommandQueue(String commandName) {
      return commandPrefix + commandName.toUpperCase(java.util.Locale.ROOT) + queueSuffix;
    }
  }

  public static class TopicNaming {
    private String eventPrefix = "events.";

    public String getEventPrefix() {
      return eventPrefix;
    }

    public void setEventPrefix(String eventPrefix) {
      this.eventPrefix = eventPrefix;
    }

    /** Build an event topic name from a command name. Example: CreateUser -> events.CreateUser */
    public String buildEventTopic(String commandName) {
      return eventPrefix + commandName;
    }
  }
}
