package com.marketplace.account.internal.controller;

import com.marketplace.account.OperatorRole;
import com.marketplace.account.SellerType;
import com.marketplace.account.internal.service.OperatorUserService;
import com.marketplace.account.internal.service.OperatorUserService.OperatorView;
import com.marketplace.account.internal.service.SellerService;
import com.marketplace.account.internal.service.SellerService.NewSeller;
import com.marketplace.account.internal.service.SellerService.SellerView;
import com.marketplace.account.internal.service.TermsService;
import com.marketplace.openapi.api.OpsAccountApi;
import com.marketplace.openapi.model.CreateOperatorUserRequest;
import com.marketplace.openapi.model.CreateSellerRequest;
import com.marketplace.openapi.model.OperatorRolesRequest;
import com.marketplace.openapi.model.OperatorUser;
import com.marketplace.openapi.model.OperatorUserPage;
import com.marketplace.openapi.model.PublishTermsRequest;
import com.marketplace.openapi.model.Seller;
import com.marketplace.openapi.model.SellerPage;
import com.marketplace.openapi.model.TermsConsentRecord;
import com.marketplace.openapi.model.TermsDocument;
import com.marketplace.shared.ApiPages;
import com.marketplace.shared.PageResult;
import com.marketplace.shared.Paging;
import com.marketplace.shared.RoleChecks;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RestController;

/**
 * Sellers, operator accounts and terms as operators manage them. Every operator role may read
 * sellers and terms; changes are for operator administrators (需求 3.13).
 */
@RestController
public class OpsAccountController implements OpsAccountApi {

  private final SellerService sellers;
  private final OperatorUserService operators;
  private final TermsService terms;

  OpsAccountController(SellerService sellers, OperatorUserService operators, TermsService terms) {
    this.sellers = sellers;
    this.operators = operators;
    this.terms = terms;
  }

  @Override
  public ResponseEntity<SellerPage> listSellers(
      @Nullable String keyword, Integer page, Integer size) {
    PageResult<SellerView> result =
        sellers.search(keyword, Paging.of(page, size, Paging.MAX_ITEMS));
    return ResponseEntity.ok(
        new SellerPage(
            result.items().stream().map(OpsAccountController::toApi).toList(),
            ApiPages.meta(result)));
  }

  @Override
  @PreAuthorize(RoleChecks.OPERATOR_ADMIN)
  public ResponseEntity<Seller> createSeller(CreateSellerRequest request) {
    SellerView created =
        sellers.create(
            new NewSeller(
                request.getName(),
                SellerType.valueOf(request.getSellerType().getValue()),
                request.getCommissionRate(),
                request.getAdminUsername(),
                request.getAdminInitialPassword()));
    return ResponseEntity.status(HttpStatus.CREATED).body(toApi(created));
  }

  @Override
  public ResponseEntity<Seller> getSeller(UUID sellerId) {
    return ResponseEntity.ok(toApi(sellers.find(sellerId)));
  }

  @Override
  @PreAuthorize(RoleChecks.OPERATOR_ADMIN)
  public ResponseEntity<TermsDocument> publishTermsDocument(PublishTermsRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(
            TermsMapper.toApi(
                terms.publish(
                    TermsMapper.fromApi(request.getDocumentType()), request.getContent())));
  }

  @Override
  public ResponseEntity<TermsDocument> getTermsDocument(UUID termsDocumentId) {
    return ResponseEntity.ok(TermsMapper.toApi(terms.find(termsDocumentId)));
  }

  @Override
  public ResponseEntity<List<TermsConsentRecord>> listTermsConsents(
      @Nullable UUID memberId, @Nullable UUID sellerId) {
    return ResponseEntity.ok(
        terms.consents(memberId, sellerId).stream().map(TermsMapper::toApi).toList());
  }

  @Override
  @PreAuthorize(RoleChecks.OPERATOR_ADMIN)
  public ResponseEntity<OperatorUserPage> listOperatorUsers(Integer page, Integer size) {
    PageResult<OperatorView> result = operators.list(Paging.of(page, size, Paging.MAX_ITEMS));
    return ResponseEntity.ok(
        new OperatorUserPage(
            result.items().stream().map(OpsAccountController::toApi).toList(),
            ApiPages.meta(result)));
  }

  @Override
  @PreAuthorize(RoleChecks.OPERATOR_ADMIN)
  public ResponseEntity<OperatorUser> createOperatorUser(CreateOperatorUserRequest request) {
    OperatorView created =
        operators.create(
            request.getUsername(), request.getInitialPassword(), roles(request.getRoles()));
    return ResponseEntity.status(HttpStatus.CREATED).body(toApi(created));
  }

  @Override
  @PreAuthorize(RoleChecks.OPERATOR_ADMIN)
  public ResponseEntity<OperatorUser> setOperatorRoles(
      UUID operatorUserId, OperatorRolesRequest request) {
    return ResponseEntity.ok(toApi(operators.setRoles(operatorUserId, roles(request.getRoles()))));
  }

  private static Set<OperatorRole> roles(
      Collection<com.marketplace.openapi.model.OperatorRole> roles) {
    Set<OperatorRole> result = EnumSet.noneOf(OperatorRole.class);
    roles.forEach(role -> result.add(OperatorRole.valueOf(role.getValue())));
    return result;
  }

  private static Seller toApi(SellerView seller) {
    return new Seller(
            seller.id(),
            seller.name(),
            com.marketplace.openapi.model.SellerType.fromValue(seller.type().name()),
            seller.active() ? Seller.StatusEnum.ACTIVE : Seller.StatusEnum.DISABLED)
        .commissionRate(seller.commissionRate());
  }

  private static OperatorUser toApi(OperatorView operator) {
    return new OperatorUser(
        operator.id(),
        operator.username(),
        operator.active() ? OperatorUser.StatusEnum.ACTIVE : OperatorUser.StatusEnum.DISABLED,
        operator.roles().stream()
            .sorted()
            .map(role -> com.marketplace.openapi.model.OperatorRole.fromValue(role.name()))
            .toList());
  }
}
