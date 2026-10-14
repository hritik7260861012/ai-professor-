package com.professor.assistant.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.professor.assistant.domain.GenerationContentType;
import com.professor.assistant.domain.GenerationDifficulty;
import com.professor.assistant.domain.GenerationDetailLevel;
import com.professor.assistant.domain.GenerationLanguage;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Prompt grounding and structured-output validation — the two places where
 * the application refuses to trust a model.
 */
class SyllabusPromptBuilderTest {

  private final SyllabusPromptBuilder builder = new SyllabusPromptBuilder();

  private GenerationRequest request(
      GenerationContentType type, String unitTitle, List<String> topics) {
    return new GenerationRequest(
        "CS101",
        "Introduction to Computer Science",
        "SEC-A",
        3,
        unitTitle,
        topics,
        List.of("Explain binary representation"),
        type,
        GenerationLanguage.ENGLISH,
        GenerationDetailLevel.STANDARD,
        GenerationDifficulty.INTERMEDIATE,
        5,
        false,
        null);
  }

  /* ------------------------------- grounding ------------------------------- */

  @Test
  @DisplayName("System instruction forbids inventing syllabus content")
  void systemInstructionForbidsInvention() {
    String system = builder.systemInstruction(request(GenerationContentType.UNIT_NOTES, "Unit I", List.of("A")));

    assertThat(system).contains("Do not");
    assertThat(system).contains("invent");
    assertThat(system).contains("Limitation");
    assertThat(system).contains("illustrative, not from the syllabus");
  }

  @Test
  @DisplayName("Prompt carries only the selected unit's topics")
  void promptIsScopedToTheUnit() {
    String prompt =
        builder.userPrompt(
            request(GenerationContentType.UNIT_NOTES, "Unit I: Foundations", List.of("1.1 History")));

    assertThat(prompt).contains("Unit I: Foundations");
    assertThat(prompt).contains("1.1 History");
    // Nothing from another unit leaks in.
    assertThat(prompt).doesNotContain("Unit IX");
  }

  @Test
  @DisplayName("Prompt does not contain personal data")
  void promptHasNoPersonalData() {
    String prompt =
        builder.userPrompt(
            request(GenerationContentType.UNIT_NOTES, "Unit I", List.of("1.1 History")));

    assertThat(prompt).doesNotContain("@example.com");
    assertThat(prompt).doesNotContain("student");
    assertThat(prompt).doesNotContain("password");
  }

  @Test
  @DisplayName("Prompt states the syllabus version it is grounded in")
  void promptIncludesSyllabusVersion() {
    String prompt = builder.userPrompt(request(GenerationContentType.UNIT_NOTES, "Unit I", List.of("x")));

    assertThat(prompt).contains("Syllabus version: 3");
  }

  @Test
  @DisplayName("A syllabus with no topics is stated honestly")
  void emptyTopicsAreDeclared() {
    String prompt =
        builder.userPrompt(request(GenerationContentType.UNIT_NOTES, "Unit I", List.of()));

    assertThat(prompt).contains("does not list any topics");
  }

  @Test
  @DisplayName("Countable types request an exact number of items")
  void countableTypesRequestExactCounts() {
    String prompt =
        builder.userPrompt(
            request(GenerationContentType.MCQS, "Unit I", List.of("1.1 History")));

    assertThat(prompt).contains("exactly 5");
  }

  @Test
  @DisplayName("Teacher-only types say the material is never shown to students")
  void teacherMaterialIsMarkedPrivate() {
    String prompt =
        builder.userPrompt(
            request(GenerationContentType.TEACHER_STUDY_MATERIAL, "Unit I", List.of("1.1 History")));

    assertThat(prompt).contains("never shown to students");
  }

  @Test
  @DisplayName("An answer key is only requested when the teacher asked for one")
  void answerKeyIsConditional() {
    String without =
        builder.userPrompt(request(GenerationContentType.ASSIGNMENTS_QUESTIONS, "Unit I", List.of("t")));
    assertThat(without).contains("Do not include answers");

    GenerationRequest withKey =
        new GenerationRequest(
            "CS101", "Intro", "SEC-A", 1, "Unit I", List.of("t"), List.of(),
            GenerationContentType.ASSIGNMENTS_QUESTIONS, GenerationLanguage.ENGLISH,
            GenerationDetailLevel.STANDARD, GenerationDifficulty.INTERMEDIATE, 5, true, null);
    assertThat(builder.userPrompt(withKey)).contains("answer key");
  }

