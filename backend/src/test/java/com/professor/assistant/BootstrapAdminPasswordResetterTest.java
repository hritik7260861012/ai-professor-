package com.professor.assistant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.professor.assistant.domain.Role;
import com.professor.assistant.domain.User;
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
 * Local-development admin password reset.
 *
 * <p>The interesting properties are all refusals: that it cannot create an
 * account, cannot touch a non-admin, and that the new secret is stored as a
 * BCrypt hash rather than in the clear.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BootstrapAdminPasswordResetterTest {

  private static final String EMAIL = "reset-test@example.invalid";
  private static final String NEW_PASSWORD = "a-brand-new-local-password";

  @Mock private UserRepository userRepository;

  @Captor private ArgumentCaptor<User> savedUser;

  private AuthService authService;
  private PasswordEncoder passwordEncoder;

  @BeforeEach
  void setUp() {
    passwordEncoder = new BCryptPasswordEncoder();
    authService = new AuthService(userRepository, passwordEncoder);
    when(userRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
  }

  private User user(Role role) {
    return new User(EMAIL, passwordEncoder.encode("the-old-password"), "Administrator", role);
  }

  private AuthService.AdminPasswordReset reset() {
    return authService.resetAdminPassword(EMAIL, NEW_PASSWORD);
  }

  private void resetter() {
    new BootstrapAdminPasswordResetter(authService, EMAIL, NEW_PASSWORD).run(null);
  }

  /* ------------------------------ happy path ------------------------------ */

  @Test
  @DisplayName("An existing admin's password is replaced")
  void resetsExistingAdminPassword() {
    User existing = user(Role.ADMIN);
    when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(existing));

    assertThat(reset()).isEqualTo(AuthService.AdminPasswordReset.RESET);

    verify(userRepository).save(savedUser.capture());
    assertThat(passwordEncoder.matches(NEW_PASSWORD, savedUser.getValue().getPasswordHash()))
        .isTrue();
  }

  @Test
  @DisplayName("The new password is stored hashed, never in the clear")
  void storesHashNotPlaintext() {
    when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user(Role.ADMIN)));

    reset();

    verify(userRepository).save(savedUser.capture());
    String hash = savedUser.getValue().getPasswordHash();
    assertThat(hash).isNotEqualTo(NEW_PASSWORD).startsWith("$2");
  }

  @Test
  @DisplayName("The old password stops working after a reset")
  void oldPasswordNoLongerMatches() {
    User existing = user(Role.ADMIN);
    when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(existing));

    reset();

    assertThat(passwordEncoder.matches("the-old-password", existing.getPasswordHash())).isFalse();
  }

  @Test
  @DisplayName("Resetting preserves the account's role and status")
  void keepsRoleAndStatus() {
    when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user(Role.ADMIN)));

    reset();

    verify(userRepository).save(savedUser.capture());
    assertThat(savedUser.getValue().getRole()).isEqualTo(Role.ADMIN);
  }

  /* --------------------------- safety refusals ---------------------------- */

  @Test
  @DisplayName("A reset never creates a missing account")
  void neverCreatesAnAccount() {
    when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.empty());

    assertThat(reset()).isEqualTo(AuthService.AdminPasswordReset.NO_SUCH_ACCOUNT);

    verify(userRepository, never()).save(any());
  }

  @Test
  @DisplayName("A reset refuses to touch an account that is not an ADMIN")
  void refusesNonAdmin() {
    when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user(Role.STUDENT)));

    assertThat(reset()).isEqualTo(AuthService.AdminPasswordReset.NOT_AN_ADMIN);

    verify(userRepository, never()).save(any());
  }

  @Test
  @DisplayName("A reset refuses to touch a teacher account")
  void refusesTeacher() {
    when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user(Role.TEACHER)));

    assertThat(reset()).isEqualTo(AuthService.AdminPasswordReset.NOT_AN_ADMIN);

    verify(userRepository, never()).save(any());
  }

  /* -------------------------------- runner -------------------------------- */

  @Test
  @DisplayName("The runner applies the reset when the account exists")
  void runnerResetsAdmin() {
    when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user(Role.ADMIN)));

    resetter();

    verify(userRepository).save(savedUser.capture());
    assertThat(passwordEncoder.matches(NEW_PASSWORD, savedUser.getValue().getPasswordHash()))
        .isTrue();
  }

  @Test
  @DisplayName("The runner does nothing without an email or password")
  void runnerRequiresBothValues() {
    new BootstrapAdminPasswordResetter(authService, EMAIL, "  ").run(null);
    new BootstrapAdminPasswordResetter(authService, "", NEW_PASSWORD).run(null);

    verify(userRepository, never()).save(any());
    verify(userRepository, never()).findByEmail(anyString());
  }
}