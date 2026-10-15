package com.marketplace.shared.internal.support;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

/** Reads the trace ID of the current request, for error responses and audit records. */
@Component
public class TraceIds {

  private final Tracer tracer;

  public TraceIds(Tracer tracer) {
    this.tracer = tracer;
  }

  public @Nullable String current() {
    Span span = tracer.currentSpan();
    if (span == null) {
      return null;
    }
    String traceId = span.context().traceId();
    return traceId.isEmpty() ? null : traceId;
  }
}
