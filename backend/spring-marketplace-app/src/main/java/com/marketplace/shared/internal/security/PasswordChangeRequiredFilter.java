package com.marketplace.shared.internal.security;

import com.marketplace.shared.AuthenticatedAccount;
import com.marketplace.shared.ErrorCode;
import com.marketplace.shared.internal.web.Problems;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Optional;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Accounts that still use their initial password may only call the auth API, where they change it
 * (設計_005 2.7).
 *
 * <p>Runs after authorization, so a request that is not allowed at all still gets {@code
 * FORBIDDEN}.
 */
public class PasswordChangeRequiredFilter extends OncePerRequestFilter {

  private final Problems problems;

  PasswordChangeRequiredFilter(Problems problems) {
    this.problems = problems;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    Optional<AuthenticatedAccount> account = AuthenticatedAccount.current();
    if (account.isPresent()
        && account.get().mustChangePassword()
        && !request.getRequestURI().startsWith(account.get().type().apiPrefix() + "/auth/")) {
      problems.write(
          response, problems.create(ErrorCode.PASSWORD_CHANGE_REQUIRED, "使用初始密碼登入，請先修改密碼"));
      return;
    }
    chain.doFilter(request, response);
  }
}
