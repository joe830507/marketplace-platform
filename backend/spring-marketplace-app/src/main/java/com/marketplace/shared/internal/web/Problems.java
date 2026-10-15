package com.marketplace.shared.internal.web;

import com.marketplace.shared.ErrorCode;
import com.marketplace.shared.internal.support.TraceIds;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * Builds the platform's error responses: RFC 9457 Problem Details plus {@code code} and {@code
 * traceId} (設計_005 2.4).
 */
@Component
public class Problems {

  private final TraceIds traceIds;
  private final JsonMapper jsonMapper;

  Problems(TraceIds traceIds, JsonMapper jsonMapper) {
    this.traceIds = traceIds;
    this.jsonMapper = jsonMapper;
  }

  public ProblemDetail create(ErrorCode code, String detail) {
    return complete(ProblemDetail.forStatusAndDetail(code.status(), detail), code);
  }

  /** Adds the platform's members to a problem that Spring or our code already built. */
  ProblemDetail complete(ProblemDetail problem, ErrorCode code) {
    problem.setType(code.type());
    problem.setTitle(code.title());
    problem.setProperty("code", code.name());
    String traceId = traceIds.current();
    if (traceId != null) {
      problem.setProperty("traceId", traceId);
    }
    return problem;
  }

  /** The error code for a failure that Spring reports only with a status. */
  static ErrorCode codeFor(HttpStatusCode status) {
    return switch (status.value()) {
      case 400 -> ErrorCode.VALIDATION_FAILED;
      case 401 -> ErrorCode.UNAUTHENTICATED;
      case 403 -> ErrorCode.FORBIDDEN;
      case 404 -> ErrorCode.NOT_FOUND;
      default ->
          status.is5xxServerError() ? ErrorCode.INTERNAL_ERROR : ErrorCode.UNSUPPORTED_REQUEST;
    };
  }

  /** Writes a problem from a servlet filter, where Spring MVC's message converters do not run. */
  public void write(HttpServletResponse response, ProblemDetail problem) throws IOException {
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("type", problem.getType().toString());
    body.put("title", problem.getTitle());
    body.put("status", problem.getStatus());
    if (problem.getDetail() != null) {
      body.put("detail", problem.getDetail());
    }
    if (problem.getProperties() != null) {
      body.putAll(problem.getProperties());
    }
    response.setStatus(problem.getStatus());
    response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
    jsonMapper.writeValue(response.getOutputStream(), body);
  }
}
