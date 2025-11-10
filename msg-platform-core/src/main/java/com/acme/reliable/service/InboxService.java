package com.acme.reliable.service;

/**
 * Service for Inbox pattern operations - idempotent message processing
 */
public interface InboxService {

    /**
     * Mark a message as processed if not already processed (idempotency check)
     *
     * @return true if this is the first time processing this message, false if already processed
     */
    boolean markIfAbsent(String messageId, String handler);
}
