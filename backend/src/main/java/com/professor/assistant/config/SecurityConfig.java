package com.professor.assistant.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.professor.assistant.repository.UserRepository;
import com.professor.assistant.security.AppUserDetails;
import com.professor.assistant.web.RestAccessDeniedHandler;
import com.professor.assistant.web.RestAuthenticationEntryPoint;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.csrf.HttpSessionCsrfTokenRepository;

/**
 * Phase 2 security configuration.
 *
 * <ul>
 *   <li>Session-based auth with a HttpOnly JSESSIONID cookie
 *       (no tokens in localStorage).</li>
 *   <li>CSRF protection: the token is stored in the HttpSession, fetched
 *       from {@code GET /api/v1/auth/csrf} and echoed back in the
 *       {@code X-CSRF-TOKEN} header on state-changing requests. It is
 *       never readable by JavaScript.</li>
 *   <li>Role-based authorization enforced server-side:
 *       {@code /api/v1/admin/**} requires ADMIN.</li>
 *   <li>Unauthenticated (401) and unauthorized (403) responses
 *       use the standard {@code ApiError} envelope.</li>
 * </ul>
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

  /** Endpoints reachable without a session. */
  public static final String[] PUBLIC_ENDPOINTS = {
    "/api/v1/auth/register",
    "/api/v1/auth/login",
    "/api/v1/auth/csrf",
    "/api/v1/health",
    "/v3/api-docs/**",
    "/swagger-ui/**",
    "/swagger-ui.html"
  };

  /**
   * Header the client must echo the CSRF token back in on
   * POST/PATCH/PUT/DELETE requests.
   */
  public static final String CSRF_HEADER_NAME = "X-CSRF-TOKEN";

  /** Form parameter equivalent of {@link #CSRF_HEADER_NAME}. */
  public static final String CSRF_PARAMETER_NAME = "_csrf";

  @Bean
  public SecurityFilterChain securityFilterChain(
      HttpSecurity http,
      RestAuthenticationEntryPoint entryPoint,
      RestAccessDeniedHandler denied,
      CsrfTokenRepository csrfTokenRepository)
      throws Exception {

    // Spring Security 6 defaults to an XOR-masked CSRF token, so the value
    // a client would need to echo is not the one the endpoint reports.
    // Opting out of the deferred-token attribute makes the resolved token
    // equal the reported one, letting a client read it from
    // GET /api/v1/auth/csrf and send it back verbatim.
    CsrfTokenRequestAttributeHandler csrfRequestHandler = new CsrfTokenRequestAttributeHandler();
    csrfRequestHandler.setCsrfRequestAttributeName(null);

    http
        .cors(Customizer.withDefaults())
        .csrf(csrf -> csrf.csrfTokenRepository(csrfTokenRepository)
            .csrfTokenRequestHandler(csrfRequestHandler))
        .sessionManagement(
            session ->
                session
                    .sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)
                    .sessionFixation()
                    .migrateSession())
        .authorizeHttpRequests(
            auth ->
                auth
                    .requestMatchers(PUBLIC_ENDPOINTS)
                    .permitAll()
                    .requestMatchers("/api/v1/admin/**")
                    .hasRole("ADMIN")
                    .anyRequest()
                    .authenticated())
        .exceptionHandling(
            handlers ->
                handlers.authenticationEntryPoint(entryPoint).accessDeniedHandler(denied))
        // Sign-out is a JSON endpoint (AuthController#logout) so the client
        // receives 204 instead of a redirect to the default login page.
        .logout(AbstractHttpConfigurer::disable);

    return http.build();
  }

  @Bean
  public PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();
  }

  /**
   * CSRF token repository.
   *
   * <p>The token is kept in the {@code HttpSession} and is never exposed to
   * JavaScript as a readable cookie. Clients fetch it from
   * {@code GET /api/v1/auth/csrf} and echo it back in the
   * {@code X-CSRF-TOKEN} header on every state-changing request. This is the
   * Spring Security default behaviour; naming it explicitly keeps the
   * contract documented and testable.
   */
  @Bean
  public CsrfTokenRepository csrfTokenRepository() {
    HttpSessionCsrfTokenRepository repository = new HttpSessionCsrfTokenRepository();
    repository.setHeaderName(CSRF_HEADER_NAME);
    repository.setParameterName(CSRF_PARAMETER_NAME);
    return repository;
  }

  /**
   * Bridges the JPA user store into Spring Security. Emails are
   * normalized before lookup so sign-in is case-insensitive.
   */
  @Bean
  public UserDetailsService userDetailsService(UserRepository userRepository) {
    return email ->
        userRepository
            .findByEmail(email == null ? null : email.trim().toLowerCase())
            .map(AppUserDetails::new)
            .orElseThrow(
                () -> new UsernameNotFoundException("User not found: " + email));
  }

  @Bean
  public AuthenticationManager authenticationManager(
      UserDetailsService userDetailsService, PasswordEncoder passwordEncoder) {
    DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
    provider.setUserDetailsService(userDetailsService);
    provider.setPasswordEncoder(passwordEncoder);
    // Report an unknown email exactly like a wrong password, so the API
    // never reveals whether an account exists.
    provider.setHideUserNotFoundExceptions(true);
    return new ProviderManager(provider);
  }

  /**
   * Explicit session-backed security context. Declaring the bean makes
   * both the filter chain and {@code AuthController#login} persist the
   * authentication to the same HttpSession, which is what turns a
   * successful sign-in into a durable session cookie.
   */
  @Bean
  public SecurityContextRepository securityContextRepository() {
    return new HttpSessionSecurityContextRepository();
  }

  @Bean
  public SecurityContextLogoutHandler securityContextLogoutHandler() {
    return new SecurityContextLogoutHandler();
  }

  @Bean
  public RestAuthenticationEntryPoint authenticationEntryPoint(ObjectMapper objectMapper) {
    return new RestAuthenticationEntryPoint(objectMapper);
  }

  @Bean
  public RestAccessDeniedHandler accessDeniedHandler(ObjectMapper objectMapper) {
    return new RestAccessDeniedHandler(objectMapper);
  }
}