package com.professor.assistant.security;

import com.professor.assistant.domain.AccountStatus;
import com.professor.assistant.domain.Role;
import com.professor.assistant.domain.User;
import java.util.Collection;
import java.util.List;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

/**
 * Spring Security principal backed by {@link User}.
 *
 * <p>Carrying the database id on the principal means {@code /auth/me}
 * and the admin endpoints do not need to re-resolve the account by
 * email, which also removes the need for a second query on every
 * authenticated request.
 *
 * <p>A suspended account is reported as disabled ({@link #isEnabled()}
 * returns {@code false}) so Spring Security rejects the sign-in
 * attempt with {@code DisabledException} before a password is even
 * compared.
 *
 * <p>Authorities use the {@code ROLE_} prefix, which is what
 * {@code hasRole("ADMIN")} in {@code SecurityConfig} matches on.
 */
public class AppUserDetails implements UserDetails {

  private static final long serialVersionUID = 1L;

  private final Long id;
  private final String email;
  private final String passwordHash;
  private final String fullName;
  private final Role role;
  private final AccountStatus status;

  public AppUserDetails(User user) {
    this.id = user.getId();
    this.email = user.getEmail();
    this.passwordHash = user.getPasswordHash();
    this.fullName = user.getFullName();
    this.role = user.getRole();
    this.status = user.getStatus();
  }

  public Long getId() {
    return id;
  }

  public String getEmail() {
    return email;
  }

  public String getFullName() {
    return fullName;
  }

  public Role getRole() {
    return role;
  }

  public AccountStatus getStatus() {
    return status;
  }

  @Override
  public Collection<? extends GrantedAuthority> getAuthorities() {
    return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
  }

  @Override
  public String getPassword() {
    return passwordHash;
  }

  @Override
  public String getUsername() {
    return email;
  }

  @Override
  public boolean isAccountNonExpired() {
    return true;
  }

  @Override
  public boolean isAccountNonLocked() {
    return true;
  }

  @Override
  public boolean isCredentialsNonExpired() {
    return true;
  }

  @Override
  public boolean isEnabled() {
    return status == AccountStatus.ACTIVE;
  }
}