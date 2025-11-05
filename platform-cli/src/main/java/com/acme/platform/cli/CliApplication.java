package com.acme.platform.cli;

import com.acme.platform.cli.commands.*;
import com.acme.platform.cli.ui.InteractiveMenu;
import picocli.CommandLine;
import picocli.CommandLine.Command;

@Command(
        name = "platform-cli",
        description = "Messaging Platform CLI - Management and monitoring tool",
        mixinStandardHelpOptions = true,
        version = "1.0.0",
        subcommands = {
                DatabaseCommands.class,
                ApiCommands.class,
                MqCommands.class,
                KafkaCommands.class,
                DockerCommands.class
        }
)
public class CliApplication implements Runnable {

    public static void main(String[] args) {
        // If no arguments provided, start interactive menu
        if (args.length == 0) {
            InteractiveMenu menu = new InteractiveMenu();
            menu.start();
        } else {
            // Run CLI command
            int exitCode = new CommandLine(new CliApplication()).execute(args);
            System.exit(exitCode);
        }
    }

    @Override
    public void run() {
        // When run without subcommand, show help
        CommandLine.usage(this, System.out);
    }
}
