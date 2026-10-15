package com.marketplace.account.internal.service;

import com.marketplace.account.SellerCommissionRates;
import com.marketplace.account.SellerCreated;
import com.marketplace.account.SellerRole;
import com.marketplace.account.SellerType;
import com.marketplace.account.internal.entity.Seller;
import com.marketplace.account.internal.entity.SellerUser;
import com.marketplace.account.internal.repository.SellerRepository;
import com.marketplace.account.internal.repository.SellerUserRepository;
import com.marketplace.shared.Actor;
import com.marketplace.shared.ApiException;
import com.marketplace.shared.AuditLog;
import com.marketplace.shared.ErrorCode;
import com.marketplace.shared.IdGenerator;
import com.marketplace.shared.LikePatterns;
import com.marketplace.shared.PageResult;
import com.marketplace.shared.Paging;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Sellers and their first administrator, created by operators (需求 3.2). Sellers do not sign up by
 * themselves.
 */
@Service
public class SellerService {

  /** Newest first; identifiers are UUIDv7, so they order accounts created in the same instant. */
  private static final Sort NEWEST_FIRST =
      Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"));

  /** A seller as operators see it. */
  public record SellerView(
      UUID id, String name, SellerType type, boolean active, @Nullable Integer commissionRate) {}

  /** What an operator enters to create a seller. */
  public record NewSeller(
      String name,
      SellerType type,
      @Nullable Integer commissionRate,
      String adminUsername,
      String adminInitialPassword) {}

  private final SellerRepository sellers;
  private final SellerUserRepository sellerUsers;
  private final ObjectProvider<SellerCommissionRates> commissionRates;
  private final ApplicationEventPublisher events;
  private final PasswordEncoder passwordEncoder;
  private final AuditLog auditLog;
  private final IdGenerator ids;

  SellerService(
      SellerRepository sellers,
      SellerUserRepository sellerUsers,
      ObjectProvider<SellerCommissionRates> commissionRates,
      ApplicationEventPublisher events,
      PasswordEncoder passwordEncoder,
      AuditLog auditLog,
      IdGenerator ids) {
    this.sellers = sellers;
    this.sellerUsers = sellerUsers;
    this.commissionRates = commissionRates;
    this.events = events;
    this.passwordEncoder = passwordEncoder;
    this.auditLog = auditLog;
    this.ids = ids;
  }

  /**
   * Creates a seller and its first administrator, who must change the initial password at first
   * login (設計_005 2.7). Third-party sellers need a commission rate; first-party sellers have none.
   */
  @Transactional
  public SellerView create(NewSeller request) {
    if (request.type() == SellerType.FIRST_PARTY && request.commissionRate() != null) {
      throw new ApiException(ErrorCode.COMMISSION_NOT_APPLICABLE, "平台自營賣家不設定抽成比例");
    }
    if (request.type() == SellerType.THIRD_PARTY && request.commissionRate() == null) {
      throw ApiException.invalidField("commissionRate", "外部賣家須設定抽成比例");
    }
    if (sellerUsers.findByUsername(request.adminUsername()).isPresent()) {
      throw MemberService.usernameTaken();
    }

    Actor actor = Actor.current();
    Seller seller = sellers.save(new Seller(ids.newId(), request.name(), request.type()));
    SellerUser admin =
        new SellerUser(
            ids.newId(),
            seller.id(),
            request.adminUsername(),
            passwordEncoder.encode(request.adminInitialPassword()),
            SellerRole.ADMIN);
    try {
      sellerUsers.saveAndFlush(admin);
    } catch (DataIntegrityViolationException e) {
      throw MemberService.usernameTaken();
    }

    Map<String, Object> created = new HashMap<>();
    created.put("name", seller.name());
    created.put("sellerType", seller.type().name());
    if (request.commissionRate() != null) {
      created.put("commissionRate", request.commissionRate());
    }
    auditLog.record(actor, "CREATE_SELLER", "SELLER", seller.id().toString(), null, created);
    auditLog.record(
        actor,
        "CREATE_SELLER_USER",
        "SELLER_USER",
        admin.id().toString(),
        null,
        Map.of("sellerId", seller.id().toString(), "username", admin.username(), "role", "ADMIN"));
    events.publishEvent(new SellerCreated(seller.id(), seller.type(), request.commissionRate()));
    return view(seller, request.commissionRate());
  }

  @Transactional(readOnly = true)
  public SellerView find(UUID sellerId) {
    Seller seller =
        sellers
            .findById(sellerId)
            .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "賣家不存在"));
    return withRates(List.of(seller)).getFirst();
  }

  /** Sellers whose name contains the keyword, newest first. */
  @Transactional(readOnly = true)
  public PageResult<SellerView> search(@Nullable String keyword, Paging paging) {
    Page<Seller> page =
        sellers.searchByName(
            LikePatterns.contains(keyword),
            PageRequest.of(paging.page() - 1, paging.size(), NEWEST_FIRST));
    List<SellerView> views = withRates(page.getContent());
    return PageResult.of(views, paging, page.getTotalElements());
  }

  private List<SellerView> withRates(List<Seller> found) {
    SellerCommissionRates rates = commissionRates.getIfAvailable();
    Map<UUID, Integer> byId =
        rates == null ? Map.of() : rates.currentRates(found.stream().map(Seller::id).toList());
    return found.stream().map(seller -> view(seller, byId.get(seller.id()))).toList();
  }

  private static SellerView view(Seller seller, @Nullable Integer commissionRate) {
    return new SellerView(
        seller.id(), seller.name(), seller.type(), seller.isActive(), commissionRate);
  }
}
