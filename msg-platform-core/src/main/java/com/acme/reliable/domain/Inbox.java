package com.acme.reliable.domain;

import io.micronaut.data.annotation.*;
import io.micronaut.data.model.DataType;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;
import java.time.Instant;

@MappedEntity("inbox")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Inbox {

    @EmbeddedId
    private InboxId id;

    @DateCreated
    @MappedProperty(value = "processed_at", type = DataType.TIMESTAMP)
    private Instant processedAt;

    @Embeddable
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class InboxId implements Serializable {
        @MappedProperty(value = "message_id", type = DataType.STRING)
        private String messageId;

        @MappedProperty(type = DataType.STRING)
        private String handler;
    }
}
