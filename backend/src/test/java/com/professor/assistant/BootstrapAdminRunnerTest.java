package com.professor.assistant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.professor.assistant.domain.Role;
import com.professor.assistant.dto.RegisterRequest;
import com.professor.assistant.dto.UserDto;
import com.professor.assistant.exception.PrivilegedRoleException;
import com.professor.assistant.repository.UserRepository;
import com.professor.assistant.service.AuthService;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * The local-development first-admin bootstrap.
 *
 * <p>Plain unit tests with a mocked repository: the behaviour under test is
 * a handful of guard clauses, and a full context would prove nothing extra
 * about them. Hashing is verified against a real BCrypt encoder so the test
 * would fail if the bootstrap ever stored the password in the clear.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BootstrapAdminRunnerTest {

  // Obvious throwaway values: this is a test fixture, never a real account.
  private static final String EMAIL = "bootstrap-test@example.invalid";
  private static final String PASSWORD = "test-only-password";

  @Mock private UserRepository userRepository;

  @Captor private ArgumentCaptor<com.professor.assistant.domain.User> savedUser;

  private AuthService authService;
  private PasswordEncoder passwordEncoder;

  @BeforeEach
  void setUp() {
    passwordEncoder = new BCryptPasswordEncoder();
    authService = new AuthService(userRepository, passwordEncoder);
    when(userRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    when(userRepository.findByEmail(anyString())).thenReturn(Optional.empty());
    when(userRepository.existsByRole(any())).thenReturn(false);
  }

  private BootstrapAdminRunner runner(boolean enabled, String email, String password) {
    return new BootstrapAdminRunner(authService, enabled, email, password);
  }

  /* ----------------------------- master switch ---------------------------- */

  @Test
  @DisplayName("Bootstrap is disabled by default and creates nothing")
  void disabledByDefault() {
    runner(false, EMAIL, PASSWORD).run(null);

    verify(userRepository, never()).save(any());
  }

  @Test
  @DisplayName("Credentials alone do not enable bootstrap; the switch is required")
  void credentialsWithoutSwitchDoNothing() {
    // Both variables are present, but the master switch is off.
    runner(false, EMAIL, PASSWORD).run(null);

    verify(userRepository, never()).save(any());
  }

  @Test
  @DisplayName("Enabling bootstrap without a password creates nothing")
  void enabledWithoutPasswordDoesNothing() {
    runner(true, EMAIL, "  ").run(null);

    verify(userRepository, never()).save(any());
  }

  @Test
  @DisplayName("Enabling bootstrap without an email creates nothing")
  void enabledWithoutEmailDoesNothing() {
    runner(true, "", PASSWORD).run(null);

    verify(userRepository, never()).save(any());
  }

  /* ------------------------------ happy path ------------------------------ */

  @Test
  @DisplayName("An explicitly enabled bootstrap creates one ADMIN account")
  void createsAdminWhenExplicitlyEnabled() {
    runner(true, EMAIL, PASSWORD).run(null);

    verify(userRepository, times(1)).save(savedUser.capture());
    com.professor.assistant.domain.User admin = savedUser.getValue();
    assertThat(admin.getRole()).isEqualTo(Role.ADMIN);
    assertThat(admin.getEmail()).isEqualTo(EMAIL);
  }

  @Test
  @DisplayName("The bootstrap password is stored as a BCrypt hash, never in the clear")
  void passwordIsHashed() {
    runner(true, EMAIL, PASSWORD).run(null);

    verify(userRepository).save(savedUser.capture());
    String hash = savedUser.getValue().getPasswordHash();

    assertThat(hash).isNotEqualTo(PASSWORD).startsWith("$2");
    assertThat(passwordEncoder.matches(PASSWORD, hash)).isTrue();
  }

  /* --------------------------- duplicate prevention ------------------------ */

  @Test
  @DisplayName("An existing administrator is never duplicated")
  void existingAdminIsNotDuplicated() {
    when(userRepository.existsByRole(Role.ADMIN)).thenReturn(true);

    runner(true, EMAIL, PASSWORD).run(null);

    verify(userRepository, never()).save(any());
  }

  @Test
  @DisplayName("An existing account is left alone and its password is not overwritten")
  void existingAccountPasswordIsNotOverwritten() {
    when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(existingUser()));

    runner(true, EMAIL, "a-different-password").run(null);

    verify(userRepository, never()).save(any());
  }

  @Test
  @DisplayName("Running bootstrap repeatedly stays idempotent")
  void repeatedRunsCreateOnlyOneAdmin() {
    BootstrapAdminRunner runner = runner(true, EMAIL, PASSWORD);

    runner.run(null);
    // Second startup with the variables still set: an admin now exists.
    when(userRepository.existsByRole(Role.ADMIN)).thenReturn(true);
    runner.run(null);
    runner.run(null);

    verify(userRepository, times(1)).save(any());
  }

  private com.professor.assistant.domain.User existingUser() {
    return new com.professor.assistant.domain.User(
        EMAIL, passwordEncoder.encode("an-older-password"), "Existing Teacher", Role.TEACHER);
  }

  /* ------------------------ privilege escalation -------------------------- */

  @Test
  @DisplayName("Public registration cannot create an ADMIN account")
  void publicRegistrationCannotSelfPromote() {
    assertThatThrownBy(
            () ->
                authService.register(
                    new RegisterRequest("Mallory", "mallory@example.com", PASSWORD, "ADMIN")))
        .isInstanceOf(PrivilegedRoleException.class);

    verify(userRepository, never()).save(any());
  }

  @Test
  @DisplayName("Public registration defaults to STUDENT when no role is sent")
  void publicRegistrationDefaultsToStudent() {
    UserDto created =
        authService.register(new RegisterRequest("Ada", "ada@example.com", PASSWORD, null));

    assertThat(created.role()).isEqualTo("STUDENT");
    verify(userRepository).save(savedUser.capture());
    assertThat(savedUser.getValue().getRole()).isEqualTo(Role.STUDENT);
  }

  @Test
  @DisplayName("An admin exists check reflects the repository")
  void adminExistsDelegatesToRepository() {
    when(userRepository.existsByRole(Role.ADMIN)).thenReturn(true);

    assertThat(authService.adminExists()).isTrue();
    verify(userRepository).existsByRole(eq(Role.ADMIN));
  }
}