package com.marketplace.shared;

import java.net.URI;
import java.util.Locale;
import org.springframework.http.HttpStatus;

/**
 * The platform's error codes (設計_005 2.4). Clients show messages by code, not by parsing text.
 *
 * <p>Codes are added here together with the feature that first returns them.
 */
public enum ErrorCode {
  VALIDATION_FAILED(HttpStatus.BAD_REQUEST, "欄位驗證錯誤"),
  UNAUTHENTICATED(HttpStatus.UNAUTHORIZED, "未登入或權杖無效"),
  INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "帳號或密碼錯誤"),
  ACCOUNT_LOCKED(HttpStatus.UNAUTHORIZED, "帳號暫時鎖定"),
  TERMS_CONSENT_REQUIRED(HttpStatus.FORBIDDEN, "須先同意最新版本的條款"),
  PASSWORD_CHANGE_REQUIRED(HttpStatus.FORBIDDEN, "須先修改密碼"),
  FORBIDDEN(HttpStatus.FORBIDDEN, "沒有權限"),
  NOT_FOUND(HttpStatus.NOT_FOUND, "資源不存在"),
  USERNAME_TAKEN(HttpStatus.CONFLICT, "帳號名稱已被使用"),
  TERMS_NOT_LATEST(HttpStatus.UNPROCESSABLE_CONTENT, "同意的不是最新版本的條款"),
  COMMISSION_NOT_APPLICABLE(HttpStatus.UNPROCESSABLE_CONTENT, "平台自營賣家不設定抽成比例"),

  /** The HTTP method, media type or accepted response type is not supported (405, 406, 415). */
  UNSUPPORTED_REQUEST(HttpStatus.METHOD_NOT_ALLOWED, "不支援的請求"),
  INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "系統錯誤");

  private static final String TYPE_BASE = "https://marketplace.example/errors/";

  private final HttpStatus status;
  private final String title;

  ErrorCode(HttpStatus status, String title) {
    this.status = status;
    this.title = title;
  }

  public HttpStatus status() {
    return status;
  }

  public String title() {
    return title;
  }

  /** The Problem Details {@code type}, for example {@code .../errors/account-locked}. */
  public URI type() {
    return URI.create(TYPE_BASE + name().toLowerCase(Locale.ROOT).replace('_', '-'));
  }
}
