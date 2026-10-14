package com.professor.assistant.exception;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import com.professor.assistant.storage.StorageException;

/**
 * Converts every thrown exception into the {@link ApiError} envelope, so
 * API consumers never see HTML error pages or stack traces.
 *
 * <p>Every handler returns {@link ResponseEntity} with an explicit
 * status — returning a bare POJO would make Spring default to 200 OK.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

  private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

  @ExceptionHandler(NotImplementedException.class)
  public ResponseEntity<ApiError> handleNotImplemented(
      NotImplementedException ex, HttpServletRequest request) {
    log.info("Not implemented: {}", ex.getMessage());
    return ResponseEntity
        .status(HttpStatus.NOT_IMPLEMENTED)
        .body(ApiError.of(HttpStatus.NOT_IMPLEMENTED, ex.getMessage(), request.getRequestURI()));
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<ApiError> handleValidation(
      MethodArgumentNotValidException ex, HttpServletRequest request) {
    List<ApiFieldError> fields =
        ex.getBindingResult().getFieldErrors().stream()
            .map(field -> new ApiFieldError(field.getField(), field.getDefaultMessage()))
            .toList();
    log.debug("Validation failed for {}: {}", request.getRequestURI(), fields);
    return ResponseEntity
        .badRequest()
        .body(
            ApiError.of(
                HttpStatus.BAD_REQUEST,
                "Validation failed",
                request.getRequestURI(),
                Map.of("fields", fields)));
  }

  @ExceptionHandler(HttpMessageNotReadableException.class)
  public ResponseEntity<ApiError> handleUnreadableBody(
      HttpMessageNotReadableException ex, HttpServletRequest request) {
    log.debug("Malformed request body: {}", ex.toString());
    return ResponseEntity
        .badRequest()
        .body(
            ApiError.of(
                HttpStatus.BAD_REQUEST,
                "Request body is malformed or unreadable",
                request.getRequestURI()));
  }

  @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
  public ResponseEntity<ApiError> handleMediaType(
      HttpMediaTypeNotSupportedException ex, HttpServletRequest request) {
    return ResponseEntity
        .status(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
        .body(
            ApiError.of(
                HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                "Content type is not supported",
                request.getRequestURI()));
  }

  @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
  public ResponseEntity<ApiError> handleMethod(
      HttpRequestMethodNotSupportedException ex, HttpServletRequest request) {
    return ResponseEntity
        .status(HttpStatus.METHOD_NOT_ALLOWED)
        .body(
            ApiError.of(
                HttpStatus.METHOD_NOT_ALLOWED,
                "HTTP method is not allowed for this endpoint",
                request.getRequestURI()));
  }

  @ExceptionHandler(NoHandlerFoundException.class)
  public ResponseEntity<ApiError> handleNoHandler(
      NoHandlerFoundException ex, HttpServletRequest request) {
    log.debug("No handler for {} {}", request.getMethod(), request.getRequestURI());
    return ResponseEntity
        .status(HttpStatus.NOT_FOUND)
        .body(
            ApiError.of(HttpStatus.NOT_FOUND, "Endpoint not found", request.getRequestURI()));
  }

  @ExceptionHandler(NoResourceFoundException.class)
  public ResponseEntity<ApiError> handleNoResource(
      NoResourceFoundException ex, HttpServletRequest request) {
    log.debug("No resource for {} {}", request.getMethod(), request.getRequestURI());
    return ResponseEntity
        .status(HttpStatus.NOT_FOUND)
        .body(
            ApiError.of(HttpStatus.NOT_FOUND, "Endpoint not found", request.getRequestURI()));
  }

  /* ------------------------- security exceptions ------------------------ */

  @ExceptionHandler(BadCredentialsException.class)
  public ResponseEntity<ApiError> handleBadCredentials(
      BadCredentialsException ex, HttpServletRequest request) {
    return ResponseEntity
        .status(HttpStatus.UNAUTHORIZED)
        .body(ApiError.of(HttpStatus.UNAUTHORIZED, ex.getMessage(), request.getRequestURI()));
  }

  @ExceptionHandler(DisabledException.class)
  public ResponseEntity<ApiError> handleDisabled(
      DisabledException ex, HttpServletRequest request) {
    return ResponseEntity
        .status(HttpStatus.FORBIDDEN)
        .body(
            ApiError.of(
                HttpStatus.FORBIDDEN, "This account has been suspended", request.getRequestURI()));
  }

  @ExceptionHandler(AccountSuspendedException.class)
  public ResponseEntity<ApiError> handleAccountSuspended(
      AccountSuspendedException ex, HttpServletRequest request) {
    return ResponseEntity
        .status(HttpStatus.FORBIDDEN)
        .body(ApiError.of(HttpStatus.FORBIDDEN, ex.getMessage(), request.getRequestURI()));
  }

  @ExceptionHandler(UsernameNotFoundException.class)
  public ResponseEntity<ApiError> handleUserNotFound(
      UsernameNotFoundException ex, HttpServletRequest request) {
    return ResponseEntity
        .status(HttpStatus.UNAUTHORIZED)
        .body(
            ApiError.of(
                HttpStatus.UNAUTHORIZED, "Invalid email or password", request.getRequestURI()));
  }

  @ExceptionHandler(AccessDeniedException.class)
  public ResponseEntity<ApiError> handleAccessDenied(
      AccessDeniedException ex, HttpServletRequest request) {
    return ResponseEntity
        .status(HttpStatus.FORBIDDEN)
        .body(
            ApiError.of(
                HttpStatus.FORBIDDEN,
                "You do not have permission to access this resource",
                request.getRequestURI()));
  }

  @ExceptionHandler(DuplicateEmailException.class)
  public ResponseEntity<ApiError> handleDuplicateEmail(
      DuplicateEmailException ex, HttpServletRequest request) {
    return ResponseEntity
        .status(HttpStatus.CONFLICT)
        .body(ApiError.of(HttpStatus.CONFLICT, ex.getMessage(), request.getRequestURI()));
  }

  @ExceptionHandler(PrivilegedRoleException.class)
  public ResponseEntity<ApiError> handlePrivilegedRole(
      PrivilegedRoleException ex, HttpServletRequest request) {
    return ResponseEntity
        .status(HttpStatus.FORBIDDEN)
        .body(ApiError.of(HttpStatus.FORBIDDEN, ex.getMessage(), request.getRequestURI()));
  }

  @ExceptionHandler(ResourceNotFoundException.class)
  public ResponseEntity<ApiError> handleNotFoundResource(
      ResourceNotFoundException ex, HttpServletRequest request) {
    return ResponseEntity
        .status(HttpStatus.NOT_FOUND)
        .body(ApiError.of(HttpStatus.NOT_FOUND, ex.getMessage(), request.getRequestURI()));
  }

  @ExceptionHandler(ConflictException.class)
  public ResponseEntity<ApiError> handleConflict(
      ConflictException ex, HttpServletRequest request) {
    return ResponseEntity
        .status(HttpStatus.CONFLICT)
        .body(ApiError.of(HttpStatus.CONFLICT, ex.getMessage(), request.getRequestURI()));
  }

  @ExceptionHandler(InvalidUploadException.class)
  public ResponseEntity<ApiError> handleInvalidUpload(
      InvalidUploadException ex, HttpServletRequest request) {
    return ResponseEntity
        .status(HttpStatus.BAD_REQUEST)
        .body(ApiError.of(HttpStatus.BAD_REQUEST, ex.getMessage(), request.getRequestURI()));
  }

  @ExceptionHandler(InvalidSyllabusStateException.class)
  public ResponseEntity<ApiError> handleInvalidSyllabusState(
      InvalidSyllabusStateException ex, HttpServletRequest request) {
    return ResponseEntity
        .status(HttpStatus.CONFLICT)
        .body(ApiError.of(HttpStatus.CONFLICT, ex.getMessage(), request.getRequestURI()));
  }

  /**
   * The servlet container rejects the body before any of our code runs when
   * it exceeds the multipart limit, so this is the only place that size
   * failure can surface.
   */
  @ExceptionHandler(MaxUploadSizeExceededException.class)
  public ResponseEntity<ApiError> handleUploadTooLarge(
      MaxUploadSizeExceededException ex, HttpServletRequest request) {
    return ResponseEntity
        .status(HttpStatus.PAYLOAD_TOO_LARGE)
        .body(
            ApiError.of(
                HttpStatus.PAYLOAD_TOO_LARGE,
                "The uploaded file is too large for this server",
                request.getRequestURI()));
  }

  @ExceptionHandler(StorageException.class)
  public ResponseEntity<ApiError> handleStorage(
      StorageException ex, HttpServletRequest request) {
    log.warn("Private storage failure for {}", request.getRequestURI());
    return ResponseEntity
        .status(HttpStatus.INTERNAL_SERVER_ERROR)
        .body(
            ApiError.of(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "The stored file could not be accessed",
                request.getRequestURI()));
  }

  /* ------------------------------- Phase 5 -------------------------------- */

  /**
   * No AI provider credential configured. 503 rather than 400, because the
   * request is valid and the condition is server-side; the frontend shows a
   * configuration message rather than pretending generation failed.
   */
  @ExceptionHandler(AiNotConfiguredException.class)
  public ResponseEntity<ApiError> handleAiNotConfigured(
      AiNotConfiguredException ex, HttpServletRequest request) {
    return ResponseEntity
        .status(HttpStatus.SERVICE_UNAVAILABLE)
        .body(ApiError.of(HttpStatus.SERVICE_UNAVAILABLE, ex.getMessage(), request.getRequestURI()));
  }

  /** Cost or capacity guard hit. 429 so clients can distinguish it from bad input. */
  @ExceptionHandler(GenerationQuotaExceededException.class)
  public ResponseEntity<ApiError> handleQuota(
      GenerationQuotaExceededException ex, HttpServletRequest request) {
    return ResponseEntity
        .status(HttpStatus.TOO_MANY_REQUESTS)
        .body(
            ApiError.of(
                HttpStatus.TOO_MANY_REQUESTS,
                ex.getMessage(),
                request.getRequestURI(),
                Map.of("code", ex.errorCode())));
  }

  /**
   * Config-driven bound on a generation request (item count, instruction
   * length). 400, unlike the sibling 404 handler below: nothing is missing,
   * the options the caller sent are simply out of range.
   */
  @ExceptionHandler(InvalidGenerationRequestException.class)
  public ResponseEntity<ApiError> handleInvalidGenerationRequest(
      InvalidGenerationRequestException ex, HttpServletRequest request) {
    return ResponseEntity
        .status(HttpStatus.BAD_REQUEST)
        .body(ApiError.of(HttpStatus.BAD_REQUEST, ex.getMessage(), request.getRequestURI()));
  }

  @ExceptionHandler(DataIntegrityViolationException.class)
  public ResponseEntity<ApiError> handleIntegrity(
      DataIntegrityViolationException ex, HttpServletRequest request) {
    // Log the root cause so the offending constraint is diagnosable. The
    // client only ever sees a generic message.
    Throwable root = ex;
    while (root.getCause() != null) {
      root = root.getCause();
    }
    log.warn("Data integrity violation for {}: {}", request.getRequestURI(), root.toString());
    return ResponseEntity
        .status(HttpStatus.CONFLICT)
        .body(
            ApiError.of(
                HttpStatus.CONFLICT,
                "That record conflicts with existing data",
                request.getRequestURI()));
  }

  @ExceptionHandler(IllegalStateException.class)
  public ResponseEntity<ApiError> handleIllegalState(
      IllegalStateException ex, HttpServletRequest request) {
    return ResponseEntity
        .status(HttpStatus.CONFLICT)
        .body(ApiError.of(HttpStatus.CONFLICT, ex.getMessage(), request.getRequestURI()));
  }

  @ExceptionHandler(IllegalArgumentException.class)
  public ResponseEntity<ApiError> handleIllegalArgument(
      IllegalArgumentException ex, HttpServletRequest request) {
    return ResponseEntity
        .status(HttpStatus.NOT_FOUND)
        .body(ApiError.of(HttpStatus.NOT_FOUND, ex.getMessage(), request.getRequestURI()));
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<ApiError> handleGeneric(Exception ex, HttpServletRequest request) {
    log.error("Unhandled exception for {} {}", request.getMethod(), request.getRequestURI(), ex);
    return ResponseEntity
        .status(HttpStatus.INTERNAL_SERVER_ERROR)
        .body(
            ApiError.of(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "Internal server error",
                request.getRequestURI()));
  }
}
