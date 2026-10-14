package com.professor.assistant.repository;

import com.professor.assistant.domain.Role;
import com.professor.assistant.domain.User;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long> {

  Optional<User> findByEmail(String email);

  /**
   * Whether any account already holds the given role.
   *
   * <p>Used by the bootstrap runner so a deployment that already has an
   * administrator never gains a second one just because the bootstrap
   * environment variables are still set.
   */
  boolean existsByRole(Role role);
}
