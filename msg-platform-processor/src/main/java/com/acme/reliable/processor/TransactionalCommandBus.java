package com.acme.reliable.processor;

import com.acme.reliable.command.CommandBus;
import com.acme.reliable.core.Jsons;
import com.acme.reliable.core.Outbox;
import com.acme.reliable.service.CommandService;
import com.acme.reliable.service.OutboxService;
import io.micronaut.transaction.annotation.Transactional;
import jakarta.inject.Singleton;
import java.util.Map;
import java.util.UUID;

@Singleton
public class TransactionalCommandBus implements CommandBus {
    private final CommandService commands;
    private final OutboxService outboxStore;
    private final Outbox outbox;
    private final FastPathPublisher fastPath;

    public TransactionalCommandBus(CommandService c, OutboxService os, Outbox o, FastPathPublisher f) {
        this.commands = c;
        this.outboxStore = os;
        this.outbox = o;
        this.fastPath = f;
    }

    @Transactional
    public UUID accept(String name, String idem, String bizKey, String payload, Map<String,String> reply) {
        if (commands.existsByIdempotencyKey(idem)) {
            throw new IllegalStateException("Duplicate idempotency key");
        }
        UUID id = commands.savePending(name, idem, bizKey, payload, Jsons.toJson(reply));
        UUID outboxId = outboxStore.addReturningId(outbox.rowCommandRequested(name, id, bizKey, payload, reply));
        // fastPath.registerAfterCommit(outboxId); // DISABLED: causing transaction leak
        return id;
    }
}
