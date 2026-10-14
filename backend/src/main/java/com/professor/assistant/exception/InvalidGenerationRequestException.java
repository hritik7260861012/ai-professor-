package com.professor.assistant.exception;

/**
 * A generation request whose options are individually well-formed but
 * collectively invalid — too many items, instructions over the limit, and so
 * on.
 *
 * <p>These bounds are operator-configurable, so they cannot be expressed by
 * the static annotations on the request DTO and have to be enforced in the
 * service layer. They are reported as 400 rather than 404: the request is
 * wrong, not missing. Genuine lookup failures — an unknown syllabus, a unit
 * outside its syllabus — keep their 404.
 */
public class InvalidGenerationRequestException extends RuntimeException {

  public InvalidGenerationRequestException(String message) {
    super(message);
  }
}