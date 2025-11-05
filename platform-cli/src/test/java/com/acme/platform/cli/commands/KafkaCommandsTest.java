package com.acme.platform.cli.commands;

import org.junit.jupiter.api.Test;
import picocli.CommandLine;

import static org.assertj.core.api.Assertions.assertThat;

class KafkaCommandsTest {

    @Test
    void testKafkaCommands_hasCorrectName() {
        CommandLine cmd = new CommandLine(new KafkaCommands());
        assertThat(cmd.getCommandName()).isEqualTo("kafka");
    }

    @Test
    void testKafkaCommands_hasSubcommands() {
        CommandLine cmd = new CommandLine(new KafkaCommands());
        assertThat(cmd.getSubcommands()).isNotEmpty();
        assertThat(cmd.getSubcommands()).containsKeys("topics", "topic", "lag");
    }

    @Test
    void testTopicsCommand_exists() {
        CommandLine cmd = new CommandLine(new KafkaCommands());
        CommandLine topicsCmd = cmd.getSubcommands().get("topics");

        assertThat(topicsCmd).isNotNull();
        assertThat(topicsCmd.getCommandName()).isEqualTo("topics");
    }

    @Test
    void testTopicsCommand_hasFormatOption() {
        CommandLine cmd = new CommandLine(new KafkaCommands());
        CommandLine topicsCmd = cmd.getSubcommands().get("topics");

        assertThat(topicsCmd.getCommandSpec().findOption("format")).isNotNull();
    }

    @Test
    void testTopicCommand_exists() {
        CommandLine cmd = new CommandLine(new KafkaCommands());
        CommandLine topicCmd = cmd.getSubcommands().get("topic");

        assertThat(topicCmd).isNotNull();
        assertThat(topicCmd.getCommandName()).isEqualTo("topic");
    }

    @Test
    void testTopicCommand_hasTopicNameParameter() {
        CommandLine cmd = new CommandLine(new KafkaCommands());
        CommandLine topicCmd = cmd.getSubcommands().get("topic");

        assertThat(topicCmd.getCommandSpec().positionalParameters()).isNotEmpty();
    }

    @Test
    void testTopicCommand_hasFormatOption() {
        CommandLine cmd = new CommandLine(new KafkaCommands());
        CommandLine topicCmd = cmd.getSubcommands().get("topic");

        assertThat(topicCmd.getCommandSpec().findOption("format")).isNotNull();
    }

    @Test
    void testLagCommand_exists() {
        CommandLine cmd = new CommandLine(new KafkaCommands());
        CommandLine lagCmd = cmd.getSubcommands().get("lag");

        assertThat(lagCmd).isNotNull();
        assertThat(lagCmd.getCommandName()).isEqualTo("lag");
    }

    @Test
    void testLagCommand_hasGroupIdParameter() {
        CommandLine cmd = new CommandLine(new KafkaCommands());
        CommandLine lagCmd = cmd.getSubcommands().get("lag");

        assertThat(lagCmd.getCommandSpec().positionalParameters()).isNotEmpty();
    }

    @Test
    void testLagCommand_hasFormatOption() {
        CommandLine cmd = new CommandLine(new KafkaCommands());
        CommandLine lagCmd = cmd.getSubcommands().get("lag");

        assertThat(lagCmd.getCommandSpec().findOption("format")).isNotNull();
    }
}
