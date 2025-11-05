package com.acme.platform.cli.commands;

import org.junit.jupiter.api.Test;
import picocli.CommandLine;

import static org.assertj.core.api.Assertions.assertThat;

class ApiCommandsTest {

    @Test
    void testApiCommands_hasCorrectName() {
        CommandLine cmd = new CommandLine(new ApiCommands());
        assertThat(cmd.getCommandName()).isEqualTo("api");
    }

    @Test
    void testApiCommands_hasSubcommands() {
        CommandLine cmd = new CommandLine(new ApiCommands());
        assertThat(cmd.getSubcommands()).isNotEmpty();
        assertThat(cmd.getSubcommands()).containsKeys("exec", "get");
    }

    @Test
    void testExecCommand_exists() {
        CommandLine cmd = new CommandLine(new ApiCommands());
        CommandLine execCmd = cmd.getSubcommands().get("exec");

        assertThat(execCmd).isNotNull();
        assertThat(execCmd.getCommandName()).isEqualTo("exec");
    }

    @Test
    void testExecCommand_hasRequiredParameters() {
        CommandLine cmd = new CommandLine(new ApiCommands());
        CommandLine execCmd = cmd.getSubcommands().get("exec");

        assertThat(execCmd.getCommandSpec().positionalParameters()).hasSizeGreaterThanOrEqualTo(2);
    }

    @Test
    void testExecCommand_hasIdempotencyPrefixOption() {
        CommandLine cmd = new CommandLine(new ApiCommands());
        CommandLine execCmd = cmd.getSubcommands().get("exec");

        assertThat(execCmd.getCommandSpec().findOption('i')).isNotNull();
        assertThat(execCmd.getCommandSpec().findOption("idempotency-prefix")).isNotNull();
    }

    @Test
    void testExecCommand_hasFormatOption() {
        CommandLine cmd = new CommandLine(new ApiCommands());
        CommandLine execCmd = cmd.getSubcommands().get("exec");

        assertThat(execCmd.getCommandSpec().findOption('f')).isNotNull();
        assertThat(execCmd.getCommandSpec().findOption("format")).isNotNull();
    }

    @Test
    void testGetCommand_exists() {
        CommandLine cmd = new CommandLine(new ApiCommands());
        CommandLine getCmd = cmd.getSubcommands().get("get");

        assertThat(getCmd).isNotNull();
        assertThat(getCmd.getCommandName()).isEqualTo("get");
    }

    @Test
    void testGetCommand_hasEndpointParameter() {
        CommandLine cmd = new CommandLine(new ApiCommands());
        CommandLine getCmd = cmd.getSubcommands().get("get");

        assertThat(getCmd.getCommandSpec().positionalParameters()).isNotEmpty();
    }

    @Test
    void testGetCommand_hasFormatOption() {
        CommandLine cmd = new CommandLine(new ApiCommands());
        CommandLine getCmd = cmd.getSubcommands().get("get");

        assertThat(getCmd.getCommandSpec().findOption("format")).isNotNull();
    }
}
