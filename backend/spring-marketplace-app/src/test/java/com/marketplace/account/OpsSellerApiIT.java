package com.marketplace.account;

import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import com.marketplace.shared.AccountType;
import com.marketplace.testsupport.AccountFixtures;
import com.marketplace.testsupport.ApplicationTest;
import com.marketplace.testsupport.AuthTestClient;
import com.marketplace.testsupport.AuthTestClient.Tokens;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/** Operators create sellers with their first administrator (需求 3.2, 設計_005 3.4). */
@ApplicationTest
class OpsSellerApiIT {

  private static final String SELLERS = "/api/v1/ops/sellers";

  @Autowired AccountFixtures accounts;
  @Autowired AuthTestClient auth;
  @Autowired JdbcClient jdbc;

  private String adminToken;

  @BeforeEach
  void logIn() {
    adminToken = auth.accessToken(accounts.operator(Set.of("ADMIN"), false));
  }

  @Test
  @DisplayName("建立外部賣家：帶抽成比例；第一個管理者帳號以初始密碼登入後須先修改密碼；留下稽核紀錄")
  void createThirdPartySeller() {
    String adminUsername = uniqueUsername();

    MvcTestResult created =
        auth.post(SELLERS, adminToken, seller("好物商行", "THIRD_PARTY", adminUsername, 1000));

    assertThat(created)
        .hasStatus(201)
        .bodyJson()
        .hasPathSatisfying("$.name", name -> assertThat(name).isEqualTo("好物商行"))
        .hasPathSatisfying("$.sellerType", type -> assertThat(type).isEqualTo("THIRD_PARTY"))
        .hasPathSatisfying("$.status", status -> assertThat(status).isEqualTo("ACTIVE"));
    String sellerId = JsonPath.read(AuthTestClient.body(created), "$.id");

    Tokens tokens =
        AuthTestClient.tokens(auth.login(AccountType.SELLER_USER, adminUsername, "Initial-1234"));
    assertThat(auth.get("/api/v1/seller/products", tokens.accessToken()))
        .hasStatus(403)
        .bodyJson()
        .extractingPath("$.code")
        .isEqualTo("PASSWORD_CHANGE_REQUIRED");

    List<Map<String, Object>> audit =
        jdbc.sql(
                """
                SELECT action, after::text AS after FROM shared.audit_log
                WHERE target_id = :sellerId AND target_type = 'SELLER'
                """)
            .param("sellerId", sellerId)
            .query()
            .listOfRows();
    assertThat(audit)
        .singleElement()
        .satisfies(
            row -> {
              assertThat(row).containsEntry("action", "CREATE_SELLER");
              assertThat((String) row.get("after")).contains("\"commissionRate\": 1000");
            });
  }

  @Test
  @DisplayName("平台自營賣家不設定抽成比例：COMMISSION_NOT_APPLICABLE，不建立任何資料")
  void firstPartyWithCommission() {
    String adminUsername = uniqueUsername();

    assertThat(auth.post(SELLERS, adminToken, seller("平台自營", "FIRST_PARTY", adminUsername, 500)))
        .hasStatus(422)
        .bodyJson()
        .extractingPath("$.code")
        .isEqualTo("COMMISSION_NOT_APPLICABLE");
    assertThat(sellerUserCount(adminUsername)).isZero();
  }

  @Test
  @DisplayName("外部賣家沒有抽成比例：VALIDATION_FAILED")
  void thirdPartyWithoutCommission() {
    assertThat(auth.post(SELLERS, adminToken, seller("外部", "THIRD_PARTY", uniqueUsername(), null)))
        .hasStatus(400)
        .bodyJson()
        .hasPathSatisfying("$.code", code -> assertThat(code).isEqualTo("VALIDATION_FAILED"))
        .hasPathSatisfying(
            "$.errors[0].field", field -> assertThat(field).isEqualTo("commissionRate"));
  }

  @Test
  @DisplayName("管理者帳號名稱已被使用：USERNAME_TAKEN，不建立賣家")
  void adminUsernameTaken() {
    String adminUsername = uniqueUsername();
    assertThat(auth.post(SELLERS, adminToken, seller("甲", "FIRST_PARTY", adminUsername, null)))
        .hasStatus(201);

    assertThat(auth.post(SELLERS, adminToken, seller("乙", "FIRST_PARTY", adminUsername, null)))
        .hasStatus(409)
        .bodyJson()
        .extractingPath("$.code")
        .isEqualTo("USERNAME_TAKEN");
  }

  @Test
  @DisplayName("賣家列表依關鍵字篩選、由新到舊；賣家詳情；不存在的賣家回應 404")
  void listAndGet() {
    String keyword = "kw" + UUID.randomUUID().toString().substring(0, 8);
    String older =
        JsonPath.read(
            AuthTestClient.body(
                auth.post(
                    SELLERS,
                    adminToken,
                    seller(keyword + " 一", "FIRST_PARTY", uniqueUsername(), null))),
            "$.id");
    String newer =
        JsonPath.read(
            AuthTestClient.body(
                auth.post(
                    SELLERS,
                    adminToken,
                    seller(keyword + " 二", "FIRST_PARTY", uniqueUsername(), null))),
            "$.id");
    String viewer = auth.accessToken(accounts.operator(Set.of("FINANCE"), false));

    MvcTestResult list = auth.get(SELLERS + "?keyword=" + keyword.toUpperCase(), viewer);

    assertThat(list)
        .hasStatusOk()
        .bodyJson()
        .hasPathSatisfying(
            "$.items[*].id", ids -> assertThat(ids).asList().containsExactly(newer, older))
        .hasPathSatisfying("$.page.totalItems", total -> assertThat(total).isEqualTo(2))
        .hasPathSatisfying("$.page.size", size -> assertThat(size).isEqualTo(20));
    assertThat(auth.get(SELLERS + "/" + older, viewer)).hasStatusOk();
    assertThat(auth.get(SELLERS + "/" + UUID.randomUUID(), viewer)).hasStatus(404);
  }

  @Test
  @DisplayName("不支援的每頁筆數：VALIDATION_FAILED")
  void unsupportedPageSize() {
    assertThat(auth.get(SELLERS + "?size=30", adminToken))
        .hasStatus(400)
        .bodyJson()
        .extractingPath("$.code")
        .isEqualTo("VALIDATION_FAILED");
  }

  private long sellerUserCount(String username) {
    return jdbc.sql("SELECT count(*) FROM account.seller_user WHERE username = :username")
        .param("username", username)
        .query(Long.class)
        .single();
  }

  private static String seller(
      String name, String type, String adminUsername, Integer commissionRate) {
    String rate = commissionRate == null ? "" : ", \"commissionRate\": " + commissionRate;
    return """
        {"name": "%s", "sellerType": "%s", "adminUsername": "%s",
         "adminInitialPassword": "Initial-1234"%s}
        """
        .formatted(name, type, adminUsername, rate);
  }

  private static String uniqueUsername() {
    return "seller-" + UUID.randomUUID().toString().substring(0, 13);
  }
}
