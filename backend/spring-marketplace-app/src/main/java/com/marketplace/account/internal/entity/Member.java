package com.marketplace.account.internal.entity;

import com.marketplace.shared.AccountType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.Set;
import java.util.UUID;

/** A buyer's account. Members have no roles and can only access their own data (需求 3.13). */
@Entity
@Table(schema = "account", name = "member")
public class Member extends LoginAccount {

  @Column(nullable = false)
  private String displayName;

  protected Member() {}

  public Member(UUID id, String username, String passwordHash, String displayName) {
    super(id, username, passwordHash);
    this.displayName = displayName;
  }

  @Override
  public AccountType accountType() {
    return AccountType.MEMBER;
  }

  @Override
  public Set<String> roles() {
    return Set.of();
  }

  public String displayName() {
    return displayName;
  }
}
