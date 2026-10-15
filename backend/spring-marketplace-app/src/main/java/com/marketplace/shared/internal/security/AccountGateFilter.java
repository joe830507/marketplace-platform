package com.marketplace.shared.internal.security;

import com.marketplace.shared.AccountGate;
import com.marketplace.shared.ApiException;
import com.marketplace.shared.AuthenticatedAccount;
import com.marketplace.shared.ErrorCode;
import com.marketplace.shared.internal.web.Problems;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import org.springframework.http.ProblemDetail;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Stops authenticated requests whose account must first do something else.
 *
 * <p>Accounts that still use their initial password may only call the auth API, where they change
 * it (設計_005 2.7). After that come the modules' {@link AccountGate}s, such as accepting the latest
 * terms.
 *
 * <p>Runs after authorization, so a request that is not allowed at all still gets {@code
 * FORBIDDEN}.
 */
public class AccountGateFilter extends OncePerRequestFilter {

  private final Problems problems;
  private final List<AccountGate> gates;

  AccountGateFilter(Problems problems, List<AccountGate> gates) {
    this.problems = problems;
    this.gates = List.copyOf(gates);
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    Optional<AuthenticatedAccount> account = AuthenticatedAccount.current();
    if (account.isPresent()) {
      Optional<ProblemDetail> refusal = refusal(account.get(), request.getRequestURI());
      if (refusal.isPresent()) {
        problems.write(response, refusal.get());
        return;
      }
    }
    chain.doFilter(request, response);
  }

  private Optional<ProblemDetail> refusal(AuthenticatedAccount account, String path) {
    if (account.mustChangePassword() && !path.startsWith(account.type().apiPrefix() + "/auth/")) {
      return Optional.of(problems.create(ErrorCode.PASSWORD_CHANGE_REQUIRED, "使用初始密碼登入，請先修改密碼"));
    }
    for (AccountGate gate : gates) {
      Optional<ApiException> refused = gate.check(account, path);
      if (refused.isPresent()) {
        ProblemDetail problem = problems.create(refused.get().code(), refused.get().getMessage());
        refused.get().properties().forEach(problem::setProperty);
        return Optional.of(problem);
      }
    }
    return Optional.empty();
  }
}
