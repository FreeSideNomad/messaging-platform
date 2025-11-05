package com.acme.payments.application.command;

import com.acme.reliable.command.DomainCommand;
import java.util.UUID;

/** Compensation command to unwind (cancel) a previously booked FX contract */
public record UnwindFxCommand(UUID fxContractId, String reason) implements DomainCommand {}
