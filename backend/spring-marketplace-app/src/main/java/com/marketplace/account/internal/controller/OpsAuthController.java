package com.marketplace.account.internal.controller;

import com.marketplace.openapi.api.OpsAuthApi;
import com.marketplace.openapi.model.AccessToken;
import com.marketplace.openapi.model.ChangePasswordRequest;
import com.marketplace.openapi.model.LoginRequest;
import com.marketplace.shared.AccountType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/** Login and tokens for operator accounts. */
@RestController
public class OpsAuthController implements OpsAuthApi {

  private final AuthEndpoints auth;

  OpsAuthController(AuthEndpoints auth) {
    this.auth = auth;
  }

  @Override
  public ResponseEntity<AccessToken> loginOperator(LoginRequest loginRequest) {
    return auth.login(AccountType.OPERATOR, loginRequest);
  }

  @Override
  public ResponseEntity<AccessToken> refreshOperatorToken() {
    return auth.refresh(AccountType.OPERATOR);
  }

  @Override
  public ResponseEntity<Void> logoutOperator() {
    return auth.logout(AccountType.OPERATOR);
  }

  @Override
  public ResponseEntity<Void> changeOperatorPassword(ChangePasswordRequest changePasswordRequest) {
    return auth.changePassword(AccountType.OPERATOR, changePasswordRequest);
  }
}
