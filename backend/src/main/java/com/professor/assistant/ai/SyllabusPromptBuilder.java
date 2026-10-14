package com.professor.assistant.ai;

import com.professor.assistant.domain.GenerationContentType;
import com.professor.assistant.domain.GenerationDetailLevel;
import com.professor.assistant.domain.GenerationDifficulty;
import com.professor.assistant.domain.GenerationLanguage;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * Builds the grounded prompt sent to a provider.
 *
 * <p>Two rules are enforced here rather than trusted to the model:
 *
 * <ol>
 *   <li><b>Minimal scope.</b> Only the selected unit's own structure is
 *       sent — never the full syllabus text, and never any student data.
 *   <li><b>Explicit anti-invention instruction.</b> The model is told to
 *       stay within the supplied topics and to flag gaps instead of filling
 *       them, and to separate general illustrative examples from facts the
 *       syllabus actually asserts.
 * </ol>
 *
 * <p>Kept free of Spring-web concerns so the exact prompt text can be
 * asserted in unit tests.
 */
@Component
public class SyllabusPromptBuilder {

  /** Prepended as the system instruction for every request. */
  public String systemInstruction(GenerationRequest request) {
    return """
        You are an academic content assistant producing material for a college course.

        GROUNDING RULES — these are mandatory and override any general instruction:
        1. Use ONLY the topic names supplied in the SOURCE MATERIAL section. Do not
           introduce topics, subtopics, chapters, formulas, references, citations or
           facts that the source material does not contain.
        2. If the source material is too thin to answer properly, say so explicitly
           under a "Limitation" heading. Never fill a gap with invented detail.
        3. Clearly label any example you add for illustration as
           "Example (illustrative, not from the syllabus)". Such examples must not
           be presented as syllabus facts.
        4. Do not invent exam patterns, marks, textbook names, page numbers or
           author citations.
        5. Do not restate or infer anything about specific students.

        OUTPUT RULES:
        - Reply in %s.
        - Use Markdown headings and bullet lists.
        - Be accurate and appropriately academic; do not pad to hit a length.
        """.formatted(languageInstruction(request.language()));
  }

  /** The user message carrying the syllabus scope and the task. */
  public String userPrompt(GenerationRequest request) {
    StringBuilder prompt = new StringBuilder();

    prompt.append("SOURCE MATERIAL (the authoritative scope for this request)\n");
    prompt.append("-----------------------------------------------\n");
    prompt.append("Subject: ").append(request.subjectCode()).append(" - ")
        .append(request.subjectName()).append('\n');
    prompt.append("Class: ").append(request.classCode()).append('\n');
    if (request.syllabusVersion() != null) {
      prompt.append("Syllabus version: ").append(request.syllabusVersion()).append('\n');
    }
    prompt.append("Scope: ")
        .append(
            request.unitTitle() == null || request.unitTitle().isBlank()
                ? "whole syllabus"
                : request.unitTitle())
        .append("\n\n");

    List<String> topics = request.topicLines();
    if (topics == null || topics.isEmpty()) {
      prompt.append("Topics: (the syllabus does not list any topics for this scope)\n");
    } else {
      prompt.append("Topics:\n");
      for (String line : topics) {
        prompt.append("- ").append(line).append('\n');
      }
    }

    List<String> outcomes = request.learningOutcomes();
    if (outcomes != null && !outcomes.isEmpty()) {
      prompt.append("\nLearning outcomes:\n");
      outcomes.forEach(outcome -> prompt.append("- ").append(outcome).append('\n'));
    }

    prompt.append("\n-----------------------------------------------\n");
    prompt.append("TASK\n");
    prompt.append(taskInstruction(request));
    prompt.append("\n\n").append(constraints(request));

    if (request.instructions() != null && !request.instructions().isBlank()) {
      prompt.append("\nAdditional instruction from the teacher (respect it, but the ")
          .append("grounding rules still apply):\n").append(request.instructions().strip())
          .append('\n');
    }

    return prompt.toString();
  }

