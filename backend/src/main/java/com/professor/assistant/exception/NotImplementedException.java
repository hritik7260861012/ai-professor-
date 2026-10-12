package com.professor.assistant.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/** Signals a feature that is intentionally not built yet (Phase 1 stubs). */
@ResponseStatus(HttpStatus.NOT_IMPLEMENTED)
public class NotImplementedException extends RuntimeException {

  public NotImplementedException(String message) {
    super(message);
  }
}
