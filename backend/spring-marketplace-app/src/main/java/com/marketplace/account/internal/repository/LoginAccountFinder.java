package com.marketplace.account.internal.repository;

import com.marketplace.account.internal.entity.LoginAccount;
import com.marketplace.shared.AccountType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Finds an account of any of the three types by delegating to the matching repository. Read only;
 * it writes no SQL itself (設計_003 5).
 */
@Component
public class LoginAccountFinder {

  private final MemberRepository members;
  private final SellerUserRepository sellerUsers;
  private final OperatorUserRepository operators;

  LoginAccountFinder(
      MemberRepository members,
      SellerUserRepository sellerUsers,
      OperatorUserRepository operators) {
    this.members = members;
    this.sellerUsers = sellerUsers;
    this.operators = operators;
  }

  public Optional<? extends LoginAccount> findByUsername(AccountType type, String username) {
    return switch (type) {
      case MEMBER -> members.findByUsername(username);
      case SELLER_USER -> sellerUsers.findByUsername(username);
      case OPERATOR -> operators.findByUsername(username);
    };
  }

  public Optional<? extends LoginAccount> findById(AccountType type, UUID id) {
    return switch (type) {
      case MEMBER -> members.findById(id);
      case SELLER_USER -> sellerUsers.findById(id);
      case OPERATOR -> operators.findById(id);
    };
  }
}
