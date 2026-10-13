package com.professor.assistant.service;

import com.professor.assistant.domain.AcademicClass;
import com.professor.assistant.domain.AcademicProgram;
import com.professor.assistant.domain.ClassSubject;
import com.professor.assistant.domain.EnrollmentStatus;
import com.professor.assistant.domain.Role;
import com.professor.assistant.domain.Semester;
import com.professor.assistant.domain.Subject;
import com.professor.assistant.dto.AcademicYearDto;
import com.professor.assistant.dto.ClassDto;
import com.professor.assistant.dto.ClassSubjectDto;
import com.professor.assistant.dto.DepartmentDto;
import com.professor.assistant.dto.EnrollmentDto;
import com.professor.assistant.dto.PageResponse;
import com.professor.assistant.dto.ProgramDto;
import com.professor.assistant.dto.SemesterDto;
import com.professor.assistant.dto.SubjectDto;
import com.professor.assistant.dto.TeachingAssignmentDto;
import com.professor.assistant.exception.ResourceNotFoundException;
import com.professor.assistant.repository.AcademicClassRepository;
import com.professor.assistant.repository.AcademicProgramRepository;
import com.professor.assistant.repository.AcademicYearRepository;
import com.professor.assistant.repository.ClassSubjectRepository;
import com.professor.assistant.repository.DepartmentRepository;
import com.professor.assistant.repository.EnrollmentRepository;
import com.professor.assistant.repository.SemesterRepository;
import com.professor.assistant.repository.SubjectRepository;
import com.professor.assistant.repository.TeachingAssignmentRepository;
import com.professor.assistant.security.AppUserDetails;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Role-scoped read side of the academic structure.
 *
 * <p>Every method that exposes a class or a subject filters through
 * {@link AcademicScopeService}; there is deliberately no "list everything"
 * variant reachable by a non-admin.
 */
@Service
@Transactional(readOnly = true)
public class AcademicQueryService {

  private final DepartmentRepository departments;
  private final AcademicProgramRepository programs;
  private final AcademicYearRepository academicYears;
  private final SemesterRepository semesters;
  private final SubjectRepository subjects;
  private final AcademicClassRepository classes;
  private final ClassSubjectRepository classSubjects;
  private final EnrollmentRepository enrollments;
  private final TeachingAssignmentRepository assignments;
  private final AcademicScopeService scope;
  private final AcademicMapper mapper;

  public AcademicQueryService(
      DepartmentRepository departments,
      AcademicProgramRepository programs,
      AcademicYearRepository academicYears,
      SemesterRepository semesters,
      SubjectRepository subjects,
      AcademicClassRepository classes,
      ClassSubjectRepository classSubjects,
      EnrollmentRepository enrollments,
      TeachingAssignmentRepository assignments,
      AcademicScopeService scope,
      AcademicMapper mapper) {
    this.departments = departments;
    this.programs = programs;
    this.academicYears = academicYears;
    this.semesters = semesters;
    this.subjects = subjects;
    this.classes = classes;
    this.classSubjects = classSubjects;
    this.enrollments = enrollments;
    this.assignments = assignments;
    this.scope = scope;
    this.mapper = mapper;
  }

  /* ------------------------------- catalogue ------------------------------- */
  // Departments, programs, years and semesters are global reference data with
  // no personal information attached, so every authenticated account may read
  // them. Only the *offerings* (classes, subjects, rosters) are scoped.

  public PageResponse<DepartmentDto> listDepartments(Pageable pageable) {
    return PageResponse.from(departments.findAll(pageable).map(mapper::toDto));
  }

  public PageResponse<ProgramDto> listPrograms(Long departmentId, Pageable pageable) {
    Page<AcademicProgram> page = programs.findAll(programByDepartment(departmentId), pageable);
    return PageResponse.from(page.map(mapper::toDto));
  }

