package com.acme.reliable.core;

import com.acme.reliable.spi.CommandService;
import com.acme.reliable.spi.OutboxService;
import jakarta.inject.Singleton;
import jakarta.transaction.Transactional;
import java.util.Map;
import java.util.UUID;

@Singleton
public class CommandBus {
    private final CommandService commands;
    private final OutboxService outboxStore;
    private final Outbox outbox;
    private final FastPathPublisher fastPath;

    public CommandBus(CommandService c, OutboxService os, Outbox o, FastPathPublisher f) {
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
        fastPath.registerAfterCommit(outboxId);
        return id;
    }
}
