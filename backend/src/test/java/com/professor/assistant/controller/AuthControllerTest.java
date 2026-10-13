package com.professor.assistant.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.professor.assistant.config.SecurityConfig;
import com.professor.assistant.domain.AccountStatus;
import com.professor.assistant.domain.Role;
import com.professor.assistant.domain.User;
import com.professor.assistant.dto.LoginRequest;
import com.professor.assistant.dto.RegisterRequest;
import com.professor.assistant.repository.UserRepository;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/** Phase 2 — registration, sign-in, sign-out and the current-session endpoint. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthControllerTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;
  @Autowired private UserRepository userRepository;
  @Autowired private PasswordEncoder passwordEncoder;

  private static String uniqueEmail(String prefix) {
    return prefix + "-" + UUID.randomUUID() + "@example.com";
  }

  /* ------------------------------ registration ----------------------------- */

  @Test
  void registerCreatesStudentAccount() throws Exception {
    String email = uniqueEmail("student");

    mockMvc
        .perform(
            post("/api/v1/auth/register")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        new RegisterRequest("Ada Lovelace", email, "super-secret", "STUDENT"))))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.email").value(email))
        .andExpect(jsonPath("$.fullName").value("Ada Lovelace"))
        .andExpect(jsonPath("$.role").value("STUDENT"))
        .andExpect(jsonPath("$.status").value("ACTIVE"))
        // The hash must never be serialized to clients.
        .andExpect(jsonPath("$.passwordHash").doesNotExist())
        .andExpect(jsonPath("$.password").doesNotExist());

    assertThat(userRepository.findByEmail(email)).isPresent();
  }

  @Test
  void registerDefaultsToStudentWhenRoleIsOmitted() throws Exception {
    String email = uniqueEmail("norole");

    mockMvc
        .perform(
            post("/api/v1/auth/register")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        new RegisterRequest("Grace Hopper", email, "super-secret", null))))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.role").value("STUDENT"));
  }

  @Test
  void registerNormalizesEmailToLowercase() throws Exception {
    String email = uniqueEmail("MixedCase");

    mockMvc
        .perform(
            post("/api/v1/auth/register")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        new RegisterRequest("Alan Turing", email.toUpperCase(), "super-secret", null))))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.email").value(email.toLowerCase()));
  }

  @Test
  void registerStoresBcryptHashRatherThanPlaintext() throws Exception {
    String email = uniqueEmail("hashed");
    String password = "super-secret";

    mockMvc
        .perform(
            post("/api/v1/auth/register")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        new RegisterRequest("Edsger Dijkstra", email, password, null))))
        .andExpect(status().isCreated());

    String stored = userRepository.findByEmail(email).orElseThrow().getPasswordHash();

    assertThat(stored).isNotEqualTo(password);
    assertThat(stored).startsWith("$2");
    assertThat(passwordEncoder.matches(password, stored)).isTrue();
  }

  @Test
  void registerRefusesToCreatePrivilegedAccounts() throws Exception {
    for (String privilegedRole : new String[] {"TEACHER", "ADMIN"}) {
      String email = uniqueEmail("evil");

      mockMvc
          .perform(
              post("/api/v1/auth/register")
                  .with(csrf())
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(
                      objectMapper.writeValueAsString(
                          new RegisterRequest("Mallory", email, "super-secret", privilegedRole))))
          .andExpect(status().isForbidden())
          .andExpect(jsonPath("$.status").value(403))
          .andExpect(jsonPath("$.message").value(containsString("STUDENT")));

      // Nothing was persisted for the rejected request.
      assertThat(userRepository.findByEmail(email)).isEmpty();
    }
  }

  @Test
  void registerRejectsDuplicateEmailWithConflict() throws Exception {
    String email = uniqueEmail("dupe");
    RegisterRequest request = new RegisterRequest("Ada Lovelace", email, "super-secret", null);

    mockMvc
        .perform(
            post("/api/v1/auth/register")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isCreated());

    mockMvc
        .perform(
            post("/api/v1/auth/register")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.status").value(409))
        .andExpect(jsonPath("$.message").value(containsString("already exists")));
  }

  @Test
  void registerRejectsInvalidInputWithStructuredErrors() throws Exception {
    RegisterRequest request = new RegisterRequest("A", "not-an-email", "short", "GHOST");

    mockMvc
        .perform(
            post("/api/v1/auth/register")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.status").value(400))
        .andExpect(jsonPath("$.error").value("Bad Request"))
        .andExpect(jsonPath("$.message").value("Validation failed"))
        .andExpect(jsonPath("$.path").value("/api/v1/auth/register"))
        .andExpect(jsonPath("$.details.fields").isArray())
        .andExpect(jsonPath("$.details.fields[?(@.field == 'email')]").exists())
        .andExpect(jsonPath("$.details.fields[?(@.field == 'role')]").exists());
  }

  /* --------------------------------- login --------------------------------- */

  @Test
  void loginEstablishesAnHttpSessionUsableForProtectedEndpoints() throws Exception {
    String email = uniqueEmail("session");
    register(email, "super-secret", Role.STUDENT);

    MvcResult loginResult =
        mockMvc
            .perform(
                post("/api/v1/auth/login")
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        objectMapper.writeValueAsString(
                            new LoginRequest(email, "super-secret"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.email").value(email))
            .andExpect(jsonPath("$.role").value("STUDENT"))
            .andReturn();

    // The whole point of the phase: the login produced a session that the
    // next request picks up, rather than a context that dies with the thread.
    MockHttpSession session = (MockHttpSession) loginResult.getRequest().getSession(false);
    assertThat(session).isNotNull();

    mockMvc
        .perform(get("/api/v1/auth/me").session(session))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.email").value(email))
        .andExpect(jsonPath("$.role").value("STUDENT"));
  }

  @Test
  void loginIsCaseInsensitiveOnEmail() throws Exception {
    String email = uniqueEmail("case");
    register(email, "super-secret", Role.TEACHER);

    mockMvc
        .perform(
            post("/api/v1/auth/login")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        new LoginRequest(email.toUpperCase(), "super-secret"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.role").value("TEACHER"));
  }

  @Test
  void loginRejectsWrongPasswordWithoutLeakingThatTheAccountExists() throws Exception {
    String email = uniqueEmail("wrongpw");
    register(email, "super-secret", Role.STUDENT);

    mockMvc
        .perform(
            post("/api/v1/auth/login")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(new LoginRequest(email, "not-the-password"))))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.message").value("Invalid email or password"));

    // An unknown address must produce the exact same response.
    mockMvc
        .perform(
            post("/api/v1/auth/login")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        new LoginRequest(uniqueEmail("ghost"), "not-the-password"))))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.message").value("Invalid email or password"));
  }

  @Test
  void loginRejectsSuspendedAccountWithForbidden() throws Exception {
    String email = uniqueEmail("suspended");
    User user = register(email, "super-secret", Role.STUDENT);
    user.setStatus(AccountStatus.SUSPENDED);
    userRepository.save(user);

    mockMvc
        .perform(
            post("/api/v1/auth/login")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(new LoginRequest(email, "super-secret"))))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.status").value(403))
        .andExpect(jsonPath("$.message").value("This account has been suspended"));
  }

  @Test
  void loginRejectsInvalidInputWithStructuredErrors() throws Exception {
    LoginRequest request = new LoginRequest("", "12345678");

    mockMvc
        .perform(
            post("/api/v1/auth/login")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value("Validation failed"))
        .andExpect(jsonPath("$.details.fields").isArray());
  }

  /* --------------------------------- logout -------------------------------- */

  @Test
  void logoutInvalidatesTheSession() throws Exception {
    String email = uniqueEmail("bye");
    register(email, "super-secret", Role.STUDENT);

    MockHttpSession session =
        (MockHttpSession)
            mockMvc
                .perform(
                    post("/api/v1/auth/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                            objectMapper.writeValueAsString(
                                new LoginRequest(email, "super-secret"))))
                .andExpect(status().isOk())
                .andReturn()
                .getRequest()
                .getSession(false);

    mockMvc
        .perform(get("/api/v1/auth/me").session(session))
        .andExpect(status().isOk());

    mockMvc.perform(post("/api/v1/auth/logout").with(csrf()).session(session))
        .andExpect(status().isNoContent());

    mockMvc.perform(get("/api/v1/auth/me").session(session)).andExpect(status().isUnauthorized());
  }

  /* ------------------------------ current user ----------------------------- */

  @Test
  void currentUserRequiresAuthentication() throws Exception {
    mockMvc
        .perform(get("/api/v1/auth/me"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.status").value(401));
  }

  @Test
  void csrfEndpointIssuesATokenTheClientCanEchoBack() throws Exception {
    MvcResult result =
        mockMvc.perform(get("/api/v1/auth/csrf")).andExpect(status().isOk()).andReturn();

    String body = result.getResponse().getContentAsString();
    assertThat(body).contains("\"headerName\":\"" + SecurityConfig.CSRF_HEADER_NAME + "\"");
    assertThat(readToken(body)).isNotBlank();

    // The token must never be readable by JavaScript: no CSRF cookie is set.
    assertThat(result.getResponse().getCookie("XSRF-TOKEN")).isNull();
  }

  @Test
  void echoingTheTokenInTheHeaderSatisfiesCsrfProtection() throws Exception {
    MvcResult tokenResponse = mockMvc.perform(get("/api/v1/auth/csrf")).andReturn();
    String token = readToken(tokenResponse.getResponse().getContentAsString());
    assertThat(token).isNotBlank();

    // Same request as the rejected one in ErrorHandlingTest, but now carrying
    // the token in the matching header: it must get through.
    mockMvc
        .perform(
            delete("/api/v1/health")
                .session((MockHttpSession) tokenResponse.getRequest().getSession(false))
                .header(SecurityConfig.CSRF_HEADER_NAME, token))
        .andExpect(status().isMethodNotAllowed());
  }

  private String readToken(String body) {
    try {
      return objectMapper.readTree(body).get("token").asText();
    } catch (Exception e) {
      throw new IllegalStateException("Could not parse CSRF response: " + body, e);
    }
  }

  @Test
  void malformedJsonProducesStructured400() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/auth/login")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{ not json"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.status").value(400))
        .andExpect(jsonPath("$.message").value("Request body is malformed or unreadable"));
  }

  private User register(String email, String password, Role role) throws Exception {
    mockMvc
        .perform(
            post("/api/v1/auth/register")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        new RegisterRequest(
                            "Test User", email, password, role == Role.STUDENT ? "STUDENT" : null))))
        .andExpect(status().isCreated());
    // Privileged roles cannot come from the public endpoint.
    User user = userRepository.findByEmail(email).orElseThrow();
    user.setRole(role);
    return userRepository.save(user);
  }
}