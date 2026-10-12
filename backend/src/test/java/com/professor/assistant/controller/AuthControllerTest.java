package com.professor.assistant.controller;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.professor.assistant.dto.LoginRequest;
import com.professor.assistant.dto.RegisterRequest;
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
class AuthControllerTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;

  @Test
  void registerRejectsInvalidInputWithStructuredErrors() throws Exception {
    RegisterRequest request = new RegisterRequest("A", "not-an-email", "short", "GHOST");

    mockMvc
        .perform(
            post("/api/v1/auth/register")
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

  @Test
  void registerReturnsNotImplementedInPhase1() throws Exception {
    RegisterRequest request =
        new RegisterRequest("Ada Lovelace", "ada@example.com", "super-secret", "STUDENT");

    mockMvc
        .perform(
            post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isNotImplemented())
        .andExpect(jsonPath("$.status").value(501))
        .andExpect(jsonPath("$.message").value(containsString("Phase 2")));
  }

  @Test
  void loginRejectsInvalidInputWithStructuredErrors() throws Exception {
    LoginRequest request = new LoginRequest("", "12345678");

    mockMvc
        .perform(
            post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value("Validation failed"))
        .andExpect(jsonPath("$.details.fields").isArray());
  }

  @Test
  void loginReturnsNotImplementedInPhase1() throws Exception {
    LoginRequest request = new LoginRequest("ada@example.com", "super-secret");

    mockMvc
        .perform(
            post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isNotImplemented())
        .andExpect(jsonPath("$.message").value(containsString("Phase 2")));
  }

  @Test
  void malformedJsonProducesStructured400() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{ not json"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.status").value(400))
        .andExpect(jsonPath("$.message").value("Request body is malformed or unreadable"));
  }
}
