package com.marketplace.shared.internal.security;

import com.marketplace.shared.AccessTokenClaims;
import com.marketplace.shared.AccountType;
import com.marketplace.shared.AuthenticatedAccount;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Turns a verified access token into an {@link AuthenticatedAccount} with authorities for its
 * account type and roles.
 */
public class AccessTokenAuthenticationConverter
    implements Converter<Jwt, AbstractAuthenticationToken> {

  @Override
  public AbstractAuthenticationToken convert(Jwt jwt) {
    AccountType type = AccountType.valueOf(jwt.getClaimAsString(AccessTokenClaims.ACCOUNT_TYPE));
    String sellerId = jwt.getClaimAsString(AccessTokenClaims.SELLER_ID);
    List<String> roleClaim = jwt.getClaimAsStringList(AccessTokenClaims.ROLES);
    Set<String> roles = roleClaim == null ? Set.of() : new HashSet<>(roleClaim);
    AuthenticatedAccount account =
        new AuthenticatedAccount(
            type,
            UUID.fromString(jwt.getSubject()),
            sellerId == null ? null : UUID.fromString(sellerId),
            roles,
            Boolean.TRUE.equals(jwt.getClaimAsBoolean(AccessTokenClaims.MUST_CHANGE_PASSWORD)));

    List<GrantedAuthority> authorities = new ArrayList<>();
    authorities.add(new SimpleGrantedAuthority(type.authority()));
    roles.forEach(role -> authorities.add(new SimpleGrantedAuthority(type.roleAuthority(role))));
    return new AccountAuthentication(account, jwt, authorities);
  }

  /** The authentication of a request with a valid access token. */
  static final class AccountAuthentication extends AbstractAuthenticationToken {

    private final AuthenticatedAccount account;
    private final Jwt jwt;

    AccountAuthentication(
        AuthenticatedAccount account, Jwt jwt, List<GrantedAuthority> authorities) {
      super(authorities);
      this.account = account;
      this.jwt = jwt;
      setAuthenticated(true);
    }

    @Override
    public AuthenticatedAccount getPrincipal() {
      return account;
    }

    @Override
    public Jwt getCredentials() {
      return jwt;
    }
  }
}