  @Test
  @DisplayName("Language instruction changes per requested language")
  void languageIsHonoured() {
    GenerationRequest hindi =
        new GenerationRequest(
            "CS101", "Intro", "SEC-A", 1, "Unit I", List.of("t"), List.of(),
            GenerationContentType.UNIT_NOTES, GenerationLanguage.HINDI,
            GenerationDetailLevel.STANDARD, GenerationDifficulty.INTERMEDIATE, 1, false, null);

    assertThat(builder.systemInstruction(hindi)).contains("Devanagari");
    assertThat(builder.systemInstruction(hindi)).doesNotContain("Hinglish:");

    GenerationRequest hinglish =
        new GenerationRequest(
            "CS101", "Intro", "SEC-A", 1, "Unit I", List.of("t"), List.of(),
            GenerationContentType.UNIT_NOTES, GenerationLanguage.HINGLISH,
            GenerationDetailLevel.STANDARD, GenerationDifficulty.INTERMEDIATE, 1, false, null);
    assertThat(builder.systemInstruction(hinglish)).contains("Roman script");
  }

  @Test
  @DisplayName("Topic lines fold in subtopics")
  void topicLinesIncludeSubtopics() {
    assertThat(SyllabusPromptBuilder.renderTopicLine("1.1 History", List.of("early", "later")))
        .isEqualTo("1.1 History (subtopics: early; later)");
    assertThat(SyllabusPromptBuilder.renderTopicLine("1.1 History", List.of())).isEqualTo("1.1 History");
  }

  /* ------------------------------ validation ------------------------------- */

  private final StructuredContentValidator validator = new StructuredContentValidator(new ObjectMapper());

  private static final String GOOD_MCQ =
      """
      {"questions":[
        {"question":"2+2?","options":{"A":"4","B":"3","C":"5","D":"6"},
         "correctAnswer":"A","explanation":"Basic arithmetic.","difficulty":"BEGINNER"}
      ]}
      """;

  @Test
  @DisplayName("Valid MCQs pass")
  void validMcqPasses() {
    assertThat(validator.validate(GenerationContentType.MCQS, GOOD_MCQ)).contains("2+2?");
  }

  @Test
  @DisplayName("An MCQ with three options is rejected")
  void threeOptionsRejected() {
    String bad =
        """
        {"questions":[
          {"question":"2+2?","options":{"A":"4","B":"3","C":"5"},
           "correctAnswer":"A","explanation":"x"}
        ]}
        """;

    assertThatThrownBy(() -> validator.validate(GenerationContentType.MCQS, bad))
        .isInstanceOf(AiProviderException.class)
        .hasMessageContaining("exactly four options");
  }

  @Test
  @DisplayName("An MCQ with an invalid correct answer is rejected")
  void invalidCorrectAnswerRejected() {
    String bad = GOOD_MCQ.replace("\"correctAnswer\":\"A\"", "\"correctAnswer\":\"Z\"");

    assertThatThrownBy(() -> validator.validate(GenerationContentType.MCQS, bad))
        .isInstanceOf(AiProviderException.class)
        .hasMessageContaining("invalid correct answer");
  }

  @Test
  @DisplayName("An MCQ missing its explanation is rejected")
  void missingExplanationRejected() {
    String bad = GOOD_MCQ.replace(",\"explanation\":\"Basic arithmetic.\"", "");

    assertThatThrownBy(() -> validator.validate(GenerationContentType.MCQS, bad))
        .isInstanceOf(AiProviderException.class)
        .hasMessageContaining("explanation");
  }

  @Test
  @DisplayName("Non-JSON where JSON was required is rejected")
  void nonJsonRejected() {
    assertThatThrownBy(() -> validator.validate(GenerationContentType.MCQS, "Sure! Here you go:"))
        .isInstanceOf(AiProviderException.class)
        .hasMessageContaining("malformed JSON");
  }

  @Test
  @DisplayName("A fenced code block is unwrapped before parsing")
  void codeFenceIsStripped() {
    String fenced = "```json\n" + GOOD_MCQ + "\n```";

    assertThat(validator.validate(GenerationContentType.MCQS, fenced)).contains("2+2?");
  }

  @Test
  @DisplayName("Prose-only types need no structured payload")
  void proseTypesSkipValidation() {
    assertThat(validator.validate(GenerationContentType.UNIT_NOTES, null)).isNull();
    assertThat(validator.validate(GenerationContentType.QUICK_REVISION, "anything")).isNull();
  }

  @Test
  @DisplayName("Empty structured results are rejected")
  void emptyStructuredRejected() {
    assertThatThrownBy(() -> validator.validate(GenerationContentType.MCQS, null))
        .isInstanceOf(AiProviderException.class);
    assertThatThrownBy(() -> validator.validate(GenerationContentType.PPT_SLIDES, "{}"))
        .isInstanceOf(AiProviderException.class);
  }
}