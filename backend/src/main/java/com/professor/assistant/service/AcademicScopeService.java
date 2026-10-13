package com.professor.assistant.service;

import com.professor.assistant.domain.EnrollmentStatus;
import com.professor.assistant.domain.Role;
import com.professor.assistant.repository.EnrollmentRepository;
import com.professor.assistant.repository.SubjectRepository;
import com.professor.assistant.repository.TeachingAssignmentRepository;
import com.professor.assistant.security.AppUserDetails;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Resolves which parts of the academic structure an account may see.
 *
 * <p>This is the single choke point for Phase 3 authorization:
 *
 * <ul>
 *   <li><b>ADMIN</b> — unrestricted.
 *   <li><b>TEACHER</b> — only classes and subjects appearing in their
 *       {@code teaching_assignment} rows.
 *   <li><b>STUDENT</b> — only classes they are actively enrolled in, and
 *       only the subjects those classes offer.
 * </ul>
 *
 * <p>An empty result set is returned rather than {@code null} for
 * non-admins, so "no access" degrades to an empty list instead of leaking
 * data. Service and controller code must never bypass this class by
 * querying the repositories directly.
 */
@Service
public class AcademicScopeService {

  private final TeachingAssignmentRepository teachingAssignments;
  private final EnrollmentRepository enrollments;
  private final SubjectRepository subjects;

  public AcademicScopeService(
      TeachingAssignmentRepository teachingAssignments,
      EnrollmentRepository enrollments,
      SubjectRepository subjects) {
    this.teachingAssignments = teachingAssignments;
    this.enrollments = enrollments;
    this.subjects = subjects;
  }

  /**
   * The set of class ids visible to the principal. An empty set means
   * "nothing"; callers that need to distinguish this from "everything" use
   * {@link #isUnrestricted}.
   */
  @Transactional(readOnly = true)
  public Set<Long> accessibleClassIds(AppUserDetails principal) {
    if (isUnrestricted(principal)) {
      return null;
    }
    if (principal.getRole() == Role.TEACHER) {
      return new LinkedHashSet<>(teachingAssignments.findClassIdsByTeacherId(principal.getId()));
    }
    return new LinkedHashSet<>(
        enrollments.findClassIdsByStudentIdAndStatus(principal.getId(), EnrollmentStatus.ENROLLED));
  }

  /**
   * The set of subject ids visible to the principal, or {@code null} for
   * unrestricted (ADMIN) access.
   */
  @Transactional(readOnly = true)
  public Set<Long> accessibleSubjectIds(AppUserDetails principal) {
    if (isUnrestricted(principal)) {
      return null;
    }
    if (principal.getRole() == Role.TEACHER) {
      return new LinkedHashSet<>(teachingAssignments.findSubjectIdsByTeacherId(principal.getId()));
    }
    Set<Long> classIds = accessibleClassIds(principal);
    if (classIds.isEmpty()) {
      return Collections.emptySet();
    }
    return new LinkedHashSet<>(subjects.findSubjectIdsForClasses(classIds));
  }

  /** True when the principal may read the given class. */
  @Transactional(readOnly = true)
  public boolean canAccessClass(AppUserDetails principal, Long classId) {
    if (isUnrestricted(principal)) {
      return true;
    }
    Set<Long> classIds = accessibleClassIds(principal);
    return classIds.contains(classId);
  }

  /** True when the principal may read the given subject. */
  @Transactional(readOnly = true)
  public boolean canAccessSubject(AppUserDetails principal, Long subjectId) {
    if (isUnrestricted(principal)) {
      return true;
    }
    Set<Long> subjectIds = accessibleSubjectIds(principal);
    return subjectIds.contains(subjectId);
  }

  /**
   * Subjects the principal may see within one class. For a teacher this is
   * narrowed to the subjects they are actually assigned to teach, so an
   * assignment for one subject never exposes the rest of the syllabus.
   */
  @Transactional(readOnly = true)
  public Set<Long> accessibleSubjectIdsForClass(AppUserDetails principal, Long classId) {
    if (isUnrestricted(principal)) {
      return null;
    }
    if (principal.getRole() == Role.TEACHER) {
      if (!teachingAssignments.findClassIdsByTeacherId(principal.getId()).contains(classId)) {
        return Collections.emptySet();
      }
      return new LinkedHashSet<>(
          teachingAssignments.findSubjectIdsForClass(principal.getId(), classId));
    }
    Set<Long> classIds = accessibleClassIds(principal);
    if (!classIds.contains(classId)) {
      return Collections.emptySet();
    }
    return new LinkedHashSet<>(subjects.findSubjectIdsForClasses(List.of(classId)));
  }

  /**
   * True when the principal may see the class roster (ADMIN, or a teacher
   * assigned to that class).
   */
  @Transactional(readOnly = true)
  public boolean canViewRoster(AppUserDetails principal, Long classId) {
    return canAccessClass(principal, classId);
  }

  private boolean isUnrestricted(AppUserDetails principal) {
    return principal != null && principal.getRole() == Role.ADMIN;
  }
}