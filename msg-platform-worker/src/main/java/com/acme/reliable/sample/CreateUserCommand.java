package com.acme.reliable.sample;

import com.acme.reliable.command.DomainCommand;

public record CreateUserCommand(String username) implements DomainCommand {
}