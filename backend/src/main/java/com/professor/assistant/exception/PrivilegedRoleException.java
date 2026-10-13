package com.professor.assistant.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/** A user tried to register with a role that requires admin powers. */
@ResponseStatus(HttpStatus.FORBIDDEN)
public class PrivilegedRoleException extends RuntimeException {

  public PrivilegedRoleException(String message) {
    super(message);
  }
}
