package com.marketplace.account.internal.entity;

import com.marketplace.shared.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * A recipient, phone and address a member keeps for checkout (需求 3.1). Deleting only marks it;
 * checkouts keep their own snapshot (設計_004 3).
 */
@Entity
@Table(schema = "account", name = "shipping_address")
public class ShippingAddress extends AuditableEntity {

  @Id private UUID id;

  @Column(nullable = false, updatable = false)
  private UUID memberId;

  @Column(nullable = false)
  private String recipientName;

  @Column(nullable = false)
  private String phone;

  @Column(nullable = false)
  private String address;

  @Column(nullable = false)
  private boolean isDefault;

  private @Nullable Instant deletedAt;

  protected ShippingAddress() {}

  public ShippingAddress(UUID id, UUID memberId) {
    this.id = id;
    this.memberId = memberId;
  }

  public void change(String recipientName, String phone, String address, boolean isDefault) {
    this.recipientName = recipientName;
    this.phone = phone;
    this.address = address;
    this.isDefault = isDefault;
  }

  public void clearDefault() {
    this.isDefault = false;
  }

  public void delete(Instant now) {
    this.deletedAt = now;
    this.isDefault = false;
  }

  public UUID id() {
    return id;
  }

  public UUID memberId() {
    return memberId;
  }

  public String recipientName() {
    return recipientName;
  }

  public String phone() {
    return phone;
  }

  public String address() {
    return address;
  }

  public boolean isDefault() {
    return isDefault;
  }

  public boolean isDeleted() {
    return deletedAt != null;
  }
}
