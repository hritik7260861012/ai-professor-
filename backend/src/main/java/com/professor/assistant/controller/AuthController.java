package com.professor.assistant.controller;

import com.professor.assistant.dto.LoginRequest;
import com.professor.assistant.dto.RegisterRequest;
import com.professor.assistant.exception.NotImplementedException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Authentication endpoints.
 *
 * <p>Phase 1 deliberately implements validation only: inputs are fully
 * validated server-side, but no accounts are created and no tokens are
 * issued. Real registration/sign-in (password hashing, JWT sessions,
 * email verification) is scheduled for Phase 2.
 */
@RestController
@RequestMapping("/api/v1/auth")
@Tag(
    name = "Authentication",
    description =
        "Account registration and sign-in. Phase 1 validates input and returns "
            + "501 Not Implemented; full authentication is scheduled for Phase 2.")
public class AuthController {

  private static final Logger log = LoggerFactory.getLogger(AuthController.class);

  @PostMapping(
      value = "/register",
      consumes = MediaType.APPLICATION_JSON_VALUE,
      produces = MediaType.APPLICATION_JSON_VALUE)
  @Operation(
      summary = "Register a new account",
      description =
          "Validates the registration payload. Always responds 501 Not Implemented "
              + "in Phase 1 — no account is created.")
  public ResponseEntity<Void> register(@Valid @RequestBody RegisterRequest request) {
    log.info("Registration requested: email={} role={}", request.email(), request.role());
    throw new NotImplementedException(
        "Account registration goes live in Phase 2. Input validation is already enforced.");
  }

  @PostMapping(
      value = "/login",
      consumes = MediaType.APPLICATION_JSON_VALUE,
      produces = MediaType.APPLICATION_JSON_VALUE)
  @Operation(
      summary = "Sign in",
      description =
          "Validates the sign-in payload. Always responds 501 Not Implemented "
              + "in Phase 1 — no session is created.")
  public ResponseEntity<Void> login(@Valid @RequestBody LoginRequest request) {
    log.info("Login requested: email={}", request.email());
    throw new NotImplementedException(
        "Sign-in goes live in Phase 2. Input validation is already enforced.");
  }
}
