package com.professor.assistant.controller;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.professor.assistant.dto.LoginRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ErrorHandlingTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;

  @Test
  void unknownEndpointReturnsStructured404() throws Exception {
    // Authenticated, so the request reaches the dispatcher and exercises the
    // NoHandlerFound -> 404 mapping rather than the security entry point.
    mockMvc
        .perform(get("/api/v1/does-not-exist").with(user("ada@example.com")))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.status").value(404))
        .andExpect(jsonPath("$.error").value("Not Found"))
        .andExpect(jsonPath("$.message").value("Endpoint not found"))
        .andExpect(jsonPath("$.path").value("/api/v1/does-not-exist"));
  }

  @Test
  void unauthenticatedRequestToProtectedEndpointReturnsStructured401() throws Exception {
    mockMvc
        .perform(get("/api/v1/auth/me"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.status").value(401))
        .andExpect(jsonPath("$.error").value("Unauthorized"))
        .andExpect(jsonPath("$.message").value("Authentication required"))
        .andExpect(jsonPath("$.path").value("/api/v1/auth/me"));
  }

  @Test
  void unsupportedMethodReturnsStructured405() throws Exception {
    // DELETE is a state-changing verb, so it needs a CSRF token; with one
    // the request reaches the dispatcher and produces the 405 envelope.
    mockMvc
        .perform(delete("/api/v1/health").with(csrf()))
        .andExpect(status().isMethodNotAllowed())
        .andExpect(jsonPath("$.status").value(405))
        .andExpect(jsonPath("$.error").value("Method Not Allowed"));
  }

  @Test
  void stateChangingRequestWithoutCsrfTokenIsRejected() throws Exception {
    mockMvc
        .perform(delete("/api/v1/health"))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.status").value(403))
        .andExpect(jsonPath("$.message").value("You do not have permission to access this resource"));
  }

  @Test
  void loginWithoutCsrfTokenIsRejected() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        new LoginRequest("ada@example.com", "super-secret"))))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.status").value(403));
  }
}
