package com.professor.assistant.exception;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

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
