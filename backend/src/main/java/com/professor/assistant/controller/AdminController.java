package com.professor.assistant.controller;

import com.professor.assistant.domain.Role;
import com.professor.assistant.dto.AdminCreateUserRequest;
import com.professor.assistant.dto.ChangeRoleRequest;
import com.professor.assistant.dto.UserDto;
import com.professor.assistant.security.AppUserDetails;
import com.professor.assistant.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * User administration. Every endpoint requires the
 * ADMIN role — enforced by Spring Security, not by
 * the frontend.
 */
@RestController
@RequestMapping("/api/v1/admin/users")
@Tag(
    name = "User administration",
    description = "Create and manage privileged accounts (ADMIN only)")
public class AdminController {

  private final AuthService authService;

  public AdminController(AuthService authService) {
    this.authService = authService;
  }

  @PostMapping(
      consumes = "application/json",
      produces = "application/json")
  @Operation(
      summary = "Create an account with a role",
      description =
          "Administrators can create STUDENT, TEACHER or "
              + "ADMIN accounts. Public registration can only "
              + "create STUDENT accounts.")
  public ResponseEntity<UserDto> createUser(
      @Valid @RequestBody AdminCreateUserRequest request) {
    UserDto created = authService.createUser(request);
    return ResponseEntity.status(201).body(created);
  }

  @PatchMapping(
      value = "/{id}/role",
      consumes = "application/json",
      produces = "application/json")
  @Operation(
      summary = "Change a user's role",
      description =
          "Promote or demote an account. Administrators "
              + "cannot change the role of their own account.")
  public ResponseEntity<UserDto> changeRole(
      @PathVariable Long id,
      @Valid @RequestBody ChangeRoleRequest request,
      Authentication authentication) {
    UserDto updated =
        authService.changeRole(id, Role.valueOf(request.role()), currentUserId(authentication));
    return ResponseEntity.ok(updated);
  }

  /** Resolves the acting administrator straight from the security principal. */
  private Long currentUserId(Authentication authentication) {
    if (authentication != null
        && authentication.getPrincipal() instanceof AppUserDetails principal) {
      return principal.getId();
    }
    throw new BadCredentialsException("Authentication required");
  }
}
