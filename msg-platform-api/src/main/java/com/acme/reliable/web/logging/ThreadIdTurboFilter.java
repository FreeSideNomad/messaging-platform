package com.acme.reliable.web.logging;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.turbo.TurboFilter;
import ch.qos.logback.core.spi.FilterReply;
import org.slf4j.MDC;
import org.slf4j.Marker;

/** Injects current thread id into MDC so log pattern can include it. */
public class ThreadIdTurboFilter extends TurboFilter {
  private static final String THREAD_ID_KEY = "threadId";

  @Override
  public FilterReply decide(
      Marker marker, Logger logger, Level level, String format, Object[] params, Throwable t) {
    MDC.put(THREAD_ID_KEY, String.valueOf(Thread.currentThread().getId()));
    return FilterReply.NEUTRAL;
  }
}
