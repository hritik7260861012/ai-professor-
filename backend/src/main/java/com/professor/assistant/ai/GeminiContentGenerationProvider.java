package com.professor.assistant.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.professor.assistant.config.AiGenerationProperties;
import com.professor.assistant.domain.GenerationContentType;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

/**
 * Google Gemini provider, calling the public
 * {@code POST /v1beta/models/{model}:generateContent} endpoint directly.
 *
 * <p>A plain HTTP client is used rather than the vendor SDK so timeouts,
 * retries and error classification stay under this application's control and
 * no new dependency is introduced.
 *
 * <p>The API key is read from configuration only. It is sent as a query
 * parameter (what the REST API expects) and is never logged: the request
 * URL is redacted in every log statement and in error messages.
 */
@Component
@ConditionalOnProperty(
    prefix = "app.ai",
    name = "provider",
    havingValue = "GEMINI",
    matchIfMissing = true)
public class GeminiContentGenerationProvider implements ContentGenerationProvider {

  private static final Logger log = LoggerFactory.getLogger(GeminiContentGenerationProvider.class);

  private final AiGenerationProperties properties;
  private final ObjectMapper objectMapper;
  private final SyllabusPromptBuilder promptBuilder;
  private final RestClient restClient;

  public GeminiContentGenerationProvider(
      AiGenerationProperties properties,
      ObjectMapper objectMapper,
      SyllabusPromptBuilder promptBuilder) {
    this.properties = properties;
    this.objectMapper = objectMapper;
    this.promptBuilder = promptBuilder;

    SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
    factory.setConnectTimeout(Duration.ofSeconds(properties.getTimeoutSeconds()));
    factory.setReadTimeout(Duration.ofSeconds(properties.getTimeoutSeconds()));
    this.restClient = RestClient.builder().requestFactory(factory).build();

    if (properties.isConfigured()) {
      log.info(
          "Gemini content generation enabled (model={}).", properties.getModel());
    } else {
      // Not an error: the application must boot normally without a key.
      log.warn(
          "GEMINI_API_KEY is not set. The application will start and all other "
              + "features work, but AI content generation will report a "
              + "configuration error until a key is provided.");
    }
  }

  @Override
  public String name() {
    return "GEMINI";
  }

  @Override
  public String model() {
    return properties.getModel();
  }

  @Override
  public boolean isAvailable() {
    return properties.isConfigured();
  }

  @Override
  public GenerationResult generate(GenerationRequest request) {
    if (!isAvailable()) {
      throw new AiProviderException(
          AiProviderException.Kind.CONFIGURATION,
          "AI content generation is not configured. Set GEMINI_API_KEY on the server "
              + "to enable it.");
    }

    Map<String, Object> payload = buildPayload(request);
    String responseBody = callProvider(payload);
    return parseResponse(responseBody, request);
  }

  private Map<String, Object> buildPayload(GenerationRequest request) {
    Map<String, Object> generationConfig = new LinkedHashMap<>();
    generationConfig.put("temperature", 0.4);
    if (request.contentType() == GenerationContentType.MCQS
        || request.contentType() == GenerationContentType.PPT_SLIDES
        || request.contentType() == GenerationContentType.ASSIGNMENTS_QUESTIONS) {
      // Ask the model for JSON so the caller can validate the structure
      // instead of parsing prose.
      generationConfig.put("responseMimeType", "application/json");
    }

    return Map.of(
        "systemInstruction",
        Map.of(
            "parts", java.util.List.of(Map.of("text", systemInstructionFor(request)))),
        "contents",
        java.util.List.of(
            Map.of(
                "role", "user",
                "parts", java.util.List.of(Map.of("text", userPromptFor(request))))),
        "generationConfig",
        generationConfig);
  }

