package com.professor.assistant.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.professor.assistant.domain.AccountStatus;
import com.professor.assistant.domain.Role;
import com.professor.assistant.domain.User;
import com.professor.assistant.dto.AdminCreateUserRequest;
import com.professor.assistant.dto.RegisterRequest;
import com.professor.assistant.dto.UserDto;
import com.professor.assistant.exception.DuplicateEmailException;
import com.professor.assistant.exception.PrivilegedRoleException;
import com.professor.assistant.repository.UserRepository;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/** Unit-level rules for account creation, hashing and role assignment. */
@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

  private static final String PASSWORD = "super-secret";

  @Mock private UserRepository userRepository;

  @Captor private ArgumentCaptor<User> savedUser;

  private AuthService authService;
  private PasswordEncoder passwordEncoder;

  @BeforeEach
  void setUp() {
    passwordEncoder = new BCryptPasswordEncoder();
    authService = new AuthService(userRepository, passwordEncoder);
  }

  /** Stubs the repository so that save() hands back the entity it was given. */
  private void stubSave() {
    when(userRepository.save(any(User.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
  }

  @Test
  void publicRegistrationAlwaysProducesAStudentAccount() {
    when(userRepository.findByEmail("ada@example.com")).thenReturn(Optional.empty());
    stubSave();

    UserDto created =
        authService.register(
            new RegisterRequest("Ada Lovelace", "Ada@Example.com", PASSWORD, null));

    assertThat(created.role()).isEqualTo("STUDENT");
    assertThat(created.status()).isEqualTo(AccountStatus.ACTIVE.name());

    verify(userRepository).save(savedUser.capture());
    assertThat(savedUser.getValue().getRole()).isEqualTo(Role.STUDENT);
  }

  @Test
  void publicRegistrationHashesThePassword() {
    when(userRepository.findByEmail("ada@example.com")).thenReturn(Optional.empty());
    stubSave();

    authService.register(
        new RegisterRequest("Ada Lovelace", "ada@example.com", PASSWORD, "STUDENT"));

    verify(userRepository).save(savedUser.capture());
    User stored = savedUser.getValue();

    assertThat(stored.getPasswordHash()).isNotEqualTo(PASSWORD);
    assertThat(stored.getPasswordHash()).startsWith("$2");
    assertThat(passwordEncoder.matches(PASSWORD, stored.getPasswordHash())).isTrue();
    assertThat(stored.getFullName()).isEqualTo("Ada Lovelace");
  }

  @Test
  void publicRegistrationNormalizesEmailAndName() {
    when(userRepository.findByEmail("ada@example.com")).thenReturn(Optional.empty());
    stubSave();

    authService.register(
        new RegisterRequest("  Ada Lovelace  ", "  ADA@Example.COM  ", PASSWORD, null));

    verify(userRepository).save(savedUser.capture());
    assertThat(savedUser.getValue().getEmail()).isEqualTo("ada@example.com");
    assertThat(savedUser.getValue().getFullName()).isEqualTo("Ada Lovelace");
  }

  @Test
  void publicRegistrationRefusesTeacherAndAdmin() {
    for (String role : new String[] {"TEACHER", "ADMIN"}) {
      assertThatThrownBy(
              () ->
                  authService.register(
                      new RegisterRequest("Mallory", "mallory@example.com", PASSWORD, role)))
          .isInstanceOf(PrivilegedRoleException.class);
    }

    verify(userRepository, never()).save(any(User.class));
  }

  @Test
  void publicRegistrationRejectsDuplicateEmail() {
    when(userRepository.findByEmail("ada@example.com"))
        .thenReturn(Optional.of(new User("ada@example.com", "hash", "Ada", Role.STUDENT)));

    assertThatThrownBy(
            () ->
                authService.register(
                    new RegisterRequest("Ada", "ada@example.com", PASSWORD, null)))
        .isInstanceOf(DuplicateEmailException.class);

    verify(userRepository, never()).save(any(User.class));
  }

  @Test
  void duplicateEmailFromAConcurrentInsertStillSurfacesAsConflict() {
    when(userRepository.findByEmail("ada@example.com")).thenReturn(Optional.empty());
    when(userRepository.save(any(User.class)))
        .thenThrow(new org.springframework.dao.DataIntegrityViolationException("uk_app_user_email"));

    assertThatThrownBy(
            () ->
                authService.register(
                    new RegisterRequest("Ada", "ada@example.com", PASSWORD, null)))
        .isInstanceOf(DuplicateEmailException.class);
  }

  @Test
  void administratorsMayCreatePrivilegedAccounts() {
    for (Role role : new Role[] {Role.TEACHER, Role.ADMIN}) {
      when(userRepository.findByEmail("bob@example.com")).thenReturn(Optional.empty());
      stubSave();

      UserDto created =
          authService.createUser(
              new AdminCreateUserRequest("Bob", "bob@example.com", PASSWORD, role.name()));

      assertThat(created.role()).isEqualTo(role.name());
    }
  }

  @Test
  void changeRolePromotesAnAccount() {
    User target = new User("ada@example.com", "hash", "Ada", Role.STUDENT);
    when(userRepository.findById(1L)).thenReturn(Optional.of(target));
    stubSave();

    UserDto updated = authService.changeRole(1L, Role.TEACHER, 99L);

    assertThat(updated.role()).isEqualTo("TEACHER");
  }

  @Test
  void changeRoleRefusesSelfPromotion() {
    assertThatThrownBy(() -> authService.changeRole(7L, Role.STUDENT, 7L))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("own account");

    verify(userRepository, never()).save(any(User.class));
  }

  @Test
  void changeRoleRejectsUnknownUser() {
    when(userRepository.findById(404L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> authService.changeRole(404L, Role.TEACHER, 1L))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void toDtoNeverLeaksThePasswordHash() {
    User user = new User("ada@example.com", "$2a$10$hash", "Ada", Role.ADMIN);

    UserDto dto = authService.toDto(user);

    assertThat(dto.email()).isEqualTo("ada@example.com");
    assertThat(dto.role()).isEqualTo("ADMIN");
    assertThat(dto.toString()).doesNotContain("$2a$10$hash");
  }

  @Test
  void bootstrapAdminIsCreatedWithTheAdminRole() {
    when(userRepository.findByEmail("root@example.com")).thenReturn(Optional.empty());
    stubSave();

    UserDto created = authService.createAdmin("Administrator", "root@example.com", PASSWORD);

    assertThat(created.role()).isEqualTo("ADMIN");
    verify(userRepository).save(savedUser.capture());
    assertThat(passwordEncoder.matches(PASSWORD, savedUser.getValue().getPasswordHash())).isTrue();
  }
}