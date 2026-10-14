package com.professor.assistant.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.professor.assistant.domain.GenerationContentType;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * Validates provider output before it is stored.
 *
 * <p>A model can return a question with three options, two correct answers,
 * or prose where JSON was requested. Persisting that would push the problem
 * onto students at read time, so malformed structured content is rejected at
 * the boundary and surfaced as a failed job instead.
 */
@Component
public class StructuredContentValidator {

  private static final Set<String> REQUIRED_MCQ_KEYS =
      Set.of("question", "options", "correctAnswer", "explanation");
  private static final Set<String> VALID_ANSWERS = Set.of("A", "B", "C", "D");

  private final ObjectMapper objectMapper;

  public StructuredContentValidator(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  /**
   * Parses and checks structured payload.
   *
   * @return the canonical JSON to store
   * @throws AiProviderException with MALFORMED_RESPONSE when the payload
   *     cannot be trusted
   */
  public String validate(GenerationContentType type, String structuredJson) {
    if (!type.isCountable()) {
      return null;
    }
    if (structuredJson == null || structuredJson.isBlank()) {
      throw new AiProviderException(
          AiProviderException.Kind.MALFORMED_RESPONSE,
          "The provider did not return structured content for " + type + ".");
    }

    JsonNode root;
    try {
      root = objectMapper.readTree(stripCodeFence(structuredJson));
    } catch (Exception e) {
      throw new AiProviderException(
          AiProviderException.Kind.MALFORMED_RESPONSE,
          "The provider returned malformed JSON for " + type + ".",
          e);
    }
    if (root == null || !root.isObject()) {
      throw new AiProviderException(
          AiProviderException.Kind.MALFORMED_RESPONSE,
          "The provider returned an unexpected structure for " + type + ".");
    }

    return switch (type) {
      case MCQS -> validateMcqs(root);
      case PPT_SLIDES -> validateSlides(root);
      case ASSIGNMENTS_QUESTIONS -> validateQuestions(root);
      default -> null;
    };
  }

  private String validateMcqs(JsonNode root) {
    JsonNode questions = root.path("questions");
    if (!questions.isArray() || questions.isEmpty()) {
      throw new AiProviderException(
          AiProviderException.Kind.MALFORMED_RESPONSE,
          "No multiple-choice questions were returned.");
    }
    List<String> problems = new ArrayList<>();
    for (int i = 0; i < questions.size(); i++) {
      JsonNode question = questions.get(i);
      for (String key : REQUIRED_MCQ_KEYS) {
        if (!question.hasNonNull(key)) {
          problems.add("question " + (i + 1) + " is missing '" + key + "'");
        }
      }
      JsonNode options = question.path("options");
      if (!options.isObject() || options.size() != 4) {
        problems.add("question " + (i + 1) + " must have exactly four options");
      } else {
        Iterator<String> labels = options.fieldNames();
        while (labels.hasNext()) {
          String name = labels.next();
          if (!VALID_ANSWERS.contains(name)) {
            problems.add(
                "question " + (i + 1) + " has an invalid option label '" + name + "'");
          }
        }
      }
      String correct = question.path("correctAnswer").asText("");
      if (!correct.isEmpty() && !VALID_ANSWERS.contains(correct)) {
        problems.add("question " + (i + 1) + " has an invalid correct answer '" + correct + "'");
      }
    }
    if (!problems.isEmpty()) {
      // Summarised, not dumped: provider output can be large.
      throw new AiProviderException(
          AiProviderException.Kind.MALFORMED_RESPONSE,
          "The provider returned malformed multiple-choice questions ("
              + problems.size()
              + " problem(s), first: "
              + problems.get(0)
              + ").");
    }
    return root.toString();
  }

  private String validateSlides(JsonNode root) {
    JsonNode slides = root.path("slides");
    if (!slides.isArray() || slides.isEmpty()) {
      throw new AiProviderException(
          AiProviderException.Kind.MALFORMED_RESPONSE, "No slides were returned.");
    }
    for (int i = 0; i < slides.size(); i++) {
      if (!slides.get(i).hasNonNull("title")) {
        throw new AiProviderException(
            AiProviderException.Kind.MALFORMED_RESPONSE,
            "Slide " + (i + 1) + " is missing a title.");
      }
    }
    return root.toString();
  }

  private String validateQuestions(JsonNode root) {
    JsonNode questions = root.path("questions");
    if (!questions.isArray() || questions.isEmpty()) {
      throw new AiProviderException(
          AiProviderException.Kind.MALFORMED_RESPONSE, "No questions were returned.");
    }
    for (int i = 0; i < questions.size(); i++) {
      if (!questions.get(i).hasNonNull("question")) {
        throw new AiProviderException(
            AiProviderException.Kind.MALFORMED_RESPONSE,
            "Question " + (i + 1) + " is missing its question text.");
      }
    }
    return root.toString();
  }

  /** Models often wrap JSON in a fenced code block despite being asked not to. */
  private String stripCodeFence(String text) {
    String trimmed = text.strip();
    if (!trimmed.startsWith("```")) {
      return trimmed;
    }
    int firstNewline = trimmed.indexOf('\n');
    int lastFence = trimmed.lastIndexOf("```");
    if (firstNewline < 0 || lastFence <= firstNewline) {
      return trimmed;
    }
    return trimmed.substring(firstNewline + 1, lastFence).strip();
  }
}