package com.homework.asset.api.exception;

import com.homework.asset.api.dto.ApiEnvelope;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
public class GlobalExceptionHandler {

  private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

  @ExceptionHandler(ApiException.class)
  public ResponseEntity<ApiEnvelope<Void>> handleApiException(ApiException ex) {
    log.warn("ApiException: code={}, message={}", ex.getCode(), ex.getMessage());
    return ResponseEntity.status(ex.getCode())
        .body(ApiEnvelope.error(ex.getCode(), ex.getMessage()));
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<ApiEnvelope<Void>> handleValidation(MethodArgumentNotValidException ex) {
    String msg =
        ex.getBindingResult().getFieldErrors().stream()
            .map(e -> e.getField() + ": " + e.getDefaultMessage())
            .findFirst()
            .orElse("Validation failed");
    return ResponseEntity.badRequest().body(ApiEnvelope.error(400, msg));
  }

  @ExceptionHandler(ConstraintViolationException.class)
  public ResponseEntity<ApiEnvelope<Void>> handleConstraintViolation(
      ConstraintViolationException ex) {
    return ResponseEntity.badRequest().body(ApiEnvelope.error(400, ex.getMessage()));
  }

  @ExceptionHandler(MaxUploadSizeExceededException.class)
  public ResponseEntity<ApiEnvelope<Void>> handleMaxUploadSize(MaxUploadSizeExceededException ex) {
    log.warn("File upload size exceeded: {}", ex.getMessage());
    return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
        .body(ApiEnvelope.error(413, "File size exceeds the maximum allowed limit (10MB)"));
  }

  @ExceptionHandler(NoResourceFoundException.class)
  public ResponseEntity<ApiEnvelope<Void>> handleNoResourceFound(NoResourceFoundException ex) {
    String path = ex.getResourcePath();
    if (path != null && (path.contains("favicon") || path.contains("robots.txt"))) {
      return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
    }
    log.warn("Resource not found: {}", path);
    return ResponseEntity.status(HttpStatus.NOT_FOUND)
        .body(ApiEnvelope.error(404, "Resource not found: " + path));
  }

  /**
   * 方法级 @PreAuthorize 鉴权失败（如 ROLE_USER 调用 ADMIN 接口）。
   * Spring Security 的 ExceptionTranslationFilter 只处理 URL 级拒绝，
   * 方法级拒绝会抛出 AuthorizationDeniedException 被此处理器捕获。
   */
  @ExceptionHandler(AuthorizationDeniedException.class)
  public ResponseEntity<ApiEnvelope<Void>> handleAuthorizationDenied(
      AuthorizationDeniedException ex) {
    log.warn("Authorization denied: {}", ex.getMessage());
    return ResponseEntity.status(HttpStatus.FORBIDDEN)
        .body(ApiEnvelope.error(403, "Permission denied: insufficient role"));
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<ApiEnvelope<Void>> handleGeneral(Exception ex) {
    log.error("Unhandled exception", ex);
    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
        .body(ApiEnvelope.error(500, "Internal server error"));
  }
}
