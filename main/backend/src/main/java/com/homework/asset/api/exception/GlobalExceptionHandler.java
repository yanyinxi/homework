package com.homework.asset.api.exception;

import com.homework.asset.api.dto.ApiEnvelope;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
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

  @ExceptionHandler(Exception.class)
  public ResponseEntity<ApiEnvelope<Void>> handleGeneral(Exception ex) {
    log.error("Unhandled exception", ex);
    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
        .body(ApiEnvelope.error(500, "Internal server error"));
  }
}
