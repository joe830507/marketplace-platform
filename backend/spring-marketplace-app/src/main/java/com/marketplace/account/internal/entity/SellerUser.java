package com.marketplace.account.internal.entity;

import com.marketplace.shared.AccountType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.util.Set;
import java.util.UUID;

/** An account that works for one seller, with exactly one role (需求 3.13). */
@Entity
@Table(schema = "account", name = "seller_user")
public class SellerUser extends LoginAccount {

  enum Role {
    ADMIN,
    MEMBER
  }

  @Column(nullable = false)
  private UUID sellerId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private Role role;

  @Column(nullable = false)
  private boolean mustChangePassword;

  protected SellerUser() {}

  @Override
  public AccountType accountType() {
    return AccountType.SELLER_USER;
  }

  @Override
  public Set<String> roles() {
    return Set.of(role.name());
  }

  @Override
  public UUID sellerId() {
    return sellerId;
  }

  @Override
  public boolean mustChangePassword() {
    return mustChangePassword;
  }

  @Override
  public void changePassword(String newPasswordHash) {
    super.changePassword(newPasswordHash);
    this.mustChangePassword = false;
  }
}
