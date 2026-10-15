package com.marketplace.shared;

import static org.assertj.core.api.Assertions.assertThat;

import com.marketplace.testsupport.ApplicationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/** Every error is Problem Details with the platform's code and the trace ID (設計_005 2.4). */
@ApplicationTest
class ErrorResponseApiIT {

  private static final String LOGIN = "/api/v1/member/auth/login";

  @Autowired MockMvcTester mvc;

  @Test
  @DisplayName("欄位驗證錯誤：VALIDATION_FAILED，並列出每個欄位的錯誤")
  void validationFailure() {
    MvcTestResult result =
        mvc.post().uri(LOGIN).contentType(MediaType.APPLICATION_JSON).content("{}").exchange();

    assertThat(result)
        .hasStatus(400)
        .hasContentType(MediaType.APPLICATION_PROBLEM_JSON)
        .bodyJson()
        .hasPathSatisfying(
            "$.type",
            type ->
                assertThat(type).isEqualTo("https://marketplace.example/errors/validation-failed"))
        .hasPathSatisfying("$.title", title -> assertThat(title).isEqualTo("欄位驗證錯誤"))
        .hasPathSatisfying("$.status", status -> assertThat(status).isEqualTo(400))
        .hasPathSatisfying("$.code", code -> assertThat(code).isEqualTo("VALIDATION_FAILED"))
        .hasPathSatisfying(
            "$.traceId", traceId -> assertThat(traceId).asString().matches("[0-9a-f]{32}"))
        .hasPathSatisfying(
            "$.errors[*].field",
            fields ->
                assertThat(fields).asList().containsExactlyInAnyOrder("username", "password"));
  }

  @Test
  @DisplayName("無法解析的請求內容：VALIDATION_FAILED")
  void unreadableBody() {
    MvcTestResult result =
        mvc.post().uri(LOGIN).contentType(MediaType.APPLICATION_JSON).content("{").exchange();

    assertThat(result)
        .hasStatus(400)
        .bodyJson()
        .extractingPath("$.code")
        .isEqualTo("VALIDATION_FAILED");
  }

  @Test
  @DisplayName("沒有存取權杖：UNAUTHENTICATED，並帶 WWW-Authenticate 標頭")
  void missingAccessToken() {
    MvcTestResult result = mvc.post().uri("/api/v1/member/auth/password").exchange();

    assertThat(result)
        .hasStatus(401)
        .hasHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer")
        .hasContentType(MediaType.APPLICATION_PROBLEM_JSON)
        .bodyJson()
        .hasPathSatisfying("$.code", code -> assertThat(code).isEqualTo("UNAUTHENTICATED"))
        .hasPathSatisfying(
            "$.traceId", traceId -> assertThat(traceId).asString().matches("[0-9a-f]{32}"));
  }

  @Test
  @DisplayName("無效的存取權杖：UNAUTHENTICATED")
  void invalidAccessToken() {
    MvcTestResult result =
        mvc.post()
            .uri("/api/v1/member/auth/password")
            .header(HttpHeaders.AUTHORIZATION, "Bearer not-a-jwt")
            .exchange();

    assertThat(result)
        .hasStatus(401)
        .bodyJson()
        .extractingPath("$.code")
        .isEqualTo("UNAUTHENTICATED");
  }

  @Test
  @DisplayName("不存在的路徑：NOT_FOUND")
  void unknownPath() {
    MvcTestResult result = mvc.get().uri("/api/v1/public/no-such-api").exchange();

    assertThat(result).hasStatus(404).bodyJson().extractingPath("$.code").isEqualTo("NOT_FOUND");
  }

  @Test
  @DisplayName("不支援的媒體類型：UNSUPPORTED_REQUEST")
  void unsupportedMediaType() {
    MvcTestResult result =
        mvc.post().uri(LOGIN).contentType(MediaType.TEXT_PLAIN).content("hello").exchange();

    assertThat(result)
        .hasStatus(415)
        .bodyJson()
        .extractingPath("$.code")
        .isEqualTo("UNSUPPORTED_REQUEST");
  }

  @Test
  @DisplayName("請求帶有 traceparent 時，沿用呼叫端的 trace ID")
  void continuesTheCallersTrace() {
    String traceId = "4bf92f3577b34da6a3ce929d0e0e4736";

    MvcTestResult result =
        mvc.post()
            .uri(LOGIN)
            .header("traceparent", "00-" + traceId + "-00f067aa0ba902b7-01")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{}")
            .exchange();

    assertThat(result).bodyJson().extractingPath("$.traceId").isEqualTo(traceId);
  }
}
