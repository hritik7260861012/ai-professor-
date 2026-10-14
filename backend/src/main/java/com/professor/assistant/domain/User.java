package com.professor.assistant.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;

/**
 * Platform user. Passwords are stored as BCrypt hashes only —
 * never in plaintext. Emails are stored lowercased (normalized)
 * so lookups are case-insensitive.
 */
@Entity
@Table(
    name = "app_user",
    uniqueConstraints = @UniqueConstraint(columnNames = "email"))
public class User {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false, length = 255)
  private String email;

  @Column(name = "password_hash", nullable = false, length = 255)
  private String passwordHash;

  @Column(name = "full_name", nullable = false, length = 100)
  private String fullName;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private Role role;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private AccountStatus status;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  protected User() {}

  public User(String email, String passwordHash, String fullName, Role role) {
    this(email, passwordHash, fullName, role, AccountStatus.ACTIVE);
  }

  public User(
      String email,
      String passwordHash,
      String fullName,
      Role role,
      AccountStatus status) {
    this.email = email;
    this.passwordHash = passwordHash;
    this.fullName = fullName;
    this.role = role;
    this.status = status;
  }

  @PrePersist
  void onCreate() {
    Instant now = Instant.now();
    this.createdAt = now;
    this.updatedAt = now;
  }

  @PreUpdate
  void onUpdate() {
    this.updatedAt = Instant.now();
  }

  public Long getId() {
    return id;
  }

  public String getEmail() {
    return email;
  }

  public String getPasswordHash() {
    return passwordHash;
  }

  public String getFullName() {
    return fullName;
  }

  public Role getRole() {
    return role;
  }

  public void setRole(Role role) {
    this.role = role;
  }

  /**
   * Replaces the stored password hash.
   *
   * <p>Named rather than exposed as a generic setter so that a password
   * change is always a deliberate act. The hash is expected to be already
   * encoded — this method never sees a raw password.
   *
   * <p>Normal account creation sets the hash once through the constructor;
   * this exists for the local-development reset path.
   */
  public void changePasswordHash(String newHash) {
    this.passwordHash = newHash;
  }

  public AccountStatus getStatus() {
    return status;
  }

  public void setStatus(AccountStatus status) {
    this.status = status;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }
}
