package com.professor.assistant.controller;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.professor.assistant.domain.Role;
import com.professor.assistant.domain.User;
import com.professor.assistant.dto.AdminCreateUserRequest;
import com.professor.assistant.dto.ChangeRoleRequest;
import com.professor.assistant.repository.UserRepository;
import com.professor.assistant.security.AppUserDetails;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/** Phase 2 — role-based authorization on the admin API. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminControllerTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;
  @Autowired private UserRepository userRepository;
  @Autowired private PasswordEncoder passwordEncoder;

  private static String uniqueEmail(String prefix) {
    return prefix + "-" + UUID.randomUUID() + "@example.com";
  }

  /** Persists an account and returns its security principal. */
  private AppUserDetails principalWithRole(Role role) {
    String email = uniqueEmail(role.name().toLowerCase());
    User user =
        userRepository.save(
            new User(email, passwordEncoder.encode("super-secret"), "Test " + role, role));
    return new AppUserDetails(userRepository.findById(user.getId()).orElseThrow());
  }

  private String createBody(String email, String role) throws Exception {
    return objectMapper.writeValueAsString(
        new AdminCreateUserRequest("Test User", email, "super-secret", role));
  }

  /* ----------------------------- create accounts --------------------------- */

  @Test
  void adminCanCreatePrivilegedAccounts() throws Exception {
    AppUserDetails admin = principalWithRole(Role.ADMIN);

    for (String role : new String[] {"TEACHER", "ADMIN", "STUDENT"}) {
      String email = uniqueEmail("created");
      mockMvc
          .perform(
              post("/api/v1/admin/users")
                  .with(user(admin))
                  .with(csrf())
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(createBody(email, role)))
          .andExpect(status().isCreated())
          .andExpect(jsonPath("$.email").value(email))
          .andExpect(jsonPath("$.role").value(role))
          .andExpect(jsonPath("$.passwordHash").doesNotExist());
    }
  }

  @Test
  void anonymousCannotReachTheAdminApi() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/admin/users")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(createBody(uniqueEmail("anon"), "TEACHER")))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.status").value(401));
  }

  @Test
  void nonAdminsAreForbidden() throws Exception {
    for (Role role : new Role[] {Role.STUDENT, Role.TEACHER}) {
      mockMvc
          .perform(
              post("/api/v1/admin/users")
                  .with(user(principalWithRole(role)))
                  .with(csrf())
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(createBody(uniqueEmail("nope"), "ADMIN")))
          .andExpect(status().isForbidden())
          .andExpect(jsonPath("$.status").value(403));
    }
  }

  @Test
  void createUserValidatesItsPayload() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/admin/users")
                .with(user(principalWithRole(Role.ADMIN)))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        new AdminCreateUserRequest("A", "nope", "short", "GHOST"))))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value("Validation failed"));
  }

  /* ------------------------------ change roles ----------------------------- */

  @Test
  void adminCanPromoteAndDemoteAccounts() throws Exception {
    AppUserDetails admin = principalWithRole(Role.ADMIN);
    AppUserDetails target = principalWithRole(Role.STUDENT);

    mockMvc
        .perform(
            patch("/api/v1/admin/users/{id}/role", target.getId())
                .with(user(admin))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new ChangeRoleRequest("TEACHER"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.role").value("TEACHER"));

    mockMvc
        .perform(
            patch("/api/v1/admin/users/{id}/role", target.getId())
                .with(user(admin))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new ChangeRoleRequest("STUDENT"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.role").value("STUDENT"));
  }

  @Test
  void adminCannotChangeTheirOwnRole() throws Exception {
    AppUserDetails admin = principalWithRole(Role.ADMIN);

    mockMvc
        .perform(
            patch("/api/v1/admin/users/{id}/role", admin.getId())
                .with(user(admin))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new ChangeRoleRequest("STUDENT"))))
        .andExpect(status().isConflict());
  }

  @Test
  void roleChangeOnUnknownUserReturnsNotFound() throws Exception {
    mockMvc
        .perform(
            patch("/api/v1/admin/users/{id}/role", 987654321L)
                .with(user(principalWithRole(Role.ADMIN)))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new ChangeRoleRequest("TEACHER"))))
        .andExpect(status().isNotFound());
  }

  @Test
  void roleChangeRequiresAdmin() throws Exception {
    mockMvc
        .perform(
            patch("/api/v1/admin/users/{id}/role", principalWithRole(Role.STUDENT).getId())
                .with(user(principalWithRole(Role.STUDENT)))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new ChangeRoleRequest("ADMIN"))))
        .andExpect(status().isForbidden());
  }

  /* ----------------------------- other endpoints --------------------------- */

  @Test
  void healthStaysPublic() throws Exception {
    mockMvc
        .perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .get("/api/v1/health"))
        .andExpect(status().isOk());
  }
}