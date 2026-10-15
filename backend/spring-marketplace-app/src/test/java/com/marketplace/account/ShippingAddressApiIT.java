package com.marketplace.account;

import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import com.marketplace.testsupport.AccountFixtures;
import com.marketplace.testsupport.ApplicationTest;
import com.marketplace.testsupport.AuthTestClient;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/** A member keeps shipping addresses for checkout (需求 3.1, 設計_005 3.2). */
@ApplicationTest
class ShippingAddressApiIT {

  private static final String ADDRESSES = "/api/v1/member/shipping-addresses";

  @Autowired AccountFixtures accounts;
  @Autowired AuthTestClient auth;

  @Test
  @DisplayName("新增、修改、刪除收件資訊；刪除後不再列出")
  void manageAddresses() {
    String token = auth.accessToken(accounts.member());

    MvcTestResult created = auth.post(ADDRESSES, token, address("王小明", true));
    assertThat(created)
        .hasStatus(201)
        .bodyJson()
        .hasPathSatisfying("$.recipientName", name -> assertThat(name).isEqualTo("王小明"))
        .hasPathSatisfying("$.isDefault", isDefault -> assertThat(isDefault).isEqualTo(true));
    String id = JsonPath.read(AuthTestClient.body(created), "$.id");

    assertThat(auth.put(ADDRESSES + "/" + id, token, address("王大明", true)))
        .hasStatusOk()
        .bodyJson()
        .extractingPath("$.recipientName")
        .isEqualTo("王大明");

    assertThat(auth.delete(ADDRESSES + "/" + id, token)).hasStatus(204);
    assertThat(auth.get(ADDRESSES, token))
        .hasStatusOk()
        .bodyJson()
        .extractingPath("$")
        .asList()
        .isEmpty();
  }

  @Test
  @DisplayName("新的預設收件資訊取代原本的預設，清單以預設的排在最前面")
  void newDefaultReplacesTheOld() {
    String token = auth.accessToken(accounts.member());
    String first =
        JsonPath.read(AuthTestClient.body(auth.post(ADDRESSES, token, address("甲", true))), "$.id");
    String second =
        JsonPath.read(AuthTestClient.body(auth.post(ADDRESSES, token, address("乙", true))), "$.id");

    MvcTestResult list = auth.get(ADDRESSES, token);

    assertThat(list).hasStatusOk();
    List<String> ids = JsonPath.read(AuthTestClient.body(list), "$[*].id");
    List<Boolean> defaults = JsonPath.read(AuthTestClient.body(list), "$[*].isDefault");
    assertThat(ids).containsExactly(second, first);
    assertThat(defaults).containsExactly(true, false);
  }

  @Test
  @DisplayName("其他會員的收件資訊：回應查無資料")
  void anotherMembersAddress() {
    String owner = auth.accessToken(accounts.member());
    String other = auth.accessToken(accounts.member());
    String id =
        JsonPath.read(
            AuthTestClient.body(auth.post(ADDRESSES, owner, address("甲", false))), "$.id");

    assertThat(auth.put(ADDRESSES + "/" + id, other, address("乙", false)))
        .hasStatus(404)
        .bodyJson()
        .extractingPath("$.code")
        .isEqualTo("NOT_FOUND");
    assertThat(auth.delete(ADDRESSES + "/" + id, other)).hasStatus(404);
  }

  @Test
  @DisplayName("欄位驗證：收件人不可為空")
  void validation() {
    String token = auth.accessToken(accounts.member());

    assertThat(auth.post(ADDRESSES, token, address("", false)))
        .hasStatus(400)
        .bodyJson()
        .extractingPath("$.code")
        .isEqualTo("VALIDATION_FAILED");
  }

  private static String address(String recipient, boolean isDefault) {
    return """
        {"recipientName": "%s", "phone": "0912345678", "address": "台北市信義區市府路 1 號",
         "isDefault": %s}
        """
        .formatted(recipient, isDefault);
  }
}
