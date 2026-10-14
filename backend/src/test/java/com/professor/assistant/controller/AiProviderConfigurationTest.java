package com.professor.assistant.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.professor.assistant.ai.ContentGenerationProvider;
import com.professor.assistant.config.AiGenerationProperties;
import com.professor.assistant.domain.GenerationContentType;
import com.professor.assistant.dto.GenerationRequestDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;

/**
 * The application must boot normally without an AI credential and must say so
 * clearly rather than serving fabricated content.
 *
 * <p>Runs with {@code app.ai.provider=GEMINI} and an empty API key, which is
 * what a freshly cloned deployment looks like. No live call is made: the
 * request is refused before any provider code runs.
 */
@SpringBootTest(
    properties = {
      "app.ai.provider=GEMINI",
      "app.ai.api-key=",
      "app.ai.model=gemini-3.8-flash"
    })
@ActiveProfiles("test")
class AiProviderConfigurationTest extends AbstractContentGenerationTest {

  @Autowired private ContentGenerationProvider provider;
  @Autowired private AiGenerationProperties properties;

  @BeforeEach
  void prepare() throws Exception {
    createApprovedSyllabus();
  }

  @Test
  @DisplayName("The application starts normally with no API key")
  void contextLoadsWithoutKey() {
    // If this test runs at all, the context started: no key is not a fatal
    // startup error, so every other feature stays available.
    assertThat(properties.getApiKey()).isEmpty();
    assertThat(provider.name()).isEqualTo("GEMINI");
  }

  @Test
  @DisplayName("The provider reports itself unavailable without a key")
  void providerIsUnavailable() {
    assertThat(provider.isAvailable()).isFalse();
  }

  @Test
  @DisplayName("A generation request without configuration returns 503, not fake content")
  void generationRefusedWithoutConfiguration() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/teacher/content/generations")
                .with(user(teacher))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        new GenerationRequestDto(
                            approvedSyllabus.getId(), null, GenerationContentType.UNIT_NOTES,
                            null, null, null, 1, null, null))))
        .andExpect(status().isServiceUnavailable())
        .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("not configured")));
  }

  @Test
  @DisplayName("No job is persisted when generation is not configured")
  void noJobIsCreatedWhenUnconfigured() throws Exception {
    long before = jobs.count();

    mockMvc
        .perform(
            post("/api/v1/teacher/content/generations")
                .with(user(teacher))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        new GenerationRequestDto(
                            approvedSyllabus.getId(), null, GenerationContentType.UNIT_NOTES,
                            null, null, null, 1, null, null))))
        .andExpect(status().isServiceUnavailable());

    // Reporting the configuration problem before persisting keeps a
    // misconfigured server from accumulating dead QUEUED jobs.
    assertThat(jobs.count()).isEqualTo(before);
  }

  @Test
  @DisplayName("Non-AI endpoints keep working without an API key")
  void otherFeaturesStillWork() throws Exception {
    mockMvc
        .perform(get("/api/v1/teacher/syllabi").with(user(teacher)))
        .andExpect(status().isOk());
  }

  @Test
  @DisplayName("The API key is never echoed in any response")
  void apiKeyIsNeverExposed() throws Exception {
    String body =
        mockMvc
            .perform(get("/api/v1/teacher/syllabi").with(user(teacher)))
            .andReturn()
            .getResponse()
            .getContentAsString();

    assertThat(properties.getApiKey()).isEmpty();
    assertThat(body).doesNotContain("apiKey").doesNotContain("api-key");
  }
}
