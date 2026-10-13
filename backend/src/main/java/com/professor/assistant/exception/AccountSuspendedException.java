package com.professor.assistant.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/** Sign-in attempted for a suspended account. */
@ResponseStatus(HttpStatus.FORBIDDEN)
public class AccountSuspendedException extends RuntimeException {

  public AccountSuspendedException(String message) {
    super(message);
  }
}
