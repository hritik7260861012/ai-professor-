package com.professor.assistant.service;

import com.professor.assistant.domain.ContentAudience;
import com.professor.assistant.domain.ContentReviewStatus;
import com.professor.assistant.domain.EnrollmentStatus;
import com.professor.assistant.domain.GeneratedContent;
import com.professor.assistant.domain.Role;
import com.professor.assistant.dto.GeneratedContentDto;
import com.professor.assistant.dto.PageResponse;
import com.professor.assistant.dto.StudentContentDto;
import com.professor.assistant.exception.ConflictException;
import com.professor.assistant.exception.ResourceNotFoundException;
import com.professor.assistant.repository.AiGenerationJobRepository;
import com.professor.assistant.repository.EnrollmentRepository;
import com.professor.assistant.repository.GeneratedContentRepository;
import com.professor.assistant.repository.SyllabusRepository;
import com.professor.assistant.repository.TeachingAssignmentRepository;
import com.professor.assistant.repository.UserRepository;
import com.professor.assistant.security.AppUserDetails;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Teacher review of generated artifacts, and the student read path.
 *
 * <p>Ownership follows the same rule as the syllabus: a TEACHER may act on
 * content generated from a syllabus they uploaded and are still assigned
 * to. ADMIN may act on anything. A STUDENT may only ever read, never act.
 *
 * <p>Student reads use a narrow projection ({@link StudentContentDto}) so
 * teacher-only material, answer keys and provider metadata cannot leak even
 * if a filter were wrong.
 */
@Service
public class ContentReviewService {

  private final GeneratedContentRepository contents;
  private final SyllabusRepository syllabi;
  private final TeachingAssignmentRepository assignments;
  private final EnrollmentRepository enrollments;
  private final AiGenerationJobRepository jobs;
  private final UserRepository users;
  private final ContentGenerationMapper mapper;

  public ContentReviewService(
      GeneratedContentRepository contents,
      SyllabusRepository syllabi,
      TeachingAssignmentRepository assignments,
      EnrollmentRepository enrollments,
      AiGenerationJobRepository jobs,
      UserRepository users,
      ContentGenerationMapper mapper) {
    this.contents = contents;
    this.syllabi = syllabi;
    this.assignments = assignments;
    this.enrollments = enrollments;
    this.jobs = jobs;
    this.users = users;
    this.mapper = mapper;
  }

  /* -------------------------------- teacher -------------------------------- */

  @Transactional(readOnly = true)
  public PageResponse<GeneratedContentDto> listForTeacher(
      AppUserDetails principal, Long syllabusId, Pageable pageable) {
    requireSyllabusAccess(principal, syllabusId);
    return PageResponse.from(
        contents.findAllBySyllabusId(syllabusId, pageable).map(mapper::toDto));
  }

  @Transactional
  public GeneratedContentDto update(
      AppUserDetails principal,
      Long contentId,
      String title,
      String body,
      String structuredJson) {
    GeneratedContent content = requireEditable(principal, contentId);
    if (title != null && !title.isBlank()) {
      content.setTitle(title.strip());
    }
    if (body != null) {
      content.setBody(body);
    }
    if (structuredJson != null) {
      content.setStructuredJson(structuredJson);
    }
    return mapper.toDto(contents.save(content));
  }

  /**
   * Approves an artifact.
   *
   * @param releaseToStudents teacher-controlled release switch; ignored for
   *     teacher-only material, which can never be released
   */
  @Transactional
  public GeneratedContentDto approve(
      AppUserDetails principal, Long contentId, boolean releaseToStudents) {
    GeneratedContent content = requireEditable(principal, contentId);
    content.approve(requireUser(principal.getId()), releaseToStudents, Instant.now());
    return mapper.toDto(contents.save(content));
  }

  @Transactional
  public GeneratedContentDto reject(AppUserDetails principal, Long contentId, String reason) {
    GeneratedContent content = requireEditable(principal, contentId);
    content.reject(requireUser(principal.getId()), reason, Instant.now());
    return mapper.toDto(contents.save(content));
  }

  /** Content generated from this syllabus, newest first. */
  @Transactional(readOnly = true)
  public List<GeneratedContentDto> recentForSyllabus(AppUserDetails principal, Long syllabusId) {
    requireSyllabusAccess(principal, syllabusId);
    return contents.findAllBySyllabusIdOrderByCreatedAtDesc(syllabusId).stream()
        .map(mapper::toDto)
        .toList();
  }

  @Transactional(readOnly = true)
  public GeneratedContentDto getForTeacher(AppUserDetails principal, Long contentId) {
    GeneratedContent content =
        contents
            .findById(contentId)
            .orElseThrow(() -> ResourceNotFoundException.of("Content", contentId));
    // Reuse the same ownership rule as editing, minus the editability gate:
    // reading an approved artifact is legitimate.
    requireContentAccess(principal, content);
    return mapper.toDto(content);
  }

