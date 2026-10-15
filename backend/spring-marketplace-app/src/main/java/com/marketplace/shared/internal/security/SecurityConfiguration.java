package com.marketplace.shared.internal.security;

import static org.springframework.http.HttpMethod.POST;

import com.marketplace.shared.AccountGate;
import com.marketplace.shared.AccountType;
import com.marketplace.shared.internal.web.Problems;
import jakarta.servlet.DispatcherType;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;

/**
 * Who may call which path (設計_005 2.1).
 *
 * <p>Each prefix accepts only access tokens of its own account type, so a member's token cannot
 * call seller APIs. Role checks inside a prefix belong to the module that owns the API: its
 * controllers declare them with {@code PreAuthorize}.
 */
@Configuration(proxyBeanMethods = false)
@EnableMethodSecurity
public class SecurityConfiguration {

  @Bean
  SecurityFilterChain securityFilterChain(
      HttpSecurity http,
      JwtDecoder jwtDecoder,
      SecurityProblemHandler problemHandler,
      Problems problems,
      List<AccountGate> gates)
      throws Exception {
    http.csrf(AbstractHttpConfigurer::disable)
        .formLogin(AbstractHttpConfigurer::disable)
        .httpBasic(AbstractHttpConfigurer::disable)
        .logout(AbstractHttpConfigurer::disable)
        .requestCache(AbstractHttpConfigurer::disable)
        .sessionManagement(
            session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(
            requests -> {
              requests
                  .dispatcherTypeMatchers(DispatcherType.ERROR)
                  .permitAll()
                  .requestMatchers("/actuator/health", "/actuator/health/**", "/actuator/info")
                  .permitAll()
                  .requestMatchers("/api/v1/public/**", "/api/v1/webhooks/**")
                  .permitAll()
                  .requestMatchers(POST, AccountType.MEMBER.apiPrefix() + "/auth/register")
                  .permitAll();
              for (AccountType type : AccountType.values()) {
                // Login, refresh and logout identify the account by password or refresh token.
                String auth = type.apiPrefix() + "/auth/";
                requests
                    .requestMatchers(POST, auth + "login", auth + "refresh", auth + "logout")
                    .permitAll()
                    .requestMatchers(type.apiPrefix() + "/**")
                    .hasAuthority(type.authority());
              }
              requests.anyRequest().denyAll();
            })
        .oauth2ResourceServer(
            resourceServer ->
                resourceServer
                    .jwt(
                        jwt ->
                            jwt.decoder(jwtDecoder)
                                .jwtAuthenticationConverter(
                                    new AccessTokenAuthenticationConverter()))
                    .authenticationEntryPoint(problemHandler)
                    .accessDeniedHandler(problemHandler))
        .exceptionHandling(
            exceptions ->
                exceptions
                    .authenticationEntryPoint(problemHandler)
                    .accessDeniedHandler(problemHandler))
        .addFilterAfter(new AccountGateFilter(problems, gates), AuthorizationFilter.class);
    return http.build();
  }
}
