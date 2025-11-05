package com.acme.platform.cli;

import org.junit.jupiter.api.Test;
import picocli.CommandLine;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

import static org.assertj.core.api.Assertions.assertThat;

class CliApplicationTest {

    @Test
    void testCliApplication_showsHelpWhenNoSubcommand() {
        ByteArrayOutputStream outContent = new ByteArrayOutputStream();
        System.setOut(new PrintStream(outContent));

        CommandLine cmd = new CommandLine(new CliApplication());
        int exitCode = cmd.execute();

        assertThat(exitCode).isEqualTo(0);
        String output = outContent.toString();
        assertThat(output).contains("Usage:");
    }

    @Test
    void testCliApplication_hasCorrectName() {
        CommandLine cmd = new CommandLine(new CliApplication());
        assertThat(cmd.getCommandName()).isEqualTo("platform-cli");
    }

    @Test
    void testCliApplication_hasHelpOption() {
        ByteArrayOutputStream outContent = new ByteArrayOutputStream();
        System.setOut(new PrintStream(outContent));

        CommandLine cmd = new CommandLine(new CliApplication());
        int exitCode = cmd.execute("--help");

        assertThat(exitCode).isEqualTo(0);
        String output = outContent.toString();
        assertThat(output).contains("platform-cli");
        assertThat(output).contains("Management and monitoring tool");
    }

    @Test
    void testCliApplication_hasVersionOption() {
        ByteArrayOutputStream outContent = new ByteArrayOutputStream();
        System.setOut(new PrintStream(outContent));

        CommandLine cmd = new CommandLine(new CliApplication());
        int exitCode = cmd.execute("--version");

        assertThat(exitCode).isEqualTo(0);
        String output = outContent.toString();
        assertThat(output).contains("1.0.0");
    }

    @Test
    void testCliApplication_hasSubcommands() {
        CommandLine cmd = new CommandLine(new CliApplication());

        assertThat(cmd.getSubcommands()).isNotEmpty();
        assertThat(cmd.getSubcommands()).containsKeys("db", "api", "mq", "kafka", "docker");
    }

    @Test
    void testCliApplication_dbSubcommand() {
        CommandLine cmd = new CommandLine(new CliApplication());
        CommandLine dbCmd = cmd.getSubcommands().get("db");

        assertThat(dbCmd).isNotNull();
        assertThat(dbCmd.getCommandName()).isEqualTo("db");
    }

    @Test
    void testCliApplication_apiSubcommand() {
        CommandLine cmd = new CommandLine(new CliApplication());
        CommandLine apiCmd = cmd.getSubcommands().get("api");

        assertThat(apiCmd).isNotNull();
        assertThat(apiCmd.getCommandName()).isEqualTo("api");
    }

    @Test
    void testCliApplication_mqSubcommand() {
        CommandLine cmd = new CommandLine(new CliApplication());
        CommandLine mqCmd = cmd.getSubcommands().get("mq");

        assertThat(mqCmd).isNotNull();
        assertThat(mqCmd.getCommandName()).isEqualTo("mq");
    }

    @Test
    void testCliApplication_kafkaSubcommand() {
        CommandLine cmd = new CommandLine(new CliApplication());
        CommandLine kafkaCmd = cmd.getSubcommands().get("kafka");

        assertThat(kafkaCmd).isNotNull();
        assertThat(kafkaCmd.getCommandName()).isEqualTo("kafka");
    }

    @Test
    void testCliApplication_dockerSubcommand() {
        CommandLine cmd = new CommandLine(new CliApplication());
        CommandLine dockerCmd = cmd.getSubcommands().get("docker");

        assertThat(dockerCmd).isNotNull();
        assertThat(dockerCmd.getCommandName()).isEqualTo("docker");
    }
}
