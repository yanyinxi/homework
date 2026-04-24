package com.homework.asset.api.exception;

/** 统一业务异常，由 GlobalExceptionHandler 捕获并返回标准 ApiEnvelope 格式。 */
public class ApiException extends RuntimeException {

  private final int code;

  public ApiException(int code, String message) {
    super(message);
    this.code = code;
  }

  public int getCode() {
    return code;
  }
}
