package com.marketplace.account.internal.controller;

import com.marketplace.account.internal.service.MemberService;
import com.marketplace.account.internal.service.MemberService.MemberView;
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

/** Registration, login and tokens for members. */
@RestController
public class MemberAuthController implements MemberAuthApi {

  private final AuthEndpoints auth;
  private final MemberService members;

  MemberAuthController(AuthEndpoints auth, MemberService members) {
    this.auth = auth;
    this.members = members;
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

  /** Registers a member who accepts the latest terms of service and privacy policy (N1). */
  @Override
  public ResponseEntity<Member> registerMember(RegisterMemberRequest request) {
    MemberView member =
        members.register(
            request.getUsername(),
            request.getPassword(),
            request.getDisplayName(),
            request.getConsentedTermsDocumentIds());
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(new Member(member.id(), member.username(), member.displayName()));
  }
}
