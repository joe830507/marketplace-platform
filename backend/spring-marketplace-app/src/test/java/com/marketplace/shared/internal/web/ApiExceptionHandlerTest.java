package com.marketplace.shared.internal.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.marketplace.shared.internal.support.TraceIds;
import io.micrometer.tracing.Tracer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import tools.jackson.databind.json.JsonMapper;

class ApiExceptionHandlerTest {

  private final ApiExceptionHandler handler =
      new ApiExceptionHandler(new Problems(new TraceIds(Tracer.NOOP), new JsonMapper()));

  @Test
  @DisplayName("未預期的例外：INTERNAL_ERROR，不透露例外內容")
  void unexpectedException() {
    ResponseEntity<ProblemDetail> response =
        handler.handleUnexpected(new IllegalStateException("database password is hunter2"));

    assertThat(response.getStatusCode().value()).isEqualTo(500);
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().getProperties()).containsEntry("code", "INTERNAL_ERROR");
    assertThat(response.getBody().getDetail()).doesNotContain("hunter2");
  }
}
