package com.marketplace.account;

import static org.assertj.core.api.Assertions.assertThat;

import com.marketplace.testsupport.AccountFixtures;
import com.marketplace.testsupport.ApplicationTest;
import com.marketplace.testsupport.AuthTestClient;
import com.marketplace.testsupport.TermsFixtures;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiFunction;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/**
 * Each cell of the operator permission table that has an API in this version (需求 3.13, 使用者故事 A2,
 * 設計_006 5.4).
 */
@ApplicationTest
class OpsPermissionApiIT {

  private static final Set<String> ROLES =
      Set.of("ADMIN", "CAMPAIGN", "CUSTOMER_SERVICE", "FINANCE");

  @Autowired AccountFixtures accounts;
  @Autowired AuthTestClient auth;
  @Autowired TermsFixtures terms;

  /** An operation as one of the four roles calls it. */
  private record Operation(
      String name, BiFunction<OpsPermissionApiIT, String, MvcTestResult> call) {
    @Override
    public String toString() {
      return name;
    }
  }

  static Stream<Arguments> administratorOnly() {
    Stream<Operation> operations =
        Stream.of(
            new Operation("建立賣家", (test, token) -> test.createSeller(token)),
            new Operation("發布條款", (test, token) -> test.publishTerms(token)),
            new Operation(
                "查詢營運帳號", (test, token) -> test.auth.get("/api/v1/ops/operator-users", token)),
            new Operation("新增營運帳號", (test, token) -> test.createOperator(token)),
            new Operation("設定營運帳號的角色", (test, token) -> test.setRoles(token)));
    return operations.flatMap(
        operation -> ROLES.stream().sorted().map(role -> Arguments.of(operation, role)));
  }

  static Stream<Arguments> everyRole() {
    Stream<Operation> operations =
        Stream.of(
            new Operation("查詢賣家列表", (test, token) -> test.auth.get("/api/v1/ops/sellers", token)),
            new Operation(
                "查詢賣家詳情",
                (test, token) ->
                    test.auth.get(
                        "/api/v1/ops/sellers/" + test.accounts.seller("FIRST_PARTY"), token)),
            new Operation(
                "查詢條款內容",
                (test, token) ->
                    test.auth.get(
                        "/api/v1/ops/terms-documents/" + test.terms.latest("SELLER_TERMS"),
                        token)));
    return operations.flatMap(
        operation -> ROLES.stream().sorted().map(role -> Arguments.of(operation, role)));
  }

  @ParameterizedTest(name = "A2 {0}：{1}")
  @MethodSource("administratorOnly")
  void onlyAdministrators(Operation operation, String role) {
    MvcTestResult result = operation.call().apply(this, tokenWithRole(role));

    if (role.equals("ADMIN")) {
      assertThat(result.getResponse().getStatus()).isBetween(200, 299);
    } else {
      assertThat(result).hasStatus(403).bodyJson().extractingPath("$.code").isEqualTo("FORBIDDEN");
    }
  }

  @ParameterizedTest(name = "A2 {0}：{1}")
  @MethodSource("everyRole")
  void everyOperatorRole(Operation operation, String role) {
    assertThat(operation.call().apply(this, tokenWithRole(role))).hasStatusOk();
  }

  private String tokenWithRole(String role) {
    return auth.accessToken(accounts.operator(Set.of(role), false));
  }

  private MvcTestResult createSeller(String token) {
    return auth.post(
        "/api/v1/ops/sellers",
        token,
        """
        {"name": "自營", "sellerType": "FIRST_PARTY", "adminUsername": "%s",
         "adminInitialPassword": "Initial-1234"}
        """
            .formatted(uniqueUsername()));
  }

  private MvcTestResult publishTerms(String token) {
    return auth.post(
        "/api/v1/ops/terms-documents",
        token,
        "{\"documentType\": \"SELLER_TERMS\", \"content\": \"賣家條款\"}");
  }

  private MvcTestResult createOperator(String token) {
    return auth.post(
        "/api/v1/ops/operator-users",
        token,
        """
        {"username": "%s", "initialPassword": "Initial-1234", "roles": ["FINANCE"]}
        """
            .formatted(uniqueUsername()));
  }

  private MvcTestResult setRoles(String token) {
    UUID target = accounts.operator(Set.of("CAMPAIGN"), false).id();
    return auth.put(
        "/api/v1/ops/operator-users/" + target + "/roles", token, "{\"roles\": [\"FINANCE\"]}");
  }

  private static String uniqueUsername() {
    return "perm-" + UUID.randomUUID().toString().substring(0, 13);
  }
}
