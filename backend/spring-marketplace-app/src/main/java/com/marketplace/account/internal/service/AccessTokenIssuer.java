package com.marketplace.account.internal.service;

import com.marketplace.account.internal.config.AuthProperties;
import com.marketplace.account.internal.entity.LoginAccount;
import com.marketplace.shared.AccessTokenClaims;
import java.time.Instant;
import java.util.List;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Component;

/**
 * Issues access tokens (ADR_009). Roles and status are read when the token is issued, so a change
 * takes effect at the latest when the token expires.
 */
@Component
public class AccessTokenIssuer {

  private final JwtEncoder encoder;
  private final AuthProperties properties;

  AccessTokenIssuer(JwtEncoder encoder, AuthProperties properties) {
    this.encoder = encoder;
    this.properties = properties;
  }

  String issue(LoginAccount account, Instant now) {
    JwtClaimsSet.Builder claims =
        JwtClaimsSet.builder()
            .issuer(AccessTokenClaims.ISSUER)
            .subject(account.id().toString())
            .issuedAt(now)
            .expiresAt(now.plus(properties.accessTokenTtl()))
            .claim(AccessTokenClaims.ACCOUNT_TYPE, account.accountType().name())
            .claim(AccessTokenClaims.ROLES, List.copyOf(account.roles()));
    if (account.sellerId() != null) {
      claims.claim(AccessTokenClaims.SELLER_ID, account.sellerId().toString());
    }
    if (account.mustChangePassword()) {
      claims.claim(AccessTokenClaims.MUST_CHANGE_PASSWORD, true);
    }
    JwsHeader header = JwsHeader.with(SignatureAlgorithm.RS256).build();
    return encoder.encode(JwtEncoderParameters.from(header, claims.build())).getTokenValue();
  }

  long ttlSeconds() {
    return properties.accessTokenTtl().toSeconds();
  }
}
