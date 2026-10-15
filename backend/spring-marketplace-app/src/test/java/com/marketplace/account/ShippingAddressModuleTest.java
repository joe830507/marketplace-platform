package com.marketplace.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.marketplace.account.internal.service.ShippingAddressService;
import com.marketplace.account.internal.service.ShippingAddressService.AddressInput;
import com.marketplace.account.internal.service.ShippingAddressService.AddressView;
import com.marketplace.testsupport.AccountFixtures;
import com.marketplace.testsupport.ApplicationTest;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;

/** Each member has at most one default shipping address (設計_004 3). */
@ApplicationTest
class ShippingAddressModuleTest {

  @Autowired ShippingAddressService addresses;
  @Autowired AccountFixtures accounts;
  @Autowired JdbcClient jdbc;

  @Test
  @DisplayName("資料庫拒絕同一會員的第二筆預設收件資訊")
  void databaseAllowsOneDefault() {
    UUID memberId = accounts.member().id();
    insert(memberId, true);

    assertThatThrownBy(() -> insert(memberId, true))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  @DisplayName("刪除預設收件資訊後，可以再設定另一筆為預設")
  void anotherDefaultAfterDeletion() {
    UUID memberId = accounts.member().id();
    AddressView first = addresses.create(memberId, input(true));
    AddressView second = addresses.create(memberId, input(false));

    addresses.delete(memberId, first.id());
    addresses.update(memberId, second.id(), input(true));

    assertThat(addresses.list(memberId))
        .singleElement()
        .satisfies(
            address -> {
              assertThat(address.id()).isEqualTo(second.id());
              assertThat(address.isDefault()).isTrue();
            });
    assertThat(defaultCount(memberId)).isEqualTo(1);
  }

  private static AddressInput input(boolean isDefault) {
    return new AddressInput("王小明", "0912345678", "台北市信義區市府路 1 號", isDefault);
  }

  private void insert(UUID memberId, boolean isDefault) {
    jdbc.sql(
            """
            INSERT INTO account.shipping_address (id, member_id, recipient_name, phone, address,
              is_default, created_at, created_by, updated_at, updated_by, version)
            VALUES (:id, :memberId, 'x', 'x', 'x', :isDefault, :now, 'TEST', :now, 'TEST', 0)
            """)
        .param("id", UUID.randomUUID())
        .param("memberId", memberId)
        .param("isDefault", isDefault)
        .param("now", Instant.EPOCH.atOffset(ZoneOffset.UTC))
        .update();
  }

  private long defaultCount(UUID memberId) {
    return jdbc.sql(
            """
            SELECT count(*) FROM account.shipping_address
            WHERE member_id = :memberId AND is_default AND deleted_at IS NULL
            """)
        .param("memberId", memberId)
        .query(Long.class)
        .single();
  }
}
