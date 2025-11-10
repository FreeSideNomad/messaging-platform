package com.acme.reliable.command;

import com.acme.reliable.core.Envelope;

public interface CommandExecutor {
    void process(Envelope envelope);
}
