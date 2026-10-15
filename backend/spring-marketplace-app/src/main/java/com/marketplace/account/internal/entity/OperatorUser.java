package com.marketplace.account.internal.entity;

import com.marketplace.shared.AccountType;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Embeddable;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

/** A platform operator's account; it may hold several roles (需求 3.13). */
@Entity
@Table(schema = "account", name = "operator_user")
public class OperatorUser extends LoginAccount {

  enum Role {
    ADMIN,
    CAMPAIGN,
    CUSTOMER_SERVICE,
    FINANCE
  }

  /** One row of {@code operator_user_role}; assignments are only added or removed. */
  @Embeddable
  record RoleAssignment(
      @Enumerated(EnumType.STRING) @Column(nullable = false) Role role,
      @Column(nullable = false) Instant createdAt,
      @Column(nullable = false) String createdBy) {}

  @Column(nullable = false)
  private boolean mustChangePassword;

  /** Always needed to issue a token, and an account has at most four. */
  @ElementCollection(fetch = FetchType.EAGER)
  @CollectionTable(
      schema = "account",
      name = "operator_user_role",
      joinColumns = @JoinColumn(name = "operator_user_id"))
  private Set<RoleAssignment> roleAssignments = new HashSet<>();

  protected OperatorUser() {}

  @Override
  public AccountType accountType() {
    return AccountType.OPERATOR;
  }

  @Override
  public Set<String> roles() {
    return roleAssignments.stream()
        .map(assignment -> assignment.role().name())
        .collect(Collectors.toUnmodifiableSet());
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
