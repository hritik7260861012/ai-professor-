package com.professor.assistant.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.professor.assistant.domain.Role;
import com.professor.assistant.domain.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

@DataJpaTest
@ActiveProfiles("test")
class UserRepositoryTest {

  @Autowired private UserRepository repository;

  @Test
  void persistsAndFindsUserByEmail() {
    User user = new User("ada@example.com", "hash", "Ada Lovelace", Role.STUDENT);

    User saved = repository.save(user);

    assertThat(saved.getId()).isNotNull();
    assertThat(saved.getCreatedAt()).isNotNull();
    assertThat(repository.findByEmail("ada@example.com")).isPresent();
    assertThat(repository.findByEmail("nobody@example.com")).isEmpty();
  }
}
