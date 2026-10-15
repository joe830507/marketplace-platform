package com.marketplace.account.internal.controller;

import com.marketplace.account.internal.service.ShippingAddressService;
import com.marketplace.account.internal.service.ShippingAddressService.AddressInput;
import com.marketplace.account.internal.service.ShippingAddressService.AddressView;
import com.marketplace.account.internal.service.TermsService;
import com.marketplace.openapi.api.MemberAccountApi;
import com.marketplace.openapi.model.ShippingAddress;
import com.marketplace.openapi.model.ShippingAddressRequest;
import com.marketplace.openapi.model.TermsConsentRequest;
import com.marketplace.shared.AuthenticatedAccount;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/** A member's terms consents and shipping addresses. */
@RestController
public class MemberAccountController implements MemberAccountApi {

  private final TermsService terms;
  private final ShippingAddressService addresses;

  MemberAccountController(TermsService terms, ShippingAddressService addresses) {
    this.terms = terms;
    this.addresses = addresses;
  }

  @Override
  public ResponseEntity<Void> consentMemberTerms(TermsConsentRequest termsConsentRequest) {
    terms.consent(AuthenticatedAccount.require(), termsConsentRequest.getTermsDocumentId());
    return ResponseEntity.noContent().build();
  }

  @Override
  public ResponseEntity<List<ShippingAddress>> listShippingAddresses() {
    return ResponseEntity.ok(
        addresses.list(memberId()).stream().map(MemberAccountController::toApi).toList());
  }

  @Override
  public ResponseEntity<ShippingAddress> createShippingAddress(ShippingAddressRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(toApi(addresses.create(memberId(), input(request))));
  }

  @Override
  public ResponseEntity<ShippingAddress> updateShippingAddress(
      UUID addressId, ShippingAddressRequest request) {
    return ResponseEntity.ok(toApi(addresses.update(memberId(), addressId, input(request))));
  }

  @Override
  public ResponseEntity<Void> deleteShippingAddress(UUID addressId) {
    addresses.delete(memberId(), addressId);
    return ResponseEntity.noContent().build();
  }

  private static UUID memberId() {
    return AuthenticatedAccount.require().id();
  }

  private static AddressInput input(ShippingAddressRequest request) {
    return new AddressInput(
        request.getRecipientName(),
        request.getPhone(),
        request.getAddress(),
        request.getIsDefault());
  }

  private static ShippingAddress toApi(AddressView address) {
    return new ShippingAddress(
        address.recipientName(),
        address.phone(),
        address.address(),
        address.isDefault(),
        address.id());
  }
}
