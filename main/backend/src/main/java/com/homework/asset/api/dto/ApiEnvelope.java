package com.homework.asset.api.dto;

/** 统一 API 响应包装：{ "code": 0, "message": "ok", "data": T } */
public record ApiEnvelope<T>(int code, String message, T data) {

  public static <T> ApiEnvelope<T> ok(T data) {
    return new ApiEnvelope<>(0, "ok", data);
  }

  public static <T> ApiEnvelope<T> error(int code, String message) {
    return new ApiEnvelope<>(code, message, null);
  }
}
