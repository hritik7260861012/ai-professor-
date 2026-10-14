package com.professor.assistant.service;

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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Registration and account management.
 *
 * <p>Security rules enforced here (defense in depth on top of
 * Spring Security):
 * <ul>
 *   <li>Public registration always results in a STUDENT account —
 *       any request for TEACHER/ADMIN is rejected.</li>
 *   <li>Passwords are BCrypt-hashed before storage.</li>
 *   <li>Emails are normalized to lowercase and unique.</li>
 * </ul>
 */
@Service
public class AuthService {

  private static final Logger log = LoggerFactory.getLogger(AuthService.class);

  private final UserRepository userRepository;
  private final PasswordEncoder passwordEncoder;

  public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
    this.userRepository = userRepository;
    this.passwordEncoder = passwordEncoder;
  }

  /**
   * Public registration. The requested role may only be STUDENT
   * (or omitted, which defaults to STUDENT).
   */
  @Transactional
  public UserDto register(RegisterRequest request) {
    Role requestedRole = request.role() == null ? Role.STUDENT : Role.valueOf(request.role());
    if (requestedRole != Role.STUDENT) {
      log.warn(
          "Rejected public registration requesting privileged role {} for {}",
          requestedRole,
          request.email());
      throw new PrivilegedRoleException(
          "Public registration is limited to STUDENT accounts. "
              + "Privileged accounts are created by administrators.");
    }
    return createUser(
        request.fullName(), request.email(), request.password(), Role.STUDENT);
  }

  /** Administrator-only account creation with an explicit role. */
  @Transactional
  public UserDto createUser(AdminCreateUserRequest request) {
    return createUser(
        request.fullName(), request.email(), request.password(), Role.valueOf(request.role()));
  }

  /** Administrator-only role change (promote/demote). */
  @Transactional
  public UserDto changeRole(Long userId, Role newRole, Long actingAdminId) {
    if (userId.equals(actingAdminId)) {
      throw new IllegalStateException("You cannot change the role of your own account");
    }
    User target =
        userRepository
            .findById(userId)
            .orElseThrow(
                () -> new IllegalArgumentException("User not found: " + userId));
    target.setRole(newRole);
    User saved = userRepository.save(target);
    log.info("Admin {} changed role of user {} to {}", actingAdminId, userId, newRole);
    return toDto(saved);
  }

  /**
   * Bootstrap helper used at startup to create the initial
   * administrator account when configured via environment
   * variables (never hardcoded).
   */
  @Transactional
  public UserDto createAdmin(String fullName, String email, String password) {
    return createUser(fullName, email, password, Role.ADMIN);
  }

  /**
   * Whether the deployment already has an administrator.
   *
   * <p>The bootstrap runner consults this so that leaving the bootstrap
   * variables set cannot mint extra administrators on later restarts.
   */
  @Transactional(readOnly = true)
  public boolean adminExists() {
    return userRepository.existsByRole(Role.ADMIN);
  }

  /**
   * Result of a local-development admin password reset.
   *
   * @param outcome what happened, for a clear log line
   */
  public enum AdminPasswordReset {
    RESET,
    NO_SUCH_ACCOUNT,
    NOT_AN_ADMIN
  }

  /**
   * Replaces an existing administrator's password.
   *
   * <p>Exists for local development only, where the bootstrap password is
   * unknown or was changed by hand. It deliberately refuses to create an
   * account: {@link #NO_SUCH_ACCOUNT} is returned rather than provisioning a
   * new admin, so this can never be used to add one.
   *
   * <p>Only an account that already holds {@link Role#ADMIN} may be reset, so
   * a stray variable cannot rewrite a student's password.
   *
   * <p>The new password is hashed with the same BCrypt encoder used
   * everywhere else and is never logged.
   */
  @Transactional
  public AdminPasswordReset resetAdminPassword(String email, String newPassword) {
    User user =
        userRepository
            .findByEmail(normalize(email))
            .orElse(null);
    if (user == null) {
      return AdminPasswordReset.NO_SUCH_ACCOUNT;
    }
    if (user.getRole() != Role.ADMIN) {
      return AdminPasswordReset.NOT_AN_ADMIN;
    }
    // Re-encoding produces a fresh salt, so this also produces a different
    // hash for an identical password. That is harmless and avoids leaking
    // "the password was already this" through the stored value.
    user.changePasswordHash(passwordEncoder.encode(newPassword));
    userRepository.save(user);
    return AdminPasswordReset.RESET;
  }

  public Optional<User> findByEmail(String email) {
    return userRepository.findByEmail(normalize(email));
  }

  public UserDto toDto(User user) {
    return new UserDto(
        user.getId(),
        user.getFullName(),
        user.getEmail(),
        user.getRole().name(),
        user.getStatus().name(),
        user.getCreatedAt());
  }

  private UserDto createUser(String fullName, String email, String password, Role role) {
    String normalizedEmail = normalize(email);
    if (userRepository.findByEmail(normalizedEmail).isPresent()) {
      throw new DuplicateEmailException(
          "An account with this email address already exists");
    }
    User user =
        new User(
            normalizedEmail,
            passwordEncoder.encode(password),
            fullName.trim(),
            role,
            AccountStatus.ACTIVE);
    try {
      User saved = userRepository.save(user);
      log.info("Created {} account for {}", role, normalizedEmail);
      return toDto(saved);
    } catch (DataIntegrityViolationException e) {
      // Race between the pre-check and the unique constraint.
      throw new DuplicateEmailException(
          "An account with this email address already exists");
    }
  }

  private static String normalize(String email) {
    return email == null ? null : email.trim().toLowerCase();
  }
}
