package com.professor.assistant.service;

import com.professor.assistant.domain.GeneratedContent;
import com.professor.assistant.dto.GeneratedContentDto;
import com.professor.assistant.dto.StudentContentDto;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Converts {@link GeneratedContent} to its read models.
 *
 * <p>Two projections exist deliberately. {@link #toDto} is the teacher's
 * full view; {@link #toStudentDto} carries only fields a student may see.
 * The student projection has no slot for provider metadata, review notes
 * or an answer key, so a filter mistake cannot leak them.
 */
@Component
public class ContentGenerationMapper {

  public GeneratedContentDto toDto(GeneratedContent content) {
    var syllabus = content.getSyllabus();
    return new GeneratedContentDto(
        content.getId(),
        content.getJobId(),
        syllabus.getId(),
        content.getSyllabusVersion(),
        syllabus.getSubject().getCode(),
        syllabus.getSubject().getName(),
        syllabus.getAcademicClass().getCode(),
        content.getUnitId(),
        content.getUnit() == null ? null : content.getUnit().getTitle(),
        content.getContentType(),
        content.getLanguage(),
        content.getTitle(),
        content.getBody(),
        content.getStructuredJson(),
        content.getAudience(),
        content.hasAnswerKey(),
        content.effectiveReviewStatus(),
        content.isReleasedToStudents(),
        content.getRejectionReason(),
        content.getProviderName(),
        content.getModelName(),
        content.isStale(),
        content.getCreatedAt(),
        content.getUpdatedAt(),
        content.getReviewedAt());
  }

  public StudentContentDto toStudentDto(GeneratedContent content) {
    var syllabus = content.getSyllabus();
    return new StudentContentDto(
        content.getId(),
        syllabus.getId(),
        syllabus.getSubject().getCode(),
        syllabus.getSubject().getName(),
        syllabus.getAcademicClass().getCode(),
        content.getUnitId(),
        content.getUnit() == null ? null : content.getUnit().getTitle(),
        content.getContentType(),
        content.getLanguage(),
        content.getTitle(),
        content.getBody(),
        // A student never receives a payload carrying an answer key.
        stripAnswerKeys(content));
  }

  /**
   * Question sets may include an answer key. When the artifact is flagged as
   * carrying one, the structured payload is withheld from students rather
   * than filtered field by field, so nothing partial escapes.
   */
  private String stripAnswerKeys(GeneratedContent content) {
    if (!content.hasAnswerKey()) {
      return content.getStructuredJson();
    }
    return null;
  }

  List<StudentContentDto> toStudentDtos(List<GeneratedContent> contents) {
    return contents.stream().map(this::toStudentDto).toList();
  }
}