  private String taskInstruction(GenerationRequest request) {
    GenerationContentType type = request.contentType();
    return switch (type) {
      case UNIT_NOTES ->
          """
          Write detailed study notes for this scope, structured topic by topic.
          For each topic include: a definition or core idea, key concepts, an
          illustrative example, important points to remember, and a short summary.
          Where a formula is genuinely part of the topic, give it and explain each
          symbol. Describe any diagram that would help, in words, under
          "Diagram (described)".
          """;
      case TEACHER_STUDY_MATERIAL ->
          """
          Write TEACHER study material for this scope. Include: clear concept
          explanations, a suggested teaching outline, prerequisite concepts students
          need first, illustrative examples, common student misconceptions with how to
          address them, and suggested classroom questions (with answers for the
          teacher).
          This material is for the teacher only and is never shown to students.
          """;
      case PPT_SLIDES ->
          """
          Produce slide content for this scope. For each slide give a title, 3-6
          concise bullet points, and speaker notes. Where a diagram would help,
          describe it under "Suggested diagram".
          Produce structured output only.
          """;
      case ASSIGNMENTS_QUESTIONS ->
          """
          Produce an assignment question set for this scope. Include short-answer,
          long-answer, conceptual and application-based questions. Group them by
          topic where the source material has topics, and label a suggested
          difficulty for each.
          """
          + (request.includeAnswerKey()
              ? " Also include a separate answer key with marking guidance. "
                  + "This answer key is for the teacher only.\n"
              : " Do not include answers or marking guidance.\n")
          + " Produce structured output only.";
      case MCQS ->
          """
          Produce multiple-choice questions for this scope. Every question must have
          exactly four options (A, B, C, D) and exactly one intended correct answer,
          plus a short explanation of why that answer is correct and a difficulty
          label. Produce structured output only.
          """;
      case QUICK_REVISION ->
          """
          Produce concise revision material for this scope: key definitions, key
          concepts, important formulas where the source material contains them, key
          facts to memorise, the most important questions, and a short summary per
          topic.
          """;
    };
  }

  private String constraints(GenerationRequest request) {
    GenerationDetailLevel detail = request.detailLevel();
    GenerationDifficulty difficulty = request.difficulty();

    StringBuilder constraints = new StringBuilder("\nCONSTRAINTS\n");
    constraints
        .append("- Detail level: ")
        .append(detail.name().toLowerCase())
        .append(". ")
        .append(
            switch (detail) {
              case CONCISE -> "Keep it very short; only the essentials.";
              case STANDARD -> "Aim for a balanced, classroom-appropriate treatment.";
              case DETAILED -> "Be thorough and cover edge cases explicitly.";
            })
        .append('\n');

    constraints.append("- Intended difficulty: ").append(difficulty.name().toLowerCase()).append(".\n");

    if (request.contentType().isCountable()) {
      constraints
          .append("- Produce exactly ")
          .append(request.itemCount())
          .append(
              request.contentType() == GenerationContentType.PPT_SLIDES
                  ? " slides.\n"
                  : " questions.\n");
    }

    return constraints.toString();
  }

  private String languageInstruction(GenerationLanguage language) {
    return switch (language) {
      case ENGLISH -> "English.";
      case HINDI ->
          """
          Hindi, written in Devanagari script. Keep standard technical terms in
          English where that is the usual convention in Indian higher education.
          """;
      case HINGLISH ->
          """
          Hinglish: Hindi sentence structure and grammar written in Roman script,
          keeping established English technical terms in English.
          """;
    };
  }

  /** Renders a unit's topics as compact "Title (sub: a, b)" lines. */
  public static String renderTopicLine(String topicTitle, List<String> subtopics) {
    if (subtopics == null || subtopics.isEmpty()) {
      return topicTitle;
    }
    String subs = subtopics.stream().filter(s -> s != null && !s.isBlank()).collect(Collectors.joining("; "));
    return subs.isEmpty() ? topicTitle : topicTitle + " (subtopics: " + subs + ")";
  }
}