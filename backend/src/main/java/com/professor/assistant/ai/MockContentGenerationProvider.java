package com.professor.assistant.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.professor.assistant.domain.GenerationContentType;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Deterministic provider for tests and local demos.
 *
 * <p>Selected only when {@code app.ai.provider=MOCK} is set explicitly —
 * it is never chosen automatically, so a production deployment with a
 * missing or invalid key reports a configuration error instead of quietly
 * serving fabricated content.
 *
 * <p>Output is derived purely from the request so assertions are stable.
 */
@Component
@ConditionalOnProperty(prefix = "app.ai", name = "provider", havingValue = "MOCK")
public class MockContentGenerationProvider implements ContentGenerationProvider {

  private final ObjectMapper objectMapper;

  /**
   * When non-empty, each entry is an {@link AiProviderException.Kind} name
   * that {@link #generate} throws in order. Lets tests exercise retry and
   * failure paths without a network. Set via {@link #setForcedFailures}.
   */
  private volatile List<String> forcedFailures = List.of();

  public MockContentGenerationProvider(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  void setForcedFailures(List<String> kinds) {
    this.forcedFailures = List.copyOf(kinds);
  }

  private final ThreadLocal<List<String>> pendingFailures = new ThreadLocal<>();

  /** Queues failures for the next N calls on this thread. */
  public void enqueueFailures(String... kinds) {
    pendingFailures.set(List.of(kinds));
  }

  private String takeForcedFailure() {
    List<String> queue = pendingFailures.get();
    if (queue == null || queue.isEmpty()) {
      return null;
    }
    pendingFailures.remove();
    return queue.get(0);
  }

  @Override
  public String name() {
    return "MOCK";
  }

  @Override
  public String model() {
    return "mock-model-v1";
  }

  @Override
  public boolean isAvailable() {
    return true;
  }

  @Override
  public GenerationResult generate(GenerationRequest request) {
    String forced = takeForcedFailure();
    if (forced == null && !forcedFailures.isEmpty()) {
      forced = forcedFailures.get(0);
    }
    if (forced != null) {
      AiProviderException.Kind kind;
      try {
        kind = AiProviderException.Kind.valueOf(forced);
      } catch (IllegalArgumentException e) {
        kind = AiProviderException.Kind.UNKNOWN;
      }
      throw new AiProviderException(kind, "Mock provider failure: " + kind);
    }

    String title = titleFor(request);
    String structured = structuredFor(request);

    String body =
        switch (request.contentType()) {
          case MCQS, PPT_SLIDES, ASSIGNMENTS_QUESTIONS -> structured;
          default -> proseFor(request, title);
        };
    return new GenerationResult(body, structured, name(), model());
  }

  private String titleFor(GenerationRequest request) {
    String scope = request.unitTitle() == null ? "Whole syllabus" : request.unitTitle();
    return scope + " — " + humanise(request.contentType());
  }

  private String proseFor(GenerationRequest request, String title) {
    StringBuilder text = new StringBuilder();
    text.append("# ").append(title).append("\n\n");
    text.append("Subject: ").append(request.subjectCode()).append('\n');
    text.append("Language: ").append(request.language().name().toLowerCase()).append('\n');
    text.append("Detail: ").append(request.detailLevel().name().toLowerCase()).append("\n\n");

    List<String> topics = request.topicLines();
    if (topics == null || topics.isEmpty()) {
      text.append("## Limitation\n\nThe source syllabus lists no topics for this scope.\n");
      return text.toString();
    }
    for (String topic : topics) {
      text.append("## ").append(topic).append("\n\n");
      text.append("Key concept: ").append(topic).append(" is central to this section.\n\n");
      text.append("Example (illustrative, not from the syllabus): a worked illustration ")
          .append("of ").append(topic).append(".\n\n");
      text.append("Summary: ").append(topic).append(" in brief.\n\n");
    }
    if (request.learningOutcomes() != null && !request.learningOutcomes().isEmpty()) {
      text.append("## Learning outcomes\n\n");
      request.learningOutcomes().forEach(outcome -> text.append("- ").append(outcome).append('\n'));
    }
    return text.toString();
  }

  private String structuredFor(GenerationRequest request) {
    return switch (request.contentType()) {
      case MCQS -> {
        List<Map<String, Object>> items =
            IntStream.rangeClosed(1, request.itemCount())
                .mapToObj(
                    i ->
                        Map.<String, Object>of(
                            "question", "Mock question " + i + " for " + request.subjectCode(),
                            "options",
                                Map.of(
                                    "A", "Option A",
                                    "B", "Option B",
                                    "C", "Option C",
                                    "D", "Option D"),
                            "correctAnswer", "A",
                            "explanation", "Option A is correct by definition.",
                            "difficulty", request.difficulty().name(),
                            "topic", request.unitTitle() == null ? "" : request.unitTitle()))
                .toList();
        yield write(Map.of("questions", items));
      }
      case PPT_SLIDES -> {
        List<Map<String, Object>> items =
            IntStream.rangeClosed(1, request.itemCount())
                .mapToObj(
                    i ->
                        Map.<String, Object>of(
                            "title", "Slide " + i,
                            "bullets", List.of("Point one", "Point two"),
                            "speakerNotes", "Expand on point one.",
                            "suggestedDiagram", "A simple labelled diagram."))
                .toList();
        yield write(Map.of("slides", items));
      }
      case ASSIGNMENTS_QUESTIONS -> {
        List<Map<String, Object>> items =
            IntStream.rangeClosed(1, request.itemCount())
                .mapToObj(
                    i ->
                        Map.<String, Object>of(
                            "question", "Mock question " + i,
                            "type",
                                List.of("SHORT_ANSWER", "LONG_ANSWER", "CONCEPTUAL",
                                    "APPLICATION"),
                            "difficulty", request.difficulty().name(),
                            "answer",
                            request.includeAnswerKey() ? "Mock answer key entry " + i : ""))
                .toList();
        yield write(Map.of("questions", items));
      }
      default -> null;
    };
  }

  private String write(Object value) {
    try {
      return objectMapper.writeValueAsString(value);
    } catch (Exception e) {
      throw new AiProviderException(
          AiProviderException.Kind.MALFORMED_RESPONSE, "Mock provider could not serialise.");
    }
  }

  private String humanise(GenerationContentType type) {
    return switch (type) {
      case UNIT_NOTES -> "Unit notes";
      case TEACHER_STUDY_MATERIAL -> "Teacher study material";
      case PPT_SLIDES -> "Slide content";
      case ASSIGNMENTS_QUESTIONS -> "Assignment questions";
      case MCQS -> "Multiple choice questions";
      case QUICK_REVISION -> "Quick revision";
    };
  }
}