package com.marketplace.shared;

import java.util.List;
import java.util.Map;

/**
 * A failure the API reports to the caller as Problem Details with an {@link ErrorCode}.
 *
 * <p>Services throw it for expected business failures. Methods that must keep their database
 * changes even when they fail, such as counting a failed login, declare {@code noRollbackFor =
 * ApiException.class}.
 */
public class ApiException extends RuntimeException {

  private final ErrorCode code;
  private final Map<String, Object> properties;

  public ApiException(ErrorCode code, String detail) {
    this(code, detail, Map.of());
  }

  /**
   * @param properties extra Problem Details members, for example {@code lockedUntil}
   */
  public ApiException(ErrorCode code, String detail, Map<String, Object> properties) {
    super(detail);
    this.code = code;
    this.properties = Map.copyOf(properties);
  }

  /** A {@code VALIDATION_FAILED} error for one field, in the same shape as bean validation. */
  public static ApiException invalidField(String field, String message) {
    return new ApiException(
        ErrorCode.VALIDATION_FAILED,
        message,
        Map.of("errors", List.of(Map.of("field", field, "message", message))));
  }

  public ErrorCode code() {
    return code;
  }

  public Map<String, Object> properties() {
    return properties;
  }
}
