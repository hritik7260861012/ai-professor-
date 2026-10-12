package com.professor.assistant.web;

import com.professor.assistant.exception.ApiError;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Catches container-level errors (404, 405, ...) that never reach a
 * controller, and rewrites them into the standard {@link ApiError}
 * envelope.
 */
@RestController
public class ErrorController implements org.springframework.boot.web.servlet.error.ErrorController {

  @RequestMapping(value = "/error", produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<ApiError> handleError(HttpServletRequest request) {
    Integer statusCode = (Integer) request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);
    HttpStatus status =
        statusCode != null ? HttpStatus.resolve(statusCode) : HttpStatus.INTERNAL_SERVER_ERROR;
    if (status == null) {
      status = HttpStatus.INTERNAL_SERVER_ERROR;
    }

    String message =
        switch (status) {
          case NOT_FOUND -> "Endpoint not found";
          case METHOD_NOT_ALLOWED -> "HTTP method is not allowed for this endpoint";
          case UNSUPPORTED_MEDIA_TYPE -> "Content type is not supported";
          default -> "Request failed";
        };

    Object exception = request.getAttribute(RequestDispatcher.ERROR_EXCEPTION);
    if (exception instanceof RuntimeException runtime
        && runtime.getMessage() != null
        && !runtime.getMessage().isBlank()) {
      message = runtime.getMessage();
    }

    ApiError body =
        new ApiError(
            Instant.now(),
            status.value(),
            status.getReasonPhrase(),
            message,
            request.getRequestURI(),
            null);
    return ResponseEntity.status(status).body(body);
  }
}
