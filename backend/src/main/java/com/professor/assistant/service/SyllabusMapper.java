package com.professor.assistant.service;

import com.professor.assistant.domain.Syllabus;
import com.professor.assistant.domain.SyllabusLearningOutcome;
import com.professor.assistant.domain.SyllabusSubtopic;
import com.professor.assistant.domain.SyllabusTopic;
import com.professor.assistant.domain.SyllabusUnit;
import com.professor.assistant.dto.SyllabusDetailDto;
import com.professor.assistant.dto.SyllabusDto;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Converts {@link Syllabus} entities to their public read models.
 *
 * <p>Must run inside a transaction: the unit tree is lazily loaded and
 * {@code spring.jpa.open-in-view} is disabled.
 */
@Component
public class SyllabusMapper {

  /** Metadata only. Deliberately omits the storage key and any path. */
  public SyllabusDto toDto(Syllabus syllabus) {
    return new SyllabusDto(
        syllabus.getId(),
        syllabus.getAcademicClass().getId(),
        syllabus.getAcademicClass().getCode(),
        syllabus.getAcademicClass().getName(),
        syllabus.getSubject().getId(),
        syllabus.getSubject().getCode(),
        syllabus.getSubject().getName(),
        syllabus.getSemester().getId(),
        syllabus.getSemester().getName(),
        syllabus.getSemester().getAcademicYear().getName(),
        syllabus.getUploadedBy().getId(),
        syllabus.getUploadedBy().getFullName(),
        syllabus.getOriginalFilename(),
        syllabus.getContentType(),
        syllabus.getFileSize(),
        syllabus.getStatus(),
        syllabus.getVersion(),
        syllabus.isCurrent(),
        syllabus.getExtractionError(),
        syllabus.getReviewNote(),
        syllabus.getReviewedBy() == null ? null : syllabus.getReviewedBy().getId(),
        syllabus.getReviewedAt(),
        syllabus.getUploadedAt(),
        syllabus.getUpdatedAt());
  }

  public SyllabusDetailDto toDetail(Syllabus syllabus, String warning) {
    List<SyllabusDetailDto.UnitDto> units = new ArrayList<>();
    for (SyllabusUnit unit : syllabus.getUnits()) {
      List<SyllabusDetailDto.TopicDto> topics = new ArrayList<>();
      for (SyllabusTopic topic : unit.getTopics()) {
        List<String> subtopics = new ArrayList<>();
        for (SyllabusSubtopic subtopic : topic.getSubtopics()) {
          subtopics.add(subtopic.getTitle());
        }
        topics.add(
            new SyllabusDetailDto.TopicDto(
                topic.getId(), topic.getTitle(), topic.getPosition(), subtopics));
      }
      units.add(
          new SyllabusDetailDto.UnitDto(
              unit.getId(), unit.getTitle(), unit.getPosition(), topics));
    }

    List<SyllabusDetailDto.OutcomeDto> outcomes = new ArrayList<>();
    for (SyllabusLearningOutcome outcome : syllabus.getLearningOutcomes()) {
      outcomes.add(
          new SyllabusDetailDto.OutcomeDto(
              outcome.getId(),
              outcome.getStatement(),
              outcome.getPosition(),
              outcome.getUnit() == null ? null : outcome.getUnit().getId()));
    }

    return new SyllabusDetailDto(
        toDto(syllabus), units, outcomes, syllabus.getExtractedText(), warning);
  }
}