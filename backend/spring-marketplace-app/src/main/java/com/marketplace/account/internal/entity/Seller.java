package com.marketplace.account.internal.entity;

import com.marketplace.shared.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

/** A seller is an organization; people work for it through seller accounts (需求 3.2). */
@Entity
@Table(schema = "account", name = "seller")
public class Seller extends AuditableEntity {

  enum Type {
    FIRST_PARTY,
    THIRD_PARTY
  }

  @Id private UUID id;

  @Column(nullable = false)
  private String name;

  @Enumerated(EnumType.STRING)
  @Column(name = "seller_type", nullable = false)
  private Type type;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private AccountStatus status;

  protected Seller() {}

  public boolean isActive() {
    return status == AccountStatus.ACTIVE;
  }
}
