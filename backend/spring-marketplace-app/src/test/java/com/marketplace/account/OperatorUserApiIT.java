package com.marketplace.account;

import static org.assertj.core.api.Assertions.assertThat;

import com.marketplace.shared.AccountType;
import com.marketplace.testsupport.AccountFixtures;
import com.marketplace.testsupport.AccountFixtures.TestAccount;
import com.marketplace.testsupport.ApplicationTest;
import com.marketplace.testsupport.AuthTestClient;
import com.marketplace.testsupport.AuthTestClient.Tokens;
import com.marketplace.testsupport.MutableClock;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/** Operator administrators manage operator accounts and roles (需求 3.13, 使用者故事 A2). */
@ApplicationTest
class OperatorUserApiIT {

  private static final String OPERATORS = "/api/v1/ops/operator-users";

  @Autowired AccountFixtures accounts;
  @Autowired AuthTestClient auth;
  @Autowired JdbcClient jdbc;
  @Autowired MutableClock clock;

  private String adminToken;

  @BeforeEach
  void logIn() {
    adminToken = auth.accessToken(accounts.operator(Set.of("ADMIN"), false));
  }

  @Test
  @DisplayName("新增營運帳號：可有多個角色，以初始密碼登入後須先修改密碼")
  void createOperator() {
    String username = "ops-" + UUID.randomUUID().toString().substring(0, 13);

    MvcTestResult created =
        auth.post(
            OPERATORS,
            adminToken,
            """
            {"username": "%s", "initialPassword": "Initial-1234",
             "roles": ["FINANCE", "CAMPAIGN"]}
            """
                .formatted(username));

    assertThat(created)
        .hasStatus(201)
        .bodyJson()
        .hasPathSatisfying(
            "$.roles", roles -> assertThat(roles).asList().containsExactly("CAMPAIGN", "FINANCE"));
    Tokens tokens =
        AuthTestClient.tokens(auth.login(AccountType.OPERATOR, username, "Initial-1234"));
    assertThat(auth.get("/api/v1/ops/sellers", tokens.accessToken()))
        .hasStatus(403)
        .bodyJson()
        .extractingPath("$.code")
        .isEqualTo("PASSWORD_CHANGE_REQUIRED");
  }

  @Test
  @DisplayName("帳號名稱已被使用：USERNAME_TAKEN")
  void usernameTaken() {
    TestAccount existing = accounts.operator(Set.of("FINANCE"), false);

    assertThat(
            auth.post(
                OPERATORS,
                adminToken,
                """
                {"username": "%s", "initialPassword": "Initial-1234", "roles": ["FINANCE"]}
                """
                    .formatted(existing.username())))
        .hasStatus(409)
        .bodyJson()
        .extractingPath("$.code")
        .isEqualTo("USERNAME_TAKEN");
  }

  @Test
  @DisplayName("設定角色：留下變更前後的稽核紀錄，保留的角色維持原本的指派紀錄")
  void setRoles() {
    TestAccount operator = accounts.operator(Set.of("ADMIN", "FINANCE"), false);
    String financeAssignedAt = assignedAt(operator.id(), "FINANCE");
    clock.advance(Duration.ofSeconds(1));

    assertThat(setRoles(operator.id(), "[\"FINANCE\", \"CUSTOMER_SERVICE\"]"))
        .hasStatusOk()
        .bodyJson()
        .extractingPath("$.roles")
        .asList()
        .containsExactly("CUSTOMER_SERVICE", "FINANCE");

    assertThat(assignedAt(operator.id(), "FINANCE")).isEqualTo(financeAssignedAt);
    Map<String, Object> audit =
        jdbc.sql(
                """
                SELECT before::text AS before, after::text AS after FROM shared.audit_log
                WHERE action = 'SET_OPERATOR_ROLES' AND target_id = :id
                """)
            .param("id", operator.id().toString())
            .query()
            .singleRow();
    assertThat(audit)
        .containsEntry("before", "{\"roles\": [\"ADMIN\", \"FINANCE\"]}")
        .containsEntry("after", "{\"roles\": [\"CUSTOMER_SERVICE\", \"FINANCE\"]}");
  }

  @Test
  @DisplayName("A2 角色被移除：更新權杖立即撤銷，最晚 15 分鐘後失去權限")
  void removedRoleExpiresWithTheAccessToken() {
    TestAccount operator = accounts.operator(Set.of("ADMIN", "FINANCE"), false);
    Tokens tokens = auth.loginOk(operator);
    assertThat(createSeller(tokens.accessToken())).hasStatus(201);

    assertThat(setRoles(operator.id(), "[\"FINANCE\"]")).hasStatusOk();

    assertThat(auth.refresh(AccountType.OPERATOR, tokens.refreshToken())).hasStatus(401);
    clock.advance(Duration.ofMinutes(15).plusSeconds(1));
    assertThat(createSeller(tokens.accessToken())).hasStatus(401);
    String newToken = auth.accessToken(operator);
    assertThat(createSeller(newToken))
        .hasStatus(403)
        .bodyJson()
        .extractingPath("$.code")
        .isEqualTo("FORBIDDEN");
  }

  @Test
  @DisplayName("設定不存在的營運帳號：NOT_FOUND")
  void unknownOperator() {
    assertThat(setRoles(UUID.randomUUID(), "[\"FINANCE\"]")).hasStatus(404);
  }

  private MvcTestResult setRoles(UUID operatorId, String roles) {
    return auth.put(
        OPERATORS + "/" + operatorId + "/roles", adminToken, "{\"roles\": %s}".formatted(roles));
  }

  private MvcTestResult createSeller(String token) {
    return auth.post(
        "/api/v1/ops/sellers",
        token,
        """
        {"name": "自營", "sellerType": "FIRST_PARTY", "adminUsername": "%s",
         "adminInitialPassword": "Initial-1234"}
        """
            .formatted("seller-" + UUID.randomUUID().toString().substring(0, 13)));
  }

  private String assignedAt(UUID operatorId, String role) {
    return jdbc.sql(
            """
            SELECT created_at::text FROM account.operator_user_role
            WHERE operator_user_id = :id AND role = :role
            """)
        .param("id", operatorId)
        .param("role", role)
        .query(String.class)
        .single();
  }
}
