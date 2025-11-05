package com.acme.reliable.domain;

import java.io.Serializable;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Inbox domain entity (pure domain object, no persistence annotations). */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Inbox {

  private InboxId id;
  private Instant processedAt;

  @Getter
  @Setter
  @NoArgsConstructor
  @AllArgsConstructor
  public static class InboxId implements Serializable {
    private String messageId;
    private String handler;
  }
}