  public PageResponse<AcademicYearDto> listAcademicYears(Pageable pageable) {
    return PageResponse.from(academicYears.findAll(pageable).map(mapper::toDto));
  }

  public List<SemesterDto> listSemesters(Long academicYearId) {
    List<Semester> found =
        academicYearId == null
            ? semesters.findAll()
            : semesters.findAllByAcademicYearIdOrderBySemesterNumberAsc(academicYearId);
    return found.stream().map(mapper::toDto).toList();
  }

  /* -------------------------------- classes -------------------------------- */

  public PageResponse<ClassDto> listClasses(
      AppUserDetails principal, Long programId, Long semesterId, Pageable pageable) {

    Set<Long> allowed = scope.accessibleClassIds(principal);
    if (allowed != null && allowed.isEmpty()) {
      // Empty IN () is invalid SQL, so short-circuit to an empty page.
      return PageResponse.from(new PageImpl<>(List.of()));
    }
    Page<AcademicClass> page = classes.findAll(classBy(allowed, programId, semesterId), pageable);
    return PageResponse.from(page.map(entity -> mapper.toDto(entity, activeCounts(page))));
  }

  public ClassDto getClass(AppUserDetails principal, Long classId) {
    requireClassAccess(principal, classId);
    AcademicClass entity =
        classes
            .findById(classId)
            .orElseThrow(() -> ResourceNotFoundException.of("Class", classId));
    return mapper.toDto(entity, activeCounts(List.of(entity)));
  }

  private void requireClassAccess(AppUserDetails principal, Long classId) {
    if (!scope.canAccessClass(principal, classId)) {
      // 404 rather than 403: a non-member must not learn the class exists.
      throw ResourceNotFoundException.of("Class", classId);
    }
  }

  /* ------------------------------ class subjects --------------------------- */

  /**
   * Subjects offered in a class, narrowed to what the principal may see. A
   * teacher only ever receives the subjects they are assigned to teach, so an
   * assignment for one subject does not expose the rest of the syllabus.
   */
  public List<ClassSubjectDto> listClassSubjects(AppUserDetails principal, Long classId) {
    requireClassAccess(principal, classId);
    Set<Long> allowedSubjects = scope.accessibleSubjectIdsForClass(principal, classId);
    List<ClassSubjectDto> result = new ArrayList<>();
    for (ClassSubject link : classSubjects.findAllByAcademicClassIdOrderBySubjectCodeAsc(classId)) {
      if (allowedSubjects == null || allowedSubjects.contains(link.getSubject().getId())) {
        result.add(mapper.toDto(link));
      }
    }
    return result;
  }

  /* -------------------------------- subjects ------------------------------- */

  public PageResponse<SubjectDto> listSubjects(
      AppUserDetails principal, Long departmentId, Pageable pageable) {

    Set<Long> allowed = scope.accessibleSubjectIds(principal);
    if (allowed != null && allowed.isEmpty()) {
      return PageResponse.from(new PageImpl<>(List.of()));
    }
    Page<Subject> page = subjects.findAll(subjectBy(allowed, departmentId), pageable);
    return PageResponse.from(page.map(mapper::toDto));
  }

  public SubjectDto getSubject(AppUserDetails principal, Long subjectId) {
    if (!scope.canAccessSubject(principal, subjectId)) {
      throw ResourceNotFoundException.of("Subject", subjectId);
    }
    return subjects
        .findById(subjectId)
        .map(mapper::toDto)
        .orElseThrow(() -> ResourceNotFoundException.of("Subject", subjectId));
  }

  /* ------------------------- personal academic views ----------------------- */

  /** Classes the signed-in account can work with, regardless of role. */
  public List<ClassDto> myClasses(AppUserDetails principal) {
    Set<Long> allowed = scope.accessibleClassIds(principal);
    if (allowed == null) {
      // ADMIN has no personal scope; return everything so the dashboard works
      // uniformly for every role.
      List<AcademicClass> all = classes.findAll();
      return all.stream().map(entity -> mapper.toDto(entity, activeCounts(all))).toList();
    }
    if (allowed.isEmpty()) {
      return List.of();
    }
    List<AcademicClass> found = classes.findAllById(allowed);
    return found.stream().map(entity -> mapper.toDto(entity, activeCounts(found))).toList();
  }

