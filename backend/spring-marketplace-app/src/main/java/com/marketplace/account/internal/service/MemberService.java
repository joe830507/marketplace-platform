package com.marketplace.account.internal.service;

import com.marketplace.account.internal.entity.Member;
import com.marketplace.account.internal.repository.MemberRepository;
import com.marketplace.shared.ApiException;
import com.marketplace.shared.ErrorCode;
import com.marketplace.shared.IdGenerator;
import java.util.Collection;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Member registration (需求 3.12, 使用者故事 N1). */
@Service
public class MemberService {

  public record MemberView(UUID id, String username, String displayName) {}

  private final MemberRepository members;
  private final TermsService terms;
  private final PasswordEncoder passwordEncoder;
  private final IdGenerator ids;

  MemberService(
      MemberRepository members,
      TermsService terms,
      PasswordEncoder passwordEncoder,
      IdGenerator ids) {
    this.members = members;
    this.terms = terms;
    this.passwordEncoder = passwordEncoder;
    this.ids = ids;
  }

  /**
   * Registers a member who accepts the latest terms of service and privacy policy; the consents are
   * recorded with the account.
   *
   * @throws ApiException {@code TERMS_NOT_LATEST} or {@code USERNAME_TAKEN}
   */
  @Transactional
  public MemberView register(
      String username, String password, String displayName, Collection<UUID> consented) {
    terms.requireLatestMemberDocuments(consented);
    if (members.findByUsername(username).isPresent()) {
      throw usernameTaken();
    }
    Member member =
        new Member(ids.newId(), username, passwordEncoder.encode(password), displayName);
    try {
      members.saveAndFlush(member);
    } catch (DataIntegrityViolationException e) {
      // Another registration took the username after the check above.
      throw usernameTaken();
    }
    terms.recordMemberConsents(member.id(), consented);
    return new MemberView(member.id(), member.username(), member.displayName());
  }

  static ApiException usernameTaken() {
    return new ApiException(ErrorCode.USERNAME_TAKEN, "帳號名稱已被使用");
  }
}
