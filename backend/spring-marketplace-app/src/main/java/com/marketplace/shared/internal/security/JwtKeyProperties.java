package com.marketplace.shared.internal.security;

import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.core.io.Resource;

/**
 * The RSA key pair that signs access tokens (ADR_009), as PEM files: PKCS#8 for the private key and
 * X.509 for the public key.
 *
 * <p>When neither is set, a key pair is generated at startup. That is only for local runs and
 * tests: tokens become invalid on restart and differ between instances.
 */
@ConfigurationProperties("marketplace.security.jwt")
public record JwtKeyProperties(@Nullable Resource privateKey, @Nullable Resource publicKey) {}
