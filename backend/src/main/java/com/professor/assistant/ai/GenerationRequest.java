package com.professor.assistant.ai;

import com.professor.assistant.domain.GenerationContentType;
import com.professor.assistant.domain.GenerationDifficulty;
import com.professor.assistant.domain.GenerationDetailLevel;
import com.professor.assistant.domain.GenerationLanguage;

/**
 * A single grounded generation request.
 *
 * <p>Deliberately carries only what the model needs: the unit's own
 * approved structure plus a small amount of display metadata. It never
 * contains student names, emails, or the full syllabus document text.
 *
 * @param subjectCode subject code for context
 * @param subjectName subject name for context
 * @param classCode class code for context
 * @param syllabusVersion the syllabus version this is grounded in
 * @param unitTitle selected unit title, or null for whole-syllabus requests
 * @param topicLines pre-rendered "title (sub: a, b)" lines for the unit
 * @param learningOutcomes learning outcomes, possibly empty
 * @param contentType what to produce
 * @param language output language
 * @param detailLevel how much to produce
 * @param difficulty intended difficulty, where applicable
 * @param itemCount number of questions or slides
 * @param includeAnswerKey whether the teacher asked for marking guidance
 * @param instructions optional extra teacher instruction
 */
public record GenerationRequest(
    String subjectCode,
    String subjectName,
    String classCode,
    Integer syllabusVersion,
    String unitTitle,
    java.util.List<String> topicLines,
    java.util.List<String> learningOutcomes,
    GenerationContentType contentType,
    GenerationLanguage language,
    GenerationDetailLevel detailLevel,
    GenerationDifficulty difficulty,
    int itemCount,
    boolean includeAnswerKey,
    String instructions) {}