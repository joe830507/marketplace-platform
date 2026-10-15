package com.marketplace.account.internal.controller;

import com.marketplace.openapi.api.MemberAuthApi;
import com.marketplace.openapi.model.AccessToken;
import com.marketplace.openapi.model.ChangePasswordRequest;
import com.marketplace.openapi.model.LoginRequest;
import com.marketplace.openapi.model.Member;
import com.marketplace.openapi.model.RegisterMemberRequest;
import com.marketplace.shared.AccountType;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/** Login and tokens for members. */
@RestController
public class MemberAuthController implements MemberAuthApi {

  private final AuthEndpoints auth;

  MemberAuthController(AuthEndpoints auth) {
    this.auth = auth;
  }

  @Override
  public ResponseEntity<AccessToken> loginMember(LoginRequest loginRequest) {
    return auth.login(AccountType.MEMBER, loginRequest);
  }

  @Override
  public ResponseEntity<AccessToken> refreshMemberToken() {
    return auth.refresh(AccountType.MEMBER);
  }

  @Override
  public ResponseEntity<Void> logoutMember() {
    return auth.logout(AccountType.MEMBER);
  }

  @Override
  public ResponseEntity<Void> changeMemberPassword(ChangePasswordRequest changePasswordRequest) {
    return auth.changePassword(AccountType.MEMBER, changePasswordRequest);
  }

  /** Registration comes with terms consent in the next stage of the account module. */
  @Override
  public ResponseEntity<Member> registerMember(RegisterMemberRequest registerMemberRequest) {
    return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build();
  }
}
