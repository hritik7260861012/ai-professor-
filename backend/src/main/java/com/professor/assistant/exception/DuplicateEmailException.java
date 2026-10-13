package com.professor.assistant.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/** Registration attempted with an email that is already taken. */
@ResponseStatus(HttpStatus.CONFLICT)
public class DuplicateEmailException extends RuntimeException {

  public DuplicateEmailException(String message) {
    super(message);
  }
}
