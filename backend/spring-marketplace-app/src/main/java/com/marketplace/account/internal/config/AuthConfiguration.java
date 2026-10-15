package com.marketplace.account.internal.config;

import java.util.Map;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.password.DelegatingPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Auth settings ({@link AuthProperties}) and password hashing.
 *
 * <p>Passwords are hashed with Argon2id (需求 4: passwords are stored only as hashes).
 *
 * <p>Not bcrypt: bcrypt accepts at most 72 bytes, and a 100-character password (the limit in the
 * API) can be up to 300 bytes in UTF-8. Hashes carry an {@code {id}} prefix, so the algorithm or
 * its parameters can change later without breaking existing hashes.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(AuthProperties.class)
public class AuthConfiguration {

  private static final String ARGON2 = "argon2@SpringSecurity_v5_8";

  @Bean
  PasswordEncoder passwordEncoder() {
    return new DelegatingPasswordEncoder(
        ARGON2, Map.of(ARGON2, Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8()));
  }
}
