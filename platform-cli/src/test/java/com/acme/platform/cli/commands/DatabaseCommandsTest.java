package com.acme.platform.cli.commands;

import com.acme.platform.cli.commands.DatabaseCommands;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfEnvironmentVariable;
import picocli.CommandLine;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

import static org.assertj.core.api.Assertions.assertThat;

@DisabledIfEnvironmentVariable(named = "SKIP_DB_TESTS", matches = "true")
class DatabaseCommandsTest {

    @Test
    void testDatabaseCommands_hasCorrectName() {
        CommandLine cmd = new CommandLine(new DatabaseCommands());
        assertThat(cmd.getCommandName()).isEqualTo("db");
    }

    @Test
    void testDatabaseCommands_hasSubcommands() {
        CommandLine cmd = new CommandLine(new DatabaseCommands());
        assertThat(cmd.getSubcommands()).isNotEmpty();
        assertThat(cmd.getSubcommands()).containsKeys("query", "list", "info");
    }

    @Test
    void testQueryCommand_hasRequiredParameters() {
        CommandLine cmd = new CommandLine(new DatabaseCommands());
        CommandLine queryCmd = cmd.getSubcommands().get("query");

        assertThat(queryCmd).isNotNull();
        assertThat(queryCmd.getCommandSpec().positionalParameters()).isNotEmpty();
    }

    @Test
    void testQueryCommand_hasFormatOption() {
        CommandLine cmd = new CommandLine(new DatabaseCommands());
        CommandLine queryCmd = cmd.getSubcommands().get("query");

        assertThat(queryCmd.getCommandSpec().findOption('f')).isNotNull();
        assertThat(queryCmd.getCommandSpec().findOption("format")).isNotNull();
    }

    @Test
    void testQueryCommand_hasPageOption() {
        CommandLine cmd = new CommandLine(new DatabaseCommands());
        CommandLine queryCmd = cmd.getSubcommands().get("query");

        assertThat(queryCmd.getCommandSpec().findOption('p')).isNotNull();
        assertThat(queryCmd.getCommandSpec().findOption("page")).isNotNull();
    }

    @Test
    void testQueryCommand_hasPageSizeOption() {
        CommandLine cmd = new CommandLine(new DatabaseCommands());
        CommandLine queryCmd = cmd.getSubcommands().get("query");

        assertThat(queryCmd.getCommandSpec().findOption('s')).isNotNull();
        assertThat(queryCmd.getCommandSpec().findOption("page-size")).isNotNull();
    }

    @Test
    void testListCommand_exists() {
        CommandLine cmd = new CommandLine(new DatabaseCommands());
        CommandLine listCmd = cmd.getSubcommands().get("list");

        assertThat(listCmd).isNotNull();
        assertThat(listCmd.getCommandName()).isEqualTo("list");
    }

    @Test
    void testListCommand_hasFormatOption() {
        CommandLine cmd = new CommandLine(new DatabaseCommands());
        CommandLine listCmd = cmd.getSubcommands().get("list");

        assertThat(listCmd.getCommandSpec().findOption("format")).isNotNull();
    }

    @Test
    void testInfoCommand_exists() {
        CommandLine cmd = new CommandLine(new DatabaseCommands());
        CommandLine infoCmd = cmd.getSubcommands().get("info");

        assertThat(infoCmd).isNotNull();
        assertThat(infoCmd.getCommandName()).isEqualTo("info");
    }

    @Test
    void testInfoCommand_hasTableParameter() {
        CommandLine cmd = new CommandLine(new DatabaseCommands());
        CommandLine infoCmd = cmd.getSubcommands().get("info");

        assertThat(infoCmd.getCommandSpec().positionalParameters()).isNotEmpty();
    }

    @Test
    void testInfoCommand_hasFormatOption() {
        CommandLine cmd = new CommandLine(new DatabaseCommands());
        CommandLine infoCmd = cmd.getSubcommands().get("info");

        assertThat(infoCmd.getCommandSpec().findOption("format")).isNotNull();
    }

    @Test
    void testDatabaseCommands_structure() {
        CommandLine cmd = new CommandLine(new DatabaseCommands());

        // Verify command structure
        assertThat(cmd.getCommandSpec()).isNotNull();
        assertThat(cmd.getCommandSpec().name()).isEqualTo("db");
    }
}
