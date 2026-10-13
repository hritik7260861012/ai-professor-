package com.professor.assistant.web;

import com.professor.assistant.security.AppUserDetails;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;

/** Helpers for pulling the {@link AppUserDetails} principal off a request. */
public final class Principals {

  private Principals() {}

  /**
   * Returns the authenticated principal, or throws 401. Every Phase 3
   * endpoint requires a session, so an anonymous caller never reaches the
   * authorization checks that follow.
   */
  public static AppUserDetails require(Authentication authentication) {
    if (authentication != null
        && authentication.getPrincipal() instanceof AppUserDetails principal) {
      return principal;
    }
    throw new BadCredentialsException("Authentication required");
  }
}