package com.acme.platform.cli.commands;

import org.junit.jupiter.api.Test;
import picocli.CommandLine;

import static org.assertj.core.api.Assertions.assertThat;

class MqCommandsTest {

    @Test
    void testMqCommands_hasCorrectName() {
        CommandLine cmd = new CommandLine(new MqCommands());
        assertThat(cmd.getCommandName()).isEqualTo("mq");
    }

    @Test
    void testMqCommands_hasSubcommands() {
        CommandLine cmd = new CommandLine(new MqCommands());
        assertThat(cmd.getSubcommands()).isNotEmpty();
        assertThat(cmd.getSubcommands()).containsKeys("list", "status");
    }

    @Test
    void testListCommand_exists() {
        CommandLine cmd = new CommandLine(new MqCommands());
        CommandLine listCmd = cmd.getSubcommands().get("list");

        assertThat(listCmd).isNotNull();
        assertThat(listCmd.getCommandName()).isEqualTo("list");
    }

    @Test
    void testListCommand_hasFormatOption() {
        CommandLine cmd = new CommandLine(new MqCommands());
        CommandLine listCmd = cmd.getSubcommands().get("list");

        assertThat(listCmd.getCommandSpec().findOption("format")).isNotNull();
    }

    @Test
    void testStatusCommand_exists() {
        CommandLine cmd = new CommandLine(new MqCommands());
        CommandLine statusCmd = cmd.getSubcommands().get("status");

        assertThat(statusCmd).isNotNull();
        assertThat(statusCmd.getCommandName()).isEqualTo("status");
    }

    @Test
    void testStatusCommand_hasQueueParameter() {
        CommandLine cmd = new CommandLine(new MqCommands());
        CommandLine statusCmd = cmd.getSubcommands().get("status");

        assertThat(statusCmd.getCommandSpec().positionalParameters()).isNotEmpty();
    }

    @Test
    void testStatusCommand_hasFormatOption() {
        CommandLine cmd = new CommandLine(new MqCommands());
        CommandLine statusCmd = cmd.getSubcommands().get("status");

        assertThat(statusCmd.getCommandSpec().findOption("format")).isNotNull();
    }
}
