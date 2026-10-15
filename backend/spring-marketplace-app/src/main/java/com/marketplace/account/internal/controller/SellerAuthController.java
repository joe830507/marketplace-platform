package com.marketplace.account.internal.controller;

import com.marketplace.openapi.api.SellerAuthApi;
import com.marketplace.openapi.model.AccessToken;
import com.marketplace.openapi.model.ChangePasswordRequest;
import com.marketplace.openapi.model.LoginRequest;
import com.marketplace.shared.AccountType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/** Login and tokens for seller accounts. */
@RestController
public class SellerAuthController implements SellerAuthApi {

  private final AuthEndpoints auth;

  SellerAuthController(AuthEndpoints auth) {
    this.auth = auth;
  }

  @Override
  public ResponseEntity<AccessToken> loginSellerUser(LoginRequest loginRequest) {
    return auth.login(AccountType.SELLER_USER, loginRequest);
  }

  @Override
  public ResponseEntity<AccessToken> refreshSellerUserToken() {
    return auth.refresh(AccountType.SELLER_USER);
  }

  @Override
  public ResponseEntity<Void> logoutSellerUser() {
    return auth.logout(AccountType.SELLER_USER);
  }

  @Override
  public ResponseEntity<Void> changeSellerUserPassword(
      ChangePasswordRequest changePasswordRequest) {
    return auth.changePassword(AccountType.SELLER_USER, changePasswordRequest);
  }
}
