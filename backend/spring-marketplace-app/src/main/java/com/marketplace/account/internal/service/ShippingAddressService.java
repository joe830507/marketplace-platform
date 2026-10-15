package com.marketplace.account.internal.service;

import com.marketplace.account.internal.entity.ShippingAddress;
import com.marketplace.account.internal.repository.MemberRepository;
import com.marketplace.account.internal.repository.ShippingAddressRepository;
import com.marketplace.shared.Actor;
import com.marketplace.shared.ApiException;
import com.marketplace.shared.ErrorCode;
import com.marketplace.shared.IdGenerator;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * A member's shipping addresses (需求 3.1). Each member has at most one default address; making an
 * address the default clears the previous one in the same transaction.
 *
 * <p>Changes lock the member's row first, so concurrent changes of the same member never leave two
 * defaults (設計_004 3).
 */
@Service
public class ShippingAddressService {

  /** What a member enters for an address. */
  public record AddressInput(
      String recipientName, String phone, String address, boolean isDefault) {}

  public record AddressView(
      UUID id, String recipientName, String phone, String address, boolean isDefault) {}

  private final ShippingAddressRepository addresses;
  private final MemberRepository members;
  private final IdGenerator ids;
  private final Clock clock;

  ShippingAddressService(
      ShippingAddressRepository addresses, MemberRepository members, IdGenerator ids, Clock clock) {
    this.addresses = addresses;
    this.members = members;
    this.ids = ids;
    this.clock = clock;
  }

  @Transactional(readOnly = true)
  public List<AddressView> list(UUID memberId) {
    return addresses.findActive(memberId).stream().map(ShippingAddressService::view).toList();
  }

  @Transactional
  public AddressView create(UUID memberId, AddressInput input) {
    lockMember(memberId);
    if (input.isDefault()) {
      clearDefault(memberId);
    }
    ShippingAddress address = new ShippingAddress(ids.newId(), memberId);
    address.change(input.recipientName(), input.phone(), input.address(), input.isDefault());
    return view(addresses.save(address));
  }

  @Transactional
  public AddressView update(UUID memberId, UUID addressId, AddressInput input) {
    lockMember(memberId);
    if (input.isDefault()) {
      clearDefault(memberId);
    }
    ShippingAddress address = find(memberId, addressId);
    address.change(input.recipientName(), input.phone(), input.address(), input.isDefault());
    return view(address);
  }

  /** Marks the address deleted; checkouts keep their own snapshot (設計_004 3). */
  @Transactional
  public void delete(UUID memberId, UUID addressId) {
    lockMember(memberId);
    find(memberId, addressId).delete(clock.instant());
  }

  /** An address of the member that is not deleted. */
  @Transactional(readOnly = true)
  public AddressView get(UUID memberId, UUID addressId) {
    return view(find(memberId, addressId));
  }

  private ShippingAddress find(UUID memberId, UUID addressId) {
    return addresses
        .findActive(memberId, addressId)
        .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "收件資訊不存在"));
  }

  private static AddressView view(ShippingAddress address) {
    return new AddressView(
        address.id(),
        address.recipientName(),
        address.phone(),
        address.address(),
        address.isDefault());
  }

  private void lockMember(UUID memberId) {
    members
        .lockById(memberId)
        .orElseThrow(() -> new ApiException(ErrorCode.UNAUTHENTICATED, "帳號不存在"));
  }

  private void clearDefault(UUID memberId) {
    addresses.clearDefault(memberId, clock.instant(), Actor.current().value());
  }
}
