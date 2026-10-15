package com.marketplace.shared.internal.security;

import com.marketplace.shared.ErrorCode;
import com.marketplace.shared.internal.web.Problems;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpHeaders;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

/** Answers requests that security rejects before they reach a controller. */
@Component
public class SecurityProblemHandler implements AuthenticationEntryPoint, AccessDeniedHandler {

  private final Problems problems;

  SecurityProblemHandler(Problems problems) {
    this.problems = problems;
  }

  /** No access token, or one that is invalid or expired. */
  @Override
  public void commence(
      HttpServletRequest request, HttpServletResponse response, AuthenticationException exception)
      throws IOException {
    response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
    problems.write(response, problems.create(ErrorCode.UNAUTHENTICATED, "未登入，或存取權杖無效或已過期"));
  }

  /** A valid token whose account type or roles do not allow the request. */
  @Override
  public void handle(
      HttpServletRequest request, HttpServletResponse response, AccessDeniedException exception)
      throws IOException {
    problems.write(response, problems.create(ErrorCode.FORBIDDEN, "沒有執行此操作的權限"));
  }
}
