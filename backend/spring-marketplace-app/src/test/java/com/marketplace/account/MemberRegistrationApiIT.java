package com.marketplace.account;

import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import com.marketplace.shared.AccountType;
import com.marketplace.testsupport.ApplicationTest;
import com.marketplace.testsupport.AuthTestClient;
import com.marketplace.testsupport.TermsFixtures;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/** Members register by accepting the latest terms (需求 3.12, 使用者故事 N1). */
@ApplicationTest
class MemberRegistrationApiIT {

  private static final String PASSWORD = "Register-1234";

  @Autowired MockMvcTester mvc;
  @Autowired AuthTestClient auth;
  @Autowired TermsFixtures terms;
  @Autowired JdbcClient jdbc;

  @Test
  @DisplayName("N1 註冊：同意服務條款與隱私權政策，留下會員、文件、版本、時間的同意紀錄")
  void register() {
    UUID termsOfService = terms.latest("TERMS_OF_SERVICE");
    UUID privacyPolicy = terms.latest("PRIVACY_POLICY");
    String username = uniqueUsername();

    MvcTestResult result = register(username, List.of(termsOfService, privacyPolicy));

    assertThat(result)
        .hasStatus(201)
        .bodyJson()
        .hasPathSatisfying("$.username", value -> assertThat(value).isEqualTo(username))
        .hasPathSatisfying("$.displayName", value -> assertThat(value).isEqualTo("新會員"));
    UUID memberId = UUID.fromString(JsonPath.read(AuthTestClient.body(result), "$.id"));
    assertThat(
            jdbc.sql(
                    """
                    SELECT terms_document_id FROM account.terms_consent
                    WHERE account_type = 'MEMBER' AND account_id = :id AND consented_at IS NOT NULL
                    """)
                .param("id", memberId)
                .query(UUID.class)
                .list())
        .containsExactlyInAnyOrder(termsOfService, privacyPolicy);

    String token =
        AuthTestClient.tokens(auth.login(AccountType.MEMBER, username, PASSWORD)).accessToken();
    assertThat(auth.get("/api/v1/member/shipping-addresses", token)).hasStatusOk();
  }

  @Test
  @DisplayName("N1 未同意：沒有勾選條款即送出註冊，註冊失敗")
  void withoutConsent() {
    String username = uniqueUsername();

    assertThat(register(username, List.of()))
        .hasStatus(400)
        .bodyJson()
        .extractingPath("$.code")
        .isEqualTo("VALIDATION_FAILED");
    assertThat(memberCount(username)).isZero();
  }

  @Test
  @DisplayName("同意的不是最新版本：TERMS_NOT_LATEST，不建立帳號")
  void olderVersion() {
    UUID older = terms.latest("TERMS_OF_SERVICE");
    terms.publish("TERMS_OF_SERVICE");
    String username = uniqueUsername();

    assertThat(register(username, List.of(older, terms.latest("PRIVACY_POLICY"))))
        .hasStatus(422)
        .bodyJson()
        .extractingPath("$.code")
        .isEqualTo("TERMS_NOT_LATEST");
    assertThat(memberCount(username)).isZero();
  }

  @Test
  @DisplayName("帳號名稱已被使用（不分大小寫）：USERNAME_TAKEN")
  void usernameTaken() {
    List<UUID> latest = List.of(terms.latest("TERMS_OF_SERVICE"), terms.latest("PRIVACY_POLICY"));
    String username = uniqueUsername();
    assertThat(register(username, latest)).hasStatus(201);

    assertThat(register(username.toUpperCase(), latest))
        .hasStatus(409)
        .bodyJson()
        .extractingPath("$.code")
        .isEqualTo("USERNAME_TAKEN");
  }

  private MvcTestResult register(String username, List<UUID> consented) {
    String ids = String.join(", ", consented.stream().map(id -> "\"" + id + "\"").toList());
    return mvc.post()
        .uri("/api/v1/member/auth/register")
        .contentType(MediaType.APPLICATION_JSON)
        .content(
            """
            {"username": "%s", "password": "%s", "displayName": "新會員",
             "consentedTermsDocumentIds": [%s]}
            """
                .formatted(username, PASSWORD, ids))
        .exchange();
  }

  private long memberCount(String username) {
    return jdbc.sql("SELECT count(*) FROM account.member WHERE lower(username) = lower(:username)")
        .param("username", username)
        .query(Long.class)
        .single();
  }

  private static String uniqueUsername() {
    return "reg-" + UUID.randomUUID().toString().substring(0, 13);
  }
}
