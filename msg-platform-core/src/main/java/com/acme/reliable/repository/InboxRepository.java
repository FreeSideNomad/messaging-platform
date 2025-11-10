package com.acme.reliable.repository;

/**
 * Repository for Inbox pattern - deduplication of incoming messages
 */
public interface InboxRepository {

    /**
     * Insert a message into the inbox if it doesn't already exist (idempotency check)
     *
     * @param messageId unique message identifier
     * @param handler   the handler processing this message
     * @return number of rows inserted (1 if inserted, 0 if duplicate)
     */
    int insertIfAbsent(String messageId, String handler);
}
