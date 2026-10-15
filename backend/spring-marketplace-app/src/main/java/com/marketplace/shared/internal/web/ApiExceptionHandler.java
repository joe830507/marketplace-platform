package com.marketplace.shared.internal.web;

import com.marketplace.shared.ApiException;
import com.marketplace.shared.ErrorCode;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/** Turns every failure inside Spring MVC into the platform's Problem Details. */
@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

  private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

  private final Problems problems;

  ApiExceptionHandler(Problems problems) {
    this.problems = problems;
  }

  @ExceptionHandler(ApiException.class)
  ResponseEntity<ProblemDetail> handleApiException(ApiException exception) {
    ProblemDetail problem = problems.create(exception.code(), exception.getMessage());
    exception.properties().forEach(problem::setProperty);
    return ResponseEntity.status(problem.getStatus()).body(problem);
  }

  @ExceptionHandler(AuthenticationException.class)
  ResponseEntity<ProblemDetail> handleAuthentication(AuthenticationException exception) {
    return respond(problems.create(ErrorCode.UNAUTHENTICATED, "請先登入"));
  }

  @ExceptionHandler(AccessDeniedException.class)
  ResponseEntity<ProblemDetail> handleAccessDenied(AccessDeniedException exception) {
    return respond(problems.create(ErrorCode.FORBIDDEN, "沒有執行此操作的權限"));
  }

  @ExceptionHandler(Exception.class)
  ResponseEntity<ProblemDetail> handleUnexpected(Exception exception) {
    log.error("Unexpected error", exception);
    return respond(problems.create(ErrorCode.INTERNAL_ERROR, "系統發生錯誤，請提供 traceId 以便查詢"));
  }

  @Override
  protected @Nullable ResponseEntity<Object> handleMethodArgumentNotValid(
      MethodArgumentNotValidException exception,
      HttpHeaders headers,
      HttpStatusCode status,
      WebRequest request) {
    ProblemDetail problem = exception.getBody();
    List<Map<String, String>> errors =
        exception.getBindingResult().getFieldErrors().stream()
            .map(
                error ->
                    Map.of(
                        "field",
                        error.getField(),
                        "message",
                        String.valueOf(error.getDefaultMessage())))
            .toList();
    problem.setProperty("errors", errors);
    problem.setDetail("請求的欄位不符合規格");
    return handleExceptionInternal(exception, problem, headers, status, request);
  }

  /** Every Spring MVC exception passes through here; adds our code and trace ID. */
  @Override
  protected @Nullable ResponseEntity<Object> handleExceptionInternal(
      Exception exception,
      @Nullable Object body,
      HttpHeaders headers,
      HttpStatusCode status,
      WebRequest request) {
    ProblemDetail problem =
        body instanceof ProblemDetail detail ? detail : ProblemDetail.forStatus(status);
    problems.complete(problem, Problems.codeFor(status));
    return super.handleExceptionInternal(exception, problem, headers, status, request);
  }

  private static ResponseEntity<ProblemDetail> respond(ProblemDetail problem) {
    return ResponseEntity.status(problem.getStatus()).body(problem);
  }
}
