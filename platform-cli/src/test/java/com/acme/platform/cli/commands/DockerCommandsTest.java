package com.acme.platform.cli.commands;

import org.junit.jupiter.api.Test;
import picocli.CommandLine;

import static org.assertj.core.api.Assertions.assertThat;

class DockerCommandsTest {

    @Test
    void testDockerCommands_hasCorrectName() {
        CommandLine cmd = new CommandLine(new DockerCommands());
        assertThat(cmd.getCommandName()).isEqualTo("docker");
    }

    @Test
    void testDockerCommands_hasSubcommands() {
        CommandLine cmd = new CommandLine(new DockerCommands());
        assertThat(cmd.getSubcommands()).isNotEmpty();
        assertThat(cmd.getSubcommands()).containsKeys("list", "exec", "logs", "stats");
    }

    @Test
    void testListCommand_exists() {
        CommandLine cmd = new CommandLine(new DockerCommands());
        CommandLine listCmd = cmd.getSubcommands().get("list");

        assertThat(listCmd).isNotNull();
        assertThat(listCmd.getCommandName()).isEqualTo("list");
    }

    @Test
    void testListCommand_hasAllOption() {
        CommandLine cmd = new CommandLine(new DockerCommands());
        CommandLine listCmd = cmd.getSubcommands().get("list");

        assertThat(listCmd.getCommandSpec().findOption('a')).isNotNull();
        assertThat(listCmd.getCommandSpec().findOption("all")).isNotNull();
    }

    @Test
    void testListCommand_hasFormatOption() {
        CommandLine cmd = new CommandLine(new DockerCommands());
        CommandLine listCmd = cmd.getSubcommands().get("list");

        assertThat(listCmd.getCommandSpec().findOption("format")).isNotNull();
    }

    @Test
    void testExecCommand_exists() {
        CommandLine cmd = new CommandLine(new DockerCommands());
        CommandLine execCmd = cmd.getSubcommands().get("exec");

        assertThat(execCmd).isNotNull();
        assertThat(execCmd.getCommandName()).isEqualTo("exec");
    }

    @Test
    void testExecCommand_hasRequiredParameters() {
        CommandLine cmd = new CommandLine(new DockerCommands());
        CommandLine execCmd = cmd.getSubcommands().get("exec");

        assertThat(execCmd.getCommandSpec().positionalParameters()).hasSizeGreaterThanOrEqualTo(2);
    }

    @Test
    void testExecCommand_hasFormatOption() {
        CommandLine cmd = new CommandLine(new DockerCommands());
        CommandLine execCmd = cmd.getSubcommands().get("exec");

        assertThat(execCmd.getCommandSpec().findOption("format")).isNotNull();
    }

    @Test
    void testLogsCommand_exists() {
        CommandLine cmd = new CommandLine(new DockerCommands());
        CommandLine logsCmd = cmd.getSubcommands().get("logs");

        assertThat(logsCmd).isNotNull();
        assertThat(logsCmd.getCommandName()).isEqualTo("logs");
    }

    @Test
    void testLogsCommand_hasContainerParameter() {
        CommandLine cmd = new CommandLine(new DockerCommands());
        CommandLine logsCmd = cmd.getSubcommands().get("logs");

        assertThat(logsCmd.getCommandSpec().positionalParameters()).isNotEmpty();
    }

    @Test
    void testLogsCommand_hasTailOption() {
        CommandLine cmd = new CommandLine(new DockerCommands());
        CommandLine logsCmd = cmd.getSubcommands().get("logs");

        assertThat(logsCmd.getCommandSpec().findOption('n')).isNotNull();
        assertThat(logsCmd.getCommandSpec().findOption("tail")).isNotNull();
    }

    @Test
    void testLogsCommand_hasFormatOption() {
        CommandLine cmd = new CommandLine(new DockerCommands());
        CommandLine logsCmd = cmd.getSubcommands().get("logs");

        assertThat(logsCmd.getCommandSpec().findOption("format")).isNotNull();
    }

    @Test
    void testStatsCommand_exists() {
        CommandLine cmd = new CommandLine(new DockerCommands());
        CommandLine statsCmd = cmd.getSubcommands().get("stats");

        assertThat(statsCmd).isNotNull();
        assertThat(statsCmd.getCommandName()).isEqualTo("stats");
    }

    @Test
    void testStatsCommand_hasContainerParameter() {
        CommandLine cmd = new CommandLine(new DockerCommands());
        CommandLine statsCmd = cmd.getSubcommands().get("stats");

        assertThat(statsCmd.getCommandSpec().positionalParameters()).isNotEmpty();
    }

    @Test
    void testStatsCommand_hasFormatOption() {
        CommandLine cmd = new CommandLine(new DockerCommands());
        CommandLine statsCmd = cmd.getSubcommands().get("stats");

        assertThat(statsCmd.getCommandSpec().findOption("format")).isNotNull();
    }
}
