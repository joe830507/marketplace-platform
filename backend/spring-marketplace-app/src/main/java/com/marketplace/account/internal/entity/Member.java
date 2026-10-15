package com.marketplace.account.internal.entity;

import com.marketplace.shared.AccountType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.Set;

/** A buyer's account. Members have no roles and can only access their own data (需求 3.13). */
@Entity
@Table(schema = "account", name = "member")
public class Member extends LoginAccount {

  @Column(nullable = false)
  private String displayName;

  protected Member() {}

  @Override
  public AccountType accountType() {
    return AccountType.MEMBER;
  }

  @Override
  public Set<String> roles() {
    return Set.of();
  }
}
