package com.marketplace.account;

import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import com.marketplace.testsupport.AccountFixtures;
import com.marketplace.testsupport.AccountFixtures.TestAccount;
import com.marketplace.testsupport.ApplicationTest;
import com.marketplace.testsupport.AuthTestClient;
import com.marketplace.testsupport.TermsFixtures;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/** Terms versions, consents and the terms gate (需求 3.12, 使用者故事 N1). */
@ApplicationTest
class TermsApiIT {

  /** Any seller API; it must not be reachable before the seller accepts the latest terms. */
  private static final String SELLER_API = "/api/v1/seller/products";

  @Autowired MockMvcTester mvc;
  @Autowired AccountFixtures accounts;
  @Autowired AuthTestClient auth;
  @Autowired TermsFixtures terms;

  @Test
  @DisplayName("N1 條款更新：發布新版後，會員須同意新版才能使用需要身分的功能；條款仍可查閱")
  void memberMustAcceptNewVersion() {
    TestAccount member = accounts.member();
    String token = auth.accessToken(member);
    assertThat(auth.get("/api/v1/member/shipping-addresses", token)).hasStatusOk();

    UUID newVersion = terms.publish("TERMS_OF_SERVICE");

    assertThat(auth.get("/api/v1/member/shipping-addresses", token))
        .hasStatus(403)
        .bodyJson()
        .extractingPath("$.code")
        .isEqualTo("TERMS_CONSENT_REQUIRED");
    assertThat(mvc.get().uri("/api/v1/public/terms/TERMS_OF_SERVICE/latest"))
        .hasStatusOk()
        .bodyJson()
        .extractingPath("$.id")
        .isEqualTo(newVersion.toString());

    assertThat(consent("/api/v1/member/terms-consents", token, newVersion)).hasStatus(204);
    assertThat(auth.get("/api/v1/member/shipping-addresses", token)).hasStatusOk();
  }

  @Test
  @DisplayName("N1 舊版保留：營運可查到會員對兩個版本各自的同意時間，舊版內容仍可查閱")
  void earlierVersionsAreKept() {
    UUID first = terms.latest("TERMS_OF_SERVICE");
    TestAccount member = accounts.member();
    UUID second = terms.publish("TERMS_OF_SERVICE");
    assertThat(consent("/api/v1/member/terms-consents", auth.accessToken(member), second))
        .hasStatus(204);
    String opsToken = auth.accessToken(accounts.operator(Set.of("CUSTOMER_SERVICE"), false));

    MvcTestResult consents =
        auth.get("/api/v1/ops/terms-consents?memberId=" + member.id(), opsToken);

    assertThat(consents).hasStatusOk();
    List<Map<String, Object>> records =
        JsonPath.read(AuthTestClient.body(consents), "$[?(@.documentType == 'TERMS_OF_SERVICE')]");
    assertThat(records)
        .extracting(record -> record.get("termsDocumentId"))
        .containsExactly(second.toString(), first.toString());
    assertThat(records).allSatisfy(record -> assertThat(record).containsKey("consentedAt"));
    assertThat(auth.get("/api/v1/ops/terms-documents/" + first, opsToken))
        .hasStatusOk()
        .bodyJson()
        .extractingPath("$.content")
        .asString()
        .contains("TERMS_OF_SERVICE");
  }

  @Test
  @DisplayName("只能同意最新版本、且適用於自己帳號類型的條款：TERMS_NOT_LATEST")
  void onlyLatestApplicableTerms() {
    UUID older = terms.latest("PRIVACY_POLICY");
    terms.publish("PRIVACY_POLICY");
    UUID sellerTerms = terms.latest("SELLER_TERMS");
    String token = auth.accessToken(accounts.member());

    assertThat(consent("/api/v1/member/terms-consents", token, older))
        .hasStatus(422)
        .bodyJson()
        .extractingPath("$.code")
        .isEqualTo("TERMS_NOT_LATEST");
    assertThat(consent("/api/v1/member/terms-consents", token, sellerTerms))
        .hasStatus(422)
        .bodyJson()
        .extractingPath("$.code")
        .isEqualTo("TERMS_NOT_LATEST");
    assertThat(consent("/api/v1/member/terms-consents", token, UUID.randomUUID())).hasStatus(404);
  }

  @Test
  @DisplayName("賣家條款：管理者同意之前，該賣家的所有帳號都無法使用賣家功能；一般成員不能代表賣家同意")
  void sellerTermsAcceptedByAdministrator() {
    UUID sellerId = accounts.seller("THIRD_PARTY");
    TestAccount admin = accounts.sellerUserOf(sellerId, "ADMIN", false);
    TestAccount member = accounts.sellerUserOf(sellerId, "MEMBER", false);
    String adminToken = auth.accessToken(admin);
    String memberToken = auth.accessToken(member);

    UUID newVersion = terms.publish("SELLER_TERMS");

    for (String token : List.of(adminToken, memberToken)) {
      assertThat(auth.get(SELLER_API, token))
          .hasStatus(403)
          .bodyJson()
          .extractingPath("$.code")
          .isEqualTo("TERMS_CONSENT_REQUIRED");
    }
    assertThat(consent("/api/v1/seller/terms-consents", memberToken, newVersion))
        .hasStatus(403)
        .bodyJson()
        .extractingPath("$.code")
        .isEqualTo("FORBIDDEN");

    assertThat(consent("/api/v1/seller/terms-consents", adminToken, newVersion)).hasStatus(204);

    for (String token : List.of(adminToken, memberToken)) {
      assertThat(auth.get(SELLER_API, token).getResponse().getStatus()).isNotEqualTo(403);
    }
    MvcTestResult consents =
        auth.get(
            "/api/v1/ops/terms-consents?sellerId=" + sellerId,
            auth.accessToken(accounts.operator(Set.of("ADMIN"), false)));
    assertThat(consents)
        .bodyJson()
        .hasPathSatisfying(
            "$[?(@.termsDocumentId == '" + newVersion + "')].accountId",
            ids -> assertThat(ids).asList().containsExactly(admin.id().toString()));
  }

  @Test
  @DisplayName("查詢同意紀錄須指定會員或賣家其中之一")
  void consentQueryNeedsOneSubject() {
    String token = auth.accessToken(accounts.operator(Set.of("ADMIN"), false));

    assertThat(auth.get("/api/v1/ops/terms-consents", token))
        .hasStatus(400)
        .bodyJson()
        .extractingPath("$.code")
        .isEqualTo("VALIDATION_FAILED");
  }

  private MvcTestResult consent(String path, String token, UUID documentId) {
    return auth.post(path, token, "{\"termsDocumentId\": \"%s\"}".formatted(documentId));
  }
}