  private String callProvider(Map<String, Object> payload) {
    String url =
        properties.getBaseUrl()
            + "/"
            + properties.getApiVersion()
            + "/models/"
            + properties.getModel()
            + ":generateContent";

    try {
      return restClient
          .post()
          // The key goes in the query string because that is what the REST
          // API expects. It is deliberately never logged.
          .uri(url + "?key={apiKey}", properties.getApiKey())
          .contentType(MediaType.APPLICATION_JSON)
          .body(payload)
          .retrieve()
          .body(String.class);
    } catch (ResourceAccessException e) {
      // Most commonly a connect/read timeout.
      throw new AiProviderException(
          AiProviderException.Kind.TIMEOUT,
          "The AI provider did not respond in time. Please try again.",
          e);
    } catch (RestClientResponseException e) {
      throw classify(e);
    } catch (RuntimeException e) {
      throw new AiProviderException(
          AiProviderException.Kind.UNKNOWN, "The AI provider call failed.", e);
    }
  }

  private AiProviderException classify(RestClientResponseException e) {
    int status = e.getStatusCode().value();
    // Never include the response body: it can echo the prompt back.
    if (status == 429) {
      return new AiProviderException(
          AiProviderException.Kind.RATE_LIMITED,
          "The AI provider rate limit was reached. Please try again shortly.",
          e);
    }
    if (status >= 500) {
      return new AiProviderException(
          AiProviderException.Kind.PROVIDER_UNAVAILABLE,
          "The AI provider is temporarily unavailable. Please try again.",
          e);
    }
    if (status == 400 || status == 404) {
      return new AiProviderException(
          AiProviderException.Kind.INVALID_REQUEST,
          "The AI provider rejected the request. Check the configured model name.",
          e);
    }
    if (status == 401 || status == 403) {
      return new AiProviderException(
          AiProviderException.Kind.CONFIGURATION,
          "The AI provider rejected the server's credentials. Check GEMINI_API_KEY.",
          e);
    }
    return new AiProviderException(
        AiProviderException.Kind.UNKNOWN, "The AI provider call failed.", e);
  }

  /** Pulls the first candidate's text, raising a typed error if absent. */
  GenerationResult parseResponse(String responseBody, GenerationRequest request) {
    if (responseBody == null || responseBody.isBlank()) {
      throw new AiProviderException(
          AiProviderException.Kind.MALFORMED_RESPONSE, "The AI provider returned no content.");
    }
    try {
      JsonNode root = objectMapper.readTree(responseBody);
      JsonNode candidates = root.path("candidates");
      if (!candidates.isArray() || candidates.isEmpty()) {
        throw new AiProviderException(
            AiProviderException.Kind.MALFORMED_RESPONSE,
            "The AI provider returned no candidates.");
      }
      JsonNode parts = candidates.get(0).path("content").path("parts");
      StringBuilder text = new StringBuilder();
      for (JsonNode part : parts) {
        if (part.hasNonNull("text")) {
          text.append(part.get("text").asText());
        }
      }
      if (text.isEmpty()) {
        throw new AiProviderException(
            AiProviderException.Kind.MALFORMED_RESPONSE,
            "The AI provider returned an empty response.");
      }

      String rendered = text.toString().strip();
      if (request.contentType().isCountable()) {
        // The model was asked for JSON; keep the raw JSON as the structured
        // payload and use it as the body too, so nothing is lost.
        return new GenerationResult(rendered, rendered, name(), properties.getModel());
      }
      return new GenerationResult(rendered, null, name(), properties.getModel());
    } catch (AiProviderException e) {
      throw e;
    } catch (Exception e) {
      throw new AiProviderException(
          AiProviderException.Kind.MALFORMED_RESPONSE,
          "The AI provider returned a response that could not be read.",
          e);
    }
  }

  private String systemInstructionFor(GenerationRequest request) {
    return promptBuilder.systemInstruction(request);
  }

  private String userPromptFor(GenerationRequest request) {
    return promptBuilder.userPrompt(request);
  }
}