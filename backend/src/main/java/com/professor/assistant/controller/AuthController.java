package com.professor.assistant.controller;

import com.professor.assistant.dto.LoginRequest;
import com.professor.assistant.dto.RegisterRequest;
import com.professor.assistant.dto.UserDto;
import com.professor.assistant.exception.AccountSuspendedException;
import com.professor.assistant.repository.UserRepository;
import com.professor.assistant.security.AppUserDetails;
import com.professor.assistant.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Authentication endpoints backed by BCrypt-hashed
 * credentials and the HTTP session.
 *
 * <p>The session cookie (JSESSIONID) is HttpOnly, so no
 * token is ever exposed to JavaScript — nothing is stored
 * in localStorage. State-changing requests additionally
 * require the {@code X-CSRF-TOKEN} header, whose value is
 * read from {@code GET /api/v1/auth/csrf}.
 */
@RestController
@RequestMapping("/api/v1/auth")
@Tag(
    name = "Authentication",
    description = "Registration, sign-in, sign-out, and current session")
public class AuthController {

  private static final Logger log = LoggerFactory.getLogger(AuthController.class);

  private final AuthenticationManager authenticationManager;
  private final AuthService authService;
  private final UserRepository userRepository;
  private final SecurityContextRepository securityContextRepository;
  private final SecurityContextLogoutHandler logoutHandler;

  public AuthController(
      AuthenticationManager authenticationManager,
      AuthService authService,
      UserRepository userRepository,
      SecurityContextRepository securityContextRepository,
      SecurityContextLogoutHandler logoutHandler) {
    this.authenticationManager = authenticationManager;
    this.authService = authService;
    this.userRepository = userRepository;
    this.securityContextRepository = securityContextRepository;
    this.logoutHandler = logoutHandler;
  }

  /**
   * Public registration — always creates a STUDENT account.
   * Requests for TEACHER/ADMIN are rejected with 403.
   */
  @PostMapping(
      value = "/register",
      consumes = MediaType.APPLICATION_JSON_VALUE,
      produces = MediaType.APPLICATION_JSON_VALUE)
  @Operation(
      summary = "Register a new student account",
      description =
          "Creates a STUDENT account. The role may be omitted "
              + "or STUDENT only; TEACHER and ADMIN accounts "
              + "are created by administrators via the admin API.")
  public ResponseEntity<UserDto> register(@Valid @RequestBody RegisterRequest request) {
    UserDto user = authService.register(request);
    log.info("Registered new student {}", user.email());
    return ResponseEntity.status(HttpStatus.CREATED).body(user);
  }

  @PostMapping(
      value = "/login",
      consumes = MediaType.APPLICATION_JSON_VALUE,
      produces = MediaType.APPLICATION_JSON_VALUE)
  @Operation(
      summary = "Sign in",
      description =
          "Verifies credentials and establishes an HttpOnly "
              + "session cookie. Returns the signed-in user.")
  public ResponseEntity<UserDto> login(
      @Valid @RequestBody LoginRequest request,
      HttpServletRequest httpRequest,
      HttpServletResponse httpResponse) {
    String email = request.email().trim().toLowerCase();

    Authentication authentication;
    try {
      authentication =
          authenticationManager.authenticate(
              new UsernamePasswordAuthenticationToken(email, request.password()));
    } catch (AuthenticationException e) {
      if (e instanceof DisabledException) {
        // The account exists but has been suspended by an administrator.
        log.info("Rejected login for suspended account {}", email);
        throw new AccountSuspendedException("This account has been suspended");
      }
      // Deliberately identical for unknown email and wrong password,
      // so the API never reveals whether an account exists.
      log.info("Failed login attempt for {}", email);
      throw new BadCredentialsException("Invalid email or password");
    }

    SecurityContextHolder.getContext().setAuthentication(authentication);
    // Persist to the HttpSession (creating it on demand) so the
    // JSESSIONID cookie is issued and the login survives this request.
    securityContextRepository.saveContext(
        SecurityContextHolder.getContext(), httpRequest, httpResponse);

    UserDto user = toDto(authentication);
    log.info("User signed in: {} ({})", email, user.role());
    return ResponseEntity.ok(user);
  }

  @PostMapping(value = "/logout", produces = MediaType.APPLICATION_JSON_VALUE)
  @Operation(
      summary = "Sign out",
      description = "Invalidates the session and clears the security context.")
  public ResponseEntity<Void> logout(
      HttpServletRequest request, HttpServletResponse response, Authentication authentication) {
    logoutHandler.logout(request, response, authentication);
    SecurityContextHolder.clearContext();
    log.info("User signed out");
    return ResponseEntity.noContent().build();
  }

  @GetMapping(value = "/me", produces = MediaType.APPLICATION_JSON_VALUE)
  @Operation(summary = "Current user", description = "Returns the authenticated user.")
  public ResponseEntity<UserDto> me(Authentication authentication) {
    AppUserDetails principal = requirePrincipal(authentication);
    return userRepository
        .findById(principal.getId())
        .map(authService::toDto)
        .map(ResponseEntity::ok)
        .orElseGet(() -> ResponseEntity.status(HttpStatus.UNAUTHORIZED).build());
  }

  @GetMapping(value = "/csrf", produces = MediaType.APPLICATION_JSON_VALUE)
  @Operation(
      summary = "CSRF token",
      description =
          "Returns the CSRF token for this session. Send its value "
              + "back in the X-CSRF-TOKEN header on POST/PATCH/PUT/DELETE "
              + "requests. The token is held in the session and is never "
              + "exposed to JavaScript as a readable cookie.")
  public ResponseEntity<CsrfToken> csrf(CsrfToken token) {
    // Resolving the token here forces Spring Security to write the
    // XSRF-TOKEN cookie on this response.
    return ResponseEntity.ok(token);
  }

  private UserDto toDto(Authentication authentication) {
    AppUserDetails principal = requirePrincipal(authentication);
    return userRepository
        .findById(principal.getId())
        .map(authService::toDto)
        .orElseThrow(() -> new BadCredentialsException("Invalid email or password"));
  }

  private AppUserDetails requirePrincipal(Authentication authentication) {
    if (authentication != null
        && authentication.getPrincipal() instanceof AppUserDetails principal) {
      return principal;
    }
    throw new BadCredentialsException("Authentication required");
  }
}