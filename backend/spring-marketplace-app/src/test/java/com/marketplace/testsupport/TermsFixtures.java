package com.marketplace.testsupport;

import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import java.util.Set;
import java.util.UUID;
import org.springframework.boot.test.context.TestComponent;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/**
 * Publishes terms through the operator API, the way an operator administrator does (需求 3.12).
 *
 * <p>Publishing a new version makes every existing member or seller accept it before using the API
 * again; accounts created afterwards by {@link AccountFixtures} accept it at once.
 */
@TestComponent
public class TermsFixtures {

  private final AccountFixtures accounts;
  private final AuthTestClient auth;
  private final MockMvcTester mvc;

  TermsFixtures(AccountFixtures accounts, AuthTestClient auth, MockMvcTester mvc) {
    this.accounts = accounts;
    this.auth = auth;
    this.mvc = mvc;
  }

  /** Publishes a new version of the document type and returns its ID. */
  public UUID publish(String documentType) {
    String token = auth.accessToken(accounts.operator(Set.of("ADMIN"), false));
    MvcTestResult result =
        auth.post(
            "/api/v1/ops/terms-documents",
            token,
            """
            {"documentType": "%s", "content": "%s 測試內容 %s"}
            """
                .formatted(documentType, documentType, UUID.randomUUID()));
    assertThat(result).hasStatus(201);
    return UUID.fromString(JsonPath.read(AuthTestClient.body(result), "$.id"));
  }

  /** The latest version of the document type, publishing the first one if there is none. */
  public UUID latest(String documentType) {
    MvcTestResult result =
        mvc.get().uri("/api/v1/public/terms/" + documentType + "/latest").exchange();
    if (result.getResponse().getStatus() == 404) {
      return publish(documentType);
    }
    assertThat(result).hasStatusOk();
    return UUID.fromString(JsonPath.read(AuthTestClient.body(result), "$.id"));
  }
}
