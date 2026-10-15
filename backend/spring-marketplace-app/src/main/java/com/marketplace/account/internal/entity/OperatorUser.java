package com.marketplace.account.internal.entity;

import com.marketplace.account.OperatorRole;
import com.marketplace.shared.AccountType;
import com.marketplace.shared.Actor;
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
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/** A platform operator's account; it may hold several roles (需求 3.13). */
@Entity
@Table(schema = "account", name = "operator_user")
public class OperatorUser extends LoginAccount {

  /** One row of {@code operator_user_role}; assignments are only added or removed. */
  @Embeddable
  record RoleAssignment(
      @Enumerated(EnumType.STRING) @Column(nullable = false) OperatorRole role,
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

  /** An account created by an administrator with an initial password, which must be changed. */
  public OperatorUser(
      UUID id,
      String username,
      String passwordHash,
      Set<OperatorRole> roles,
      Instant now,
      Actor actor) {
    super(id, username, passwordHash);
    this.mustChangePassword = true;
    assignRoles(roles, now, actor);
  }

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

  public Set<OperatorRole> roleSet() {
    Set<OperatorRole> roles = EnumSet.noneOf(OperatorRole.class);
    roleAssignments.forEach(assignment -> roles.add(assignment.role()));
    return roles;
  }

  /**
   * Replaces the roles. Roles the account keeps keep their original assignment; removed roles are
   * deleted (設計_004 2.4).
   */
  public void assignRoles(Set<OperatorRole> roles, Instant now, Actor actor) {
    roleAssignments.removeIf(assignment -> !roles.contains(assignment.role()));
    Set<OperatorRole> current = roleSet();
    for (OperatorRole role : roles) {
      if (!current.contains(role)) {
        roleAssignments.add(new RoleAssignment(role, now, actor.value()));
      }
    }
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