  @Transactional
  public void delete(AppUserDetails principal, Long contentId) {
    GeneratedContent content = requireEditable(principal, contentId);
    contents.delete(content);
  }

  /**
   * Ownership check shared by read and write: uploader plus a live teaching
   * assignment, or ADMIN.
   */
  private void requireContentAccess(AppUserDetails principal, GeneratedContent content) {
    if (principal.getRole() == Role.ADMIN) {
      return;
    }
    if (principal.getRole() != Role.TEACHER) {
      throw ResourceNotFoundException.of("Content", content.getId());
    }
    var syllabus = content.getSyllabus();
    if (!syllabus.getUploadedBy().getId().equals(principal.getId())) {
      throw ResourceNotFoundException.of("Content", content.getId());
    }
    if (!assignments.existsByTeacherIdAndAcademicClassIdAndSubjectIdAndSemesterId(
        principal.getId(),
        syllabus.getAcademicClass().getId(),
        syllabus.getSubject().getId(),
        syllabus.getSemester().getId())) {
      throw ResourceNotFoundException.of("Content", content.getId());
    }
  }

  /* -------------------------------- student -------------------------------- */

  /**
   * Released, approved content for classes the student is actively
   * enrolled in. Drafts, rejected content, outdated content and all
   * teacher-only material are excluded by the query plus a defensive check.
   */
  @Transactional(readOnly = true)
  public List<StudentContentDto> listForStudent(AppUserDetails principal) {
    Set<Long> classIds = enrolledClassIds(principal.getId());
    if (classIds.isEmpty()) {
      return List.of();
    }
    return contents.findReleasedForClasses(classIds, ContentReviewStatus.APPROVED).stream()
        .filter(GeneratedContent::isVisibleToStudents)
        .filter(content -> content.getAudience() == ContentAudience.STUDENT)
        .map(mapper::toStudentDto)
        .toList();
  }

  @Transactional(readOnly = true)
  public StudentContentDto getForStudent(AppUserDetails principal, Long contentId) {
    GeneratedContent content =
        contents
            .findById(contentId)
            .orElseThrow(() -> ResourceNotFoundException.of("Content", contentId));

    // Everything below must hold, otherwise the record is reported as
    // non-existent: unreleased, not approved, teacher-only, outdated, or
    // simply for a class the student is not enrolled in.
    if (!content.isVisibleToStudents()) {
      throw ResourceNotFoundException.of("Content", contentId);
    }
    if (!enrolledClassIds(principal.getId()).contains(content.getSyllabus().getAcademicClass().getId())) {
      throw ResourceNotFoundException.of("Content", contentId);
    }
    return mapper.toStudentDto(content);
  }

  /* ------------------------------- helpers --------------------------------- */

  private Set<Long> enrolledClassIds(Long studentId) {
    return new LinkedHashSet<>(
        enrollments.findClassIdsByStudentIdAndStatus(studentId, EnrollmentStatus.ENROLLED));
  }

  private GeneratedContent requireEditable(AppUserDetails principal, Long contentId) {
    GeneratedContent content =
        contents
            .findById(contentId)
            .orElseThrow(() -> ResourceNotFoundException.of("Content", contentId));

    requireContentAccess(principal, content);

    if (content.isStale()) {
      throw new ConflictException(
          "This content was generated from an older version of the syllabus. "
              + "Generate again from the current version before editing it.");
    }
    if (!content.getReviewStatus().isEditable()) {
      throw new ConflictException(
          "This content is " + content.getReviewStatus() + " and can no longer be edited.");
    }
    return content;
  }

  private void requireSyllabusAccess(AppUserDetails principal, Long syllabusId) {
    if (principal.getRole() == Role.ADMIN) {
      return;
    }
    if (principal.getRole() != Role.TEACHER) {
      throw ResourceNotFoundException.of("Syllabus", syllabusId);
    }
    var syllabus =
        syllabi
            .findById(syllabusId)
            .orElseThrow(() -> ResourceNotFoundException.of("Syllabus", syllabusId));
    if (!syllabus.getUploadedBy().getId().equals(principal.getId())) {
      throw ResourceNotFoundException.of("Syllabus", syllabusId);
    }
    if (!assignments.existsByTeacherIdAndAcademicClassIdAndSubjectIdAndSemesterId(
        principal.getId(),
        syllabus.getAcademicClass().getId(),
        syllabus.getSubject().getId(),
        syllabus.getSemester().getId())) {
      throw ResourceNotFoundException.of("Syllabus", syllabusId);
    }
  }

  private com.professor.assistant.domain.User requireUser(Long id) {
    return users.findById(id).orElseThrow(() -> ResourceNotFoundException.of("User", id));
  }
}