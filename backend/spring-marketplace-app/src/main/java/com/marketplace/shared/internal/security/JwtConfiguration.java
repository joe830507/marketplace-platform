package com.marketplace.shared.internal.security;

import com.marketplace.shared.AccessTokenClaims;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Clock;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.security.converter.RsaKeyConverters;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

/**
 * Signs and verifies access tokens with an RSA key pair (ADR_009).
 *
 * <p>Verification needs only the public key, so services split out later can verify tokens without
 * holding the private key.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(JwtKeyProperties.class)
public class JwtConfiguration {

  private static final Logger log = LoggerFactory.getLogger(JwtConfiguration.class);

  @Bean
  RSAKey accessTokenSigningKey(JwtKeyProperties properties) {
    KeyPair keyPair;
    if (properties.privateKey() != null && properties.publicKey() != null) {
      keyPair =
          new KeyPair(
              read(properties.publicKey(), RsaKeyConverters.x509()::convert),
              read(properties.privateKey(), RsaKeyConverters.pkcs8()::convert));
    } else if (properties.privateKey() == null && properties.publicKey() == null) {
      log.warn(
          "marketplace.security.jwt keys are not set; using a key pair generated at startup."
              + " Access tokens become invalid on restart.");
      keyPair = generate();
    } else {
      throw new IllegalStateException(
          "Set both marketplace.security.jwt.private-key and public-key, or neither");
    }
    try {
      RSAKey key =
          new RSAKey.Builder((RSAPublicKey) keyPair.getPublic())
              .privateKey((RSAPrivateKey) keyPair.getPrivate())
              .build();
      return new RSAKey.Builder(key).keyIDFromThumbprint().build();
    } catch (JOSEException e) {
      throw new IllegalStateException("Cannot compute the key ID", e);
    }
  }

  @Bean
  JwtEncoder jwtEncoder(RSAKey accessTokenSigningKey) {
    return new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(accessTokenSigningKey)));
  }

  /**
   * Checks the signature, the issuer and the expiry against the injected clock (設計_006 4.1).
   *
   * <p>No clock skew is allowed: tokens are signed and verified by the same service, so "15
   * minutes" means exactly that.
   */
  @Bean
  JwtDecoder jwtDecoder(RSAKey accessTokenSigningKey, Clock clock) throws JOSEException {
    NimbusJwtDecoder decoder =
        NimbusJwtDecoder.withPublicKey(accessTokenSigningKey.toRSAPublicKey()).build();
    JwtTimestampValidator timestamps = new JwtTimestampValidator(Duration.ZERO);
    timestamps.setClock(clock);
    decoder.setJwtValidator(
        new DelegatingOAuth2TokenValidator<>(
            timestamps, new JwtIssuerValidator(AccessTokenClaims.ISSUER)));
    return decoder;
  }

  private static <T> T read(Resource resource, PemConverter<T> converter) {
    try (InputStream in = resource.getInputStream()) {
      return converter.convert(in);
    } catch (IOException e) {
      throw new UncheckedIOException("Cannot read " + resource, e);
    }
  }

  private static KeyPair generate() {
    try {
      KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
      generator.initialize(2048);
      return generator.generateKeyPair();
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }

  @FunctionalInterface
  private interface PemConverter<T> {
    T convert(InputStream in);
  }
}