  public List<EnrollmentDto> myEnrollments(AppUserDetails principal) {
    return enrollments.findAllByStudentIdOrderByIdAsc(principal.getId()).stream()
        .map(mapper::toDto)
        .toList();
  }

  public List<TeachingAssignmentDto> myAssignments(AppUserDetails principal) {
    if (principal.getRole() != Role.TEACHER) {
      return List.of();
    }
    return assignments.findAllByTeacherIdOrderByIdAsc(principal.getId()).stream()
        .map(mapper::toDto)
        .toList();
  }

  /** Class roster. Visible to ADMIN and to teachers assigned to the class. */
  public List<EnrollmentDto> classRoster(AppUserDetails principal, Long classId) {
    requireClassAccess(principal, classId);
    return enrollments.findAllByAcademicClassIdOrderByIdAsc(classId).stream()
        .map(mapper::toDto)
        .toList();
  }

  /** Teaching assignments in a class. Same visibility as the roster. */
  public List<TeachingAssignmentDto> classAssignments(AppUserDetails principal, Long classId) {
    requireClassAccess(principal, classId);
    return assignments.findAllByAcademicClassIdOrderByIdAsc(classId).stream()
        .map(mapper::toDto)
        .toList();
  }

  /* -------------------------------- helpers ------------------------------- */

  private Map<Long, Long> activeCounts(Page<AcademicClass> page) {
    return activeCounts(page.getContent());
  }

  private Map<Long, Long> activeCounts(List<AcademicClass> entities) {
    return activeCountsById(entities.stream().map(AcademicClass::getId).toList());
  }

  /** Bulk active-enrollment counts, so a page costs one COUNT, not N. */
  private Map<Long, Long> activeCountsById(List<Long> classIds) {
    if (classIds.isEmpty()) {
      return Map.of();
    }
    Map<Long, Long> counts = new HashMap<>();
    for (Object[] row : enrollments.countActiveByClassIds(classIds, EnrollmentStatus.ENROLLED)) {
      counts.put((Long) row[0], (Long) row[1]);
    }
    return counts;
  }

  /* Specifications, built inline to avoid an extra dependency. */

  private Specification<AcademicClass> classBy(
      Set<Long> allowedIds, Long programId, Long semesterId) {
    if (allowedIds == null && programId == null && semesterId == null) {
      return null;
    }
    return (root, query, cb) -> {
      List<Predicate> predicates = new ArrayList<>();
      if (allowedIds != null) {
        predicates.add(root.get("id").in(allowedIds));
      }
      if (programId != null) {
        predicates.add(cb.equal(root.get("program").get("id"), programId));
      }
      if (semesterId != null) {
        predicates.add(cb.equal(root.get("semester").get("id"), semesterId));
      }
      return cb.and(predicates.toArray(new Predicate[0]));
    };
  }

  private Specification<Subject> subjectBy(Set<Long> allowedIds, Long departmentId) {
    if (allowedIds == null && departmentId == null) {
      return null;
    }
    return (root, query, cb) -> {
      List<Predicate> predicates = new ArrayList<>();
      if (allowedIds != null) {
        predicates.add(root.get("id").in(allowedIds));
      }
      if (departmentId != null) {
        predicates.add(cb.equal(root.get("department").get("id"), departmentId));
      }
      return cb.and(predicates.toArray(new Predicate[0]));
    };
  }

  private Specification<AcademicProgram> programByDepartment(Long departmentId) {
    if (departmentId == null) {
      return null;
    }
    return (root, query, cb) -> cb.equal(root.get("department").get("id"), departmentId);
  }
}