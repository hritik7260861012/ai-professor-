package com.professor.assistant.service;

import com.professor.assistant.domain.AcademicClass;
import com.professor.assistant.domain.AcademicProgram;
import com.professor.assistant.domain.AcademicYear;
import com.professor.assistant.domain.ClassSubject;
import com.professor.assistant.domain.Department;
import com.professor.assistant.domain.Enrollment;
import com.professor.assistant.domain.EnrollmentStatus;
import com.professor.assistant.domain.ProgramLevel;
import com.professor.assistant.domain.Role;
import com.professor.assistant.domain.Semester;
import com.professor.assistant.domain.Subject;
import com.professor.assistant.domain.TeachingAssignment;
import com.professor.assistant.domain.User;
import com.professor.assistant.dto.AcademicYearDto;
import com.professor.assistant.dto.AcademicYearRequest;
import com.professor.assistant.dto.ClassRequest;
import com.professor.assistant.dto.ClassSubjectDto;
import com.professor.assistant.dto.ClassSubjectRequest;
import com.professor.assistant.dto.DepartmentDto;
import com.professor.assistant.dto.DepartmentRequest;
import com.professor.assistant.dto.EnrollmentDto;
import com.professor.assistant.dto.EnrollmentRequest;
import com.professor.assistant.dto.ProgramDto;
import com.professor.assistant.dto.ProgramRequest;
import com.professor.assistant.dto.SemesterDto;
import com.professor.assistant.dto.SemesterRequest;
import com.professor.assistant.dto.SubjectDto;
import com.professor.assistant.dto.SubjectRequest;
import com.professor.assistant.dto.TeachingAssignmentDto;
import com.professor.assistant.dto.TeachingAssignmentRequest;
import com.professor.assistant.exception.ConflictException;
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
import com.professor.assistant.repository.UserRepository;
import com.professor.assistant.security.AppUserDetails;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Write side of the academic structure. Every endpoint backed by this
 * service sits under {@code /api/v1/admin/academic/**}, which
 * {@code SecurityConfig} restricts to the ADMIN role.
 */
@Service
@Transactional
public class AcademicStructureService {

  private final DepartmentRepository departments;
  private final AcademicProgramRepository programs;
  private final AcademicYearRepository academicYears;
  private final SemesterRepository semesters;
  private final SubjectRepository subjects;
  private final AcademicClassRepository classes;
  private final ClassSubjectRepository classSubjects;
  private final EnrollmentRepository enrollments;
  private final TeachingAssignmentRepository assignments;
  private final UserRepository users;
  private final AcademicMapper mapper;

  public AcademicStructureService(
      DepartmentRepository departments,
      AcademicProgramRepository programs,
      AcademicYearRepository academicYears,
      SemesterRepository semesters,
      SubjectRepository subjects,
      AcademicClassRepository classes,
      ClassSubjectRepository classSubjects,
      EnrollmentRepository enrollments,
      TeachingAssignmentRepository assignments,
      UserRepository users,
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
    this.users = users;
    this.mapper = mapper;
  }

  /* ------------------------------- departments ----------------------------- */

  public DepartmentDto createDepartment(DepartmentRequest request) {
    String code = normalizeCode(request.code());
    if (departments.existsByCode(code)) {
      throw new ConflictException("A department with code " + code + " already exists");
    }
    Department saved =
        departments.save(new Department(code, request.name().trim(), request.description()));
    return mapper.toDto(saved);
  }

  public DepartmentDto updateDepartment(Long id, DepartmentRequest request) {
    Department department = requireDepartment(id);
    String code = normalizeCode(request.code());
    departments
        .findByCode(code)
        .filter(other -> !other.getId().equals(id))
        .ifPresent(other -> {
          throw new ConflictException("A department with code " + code + " already exists");
        });
    department.setCode(code);
    department.setName(request.name().trim());
    department.setDescription(request.description());
    return mapper.toDto(departments.save(department));
  }

  public void deleteDepartment(Long id) {
    Department department = requireDepartment(id);
    if (programs.countByDepartmentId(id) > 0) {
      throw new ConflictException("Department still has programs: " + department.getCode());
    }
    if (subjects.countByDepartmentId(id) > 0) {
      throw new ConflictException("Department still has subjects: " + department.getCode());
    }
    departments.delete(department);
  }

  /* -------------------------------- programs ------------------------------- */

  public ProgramDto createProgram(ProgramRequest request) {
    Department department = requireDepartment(request.departmentId());
    String code = normalizeCode(request.code());
    if (programs.existsByCode(code)) {
      throw new ConflictException("A program with code " + code + " already exists");
    }
    return mapper.toDto(
        programs.save(
            new AcademicProgram(
                department,
                code,
                request.name().trim(),
                ProgramLevel.valueOf(request.level()),
                request.durationYears(),
                request.description())));
  }

  public ProgramDto updateProgram(Long id, ProgramRequest request) {
    AcademicProgram program = requireProgram(id);
    String code = normalizeCode(request.code());
    programs
        .findByCode(code)
        .filter(other -> !other.getId().equals(id))
        .ifPresent(other -> {
          throw new ConflictException("A program with code " + code + " already exists");
        });
    program.setDepartment(requireDepartment(request.departmentId()));
    program.setCode(code);
    program.setName(request.name().trim());
    program.setLevel(ProgramLevel.valueOf(request.level()));
    program.setDurationYears(request.durationYears());
    program.setDescription(request.description());
    return mapper.toDto(programs.save(program));
  }

  public void deleteProgram(Long id) {
    AcademicProgram program = requireProgram(id);
    if (classes.countByProgramId(id) > 0) {
      throw new ConflictException("Program still has classes: " + program.getCode());
    }
    programs.delete(program);
  }

  /* ----------------------------- academic years ---------------------------- */

  public AcademicYearDto createAcademicYear(AcademicYearRequest request) {
    validateYearRange(request);
    if (academicYears.existsByName(request.name().trim())) {
      throw new ConflictException("Academic year " + request.name() + " already exists");
    }
    boolean makeCurrent = Boolean.TRUE.equals(request.current());
    AcademicYear saved =
        academicYears.save(
            new AcademicYear(
                request.name().trim(), request.startDate(), request.endDate(), makeCurrent));
    if (makeCurrent) {
      clearOtherCurrentFlags(saved.getId());
    }
    // Mapped here, inside the transaction: the associations are lazy and
    // open-in-view is disabled, so mapping after commit would fail.
    return mapper.toDto(saved);
  }

  public AcademicYearDto updateAcademicYear(Long id, AcademicYearRequest request) {
    validateYearRange(request);
    AcademicYear year = requireAcademicYear(id);
    academicYears
        .findByName(request.name().trim())
        .filter(other -> !other.getId().equals(id))
        .ifPresent(other -> {
          throw new ConflictException("Academic year " + request.name() + " already exists");
        });
    year.setName(request.name().trim());
    year.setStartDate(request.startDate());
    year.setEndDate(request.endDate());
    if (request.current() != null) {
      year.setCurrent(request.current());
      if (request.current()) {
        clearOtherCurrentFlags(id);
      }
    }
    return mapper.toDto(academicYears.save(year));
  }

  public void deleteAcademicYear(Long id) {
    AcademicYear year = requireAcademicYear(id);
    if (semesters.countByAcademicYearId(id) > 0) {
      throw new ConflictException("Academic year still has semesters: " + year.getName());
    }
    academicYears.delete(year);
  }

  /** Maintains the "exactly one current year" invariant. */
  private void clearOtherCurrentFlags(Long keepId) {
    for (AcademicYear other : academicYears.findAll()) {
      if (!other.getId().equals(keepId) && other.isCurrent()) {
        other.setCurrent(false);
        academicYears.save(other);
      }
    }
  }

  private void validateYearRange(AcademicYearRequest request) {
    if (!request.endDate().isAfter(request.startDate())) {
      throw new IllegalArgumentException("End date must be after the start date");
    }
  }

  /* -------------------------------- semesters ------------------------------ */

  public SemesterDto createSemester(SemesterRequest request) {
    AcademicYear year = requireAcademicYear(request.academicYearId());
    semesters
        .findByAcademicYearIdAndSemesterNumber(year.getId(), request.semesterNumber())
        .ifPresent(existing -> {
          throw new ConflictException(
              "Semester " + request.semesterNumber() + " already exists for " + year.getName());
        });
    return mapper.toDto(
        semesters.save(
            new Semester(
                year,
                request.name().trim(),
                request.semesterNumber(),
                request.startDate(),
                request.endDate())));
  }

  public SemesterDto updateSemester(Long id, SemesterRequest request) {
    Semester semester = requireSemester(id);
    semesters
        .findByAcademicYearIdAndSemesterNumber(request.academicYearId(), request.semesterNumber())
        .filter(other -> !other.getId().equals(id))
        .ifPresent(other -> {
          throw new ConflictException(
              "Semester " + request.semesterNumber() + " already exists in that academic year");
        });
    semester.setAcademicYear(requireAcademicYear(request.academicYearId()));
    semester.setName(request.name().trim());
    semester.setSemesterNumber(request.semesterNumber());
    semester.setStartDate(request.startDate());
    semester.setEndDate(request.endDate());
    return mapper.toDto(semesters.save(semester));
  }

  public void deleteSemester(Long id) {
    Semester semester = requireSemester(id);
    if (classes.countBySemesterId(id) > 0) {
      throw new ConflictException("Semester still has classes: " + semester.getName());
    }
    semesters.delete(semester);
  }

  /* -------------------------------- subjects ------------------------------- */

  public SubjectDto createSubject(SubjectRequest request) {
    Department department = requireDepartment(request.departmentId());
    AcademicProgram program = request.programId() == null ? null : requireProgram(request.programId());
    String code = normalizeCode(request.code());
    if (subjects.existsByCode(code)) {
      throw new ConflictException("A subject with code " + code + " already exists");
    }
    return mapper.toDto(
        subjects.save(
            new Subject(
                department, program, code, request.name().trim(), request.credits(),
                request.description())));
  }

  public SubjectDto updateSubject(Long id, SubjectRequest request) {
    Subject subject = requireSubject(id);
    String code = normalizeCode(request.code());
    subjects
        .findByCode(code)
        .filter(other -> !other.getId().equals(id))
        .ifPresent(other -> {
          throw new ConflictException("A subject with code " + code + " already exists");
        });
    subject.setDepartment(requireDepartment(request.departmentId()));
    subject.setProgram(request.programId() == null ? null : requireProgram(request.programId()));
    subject.setCode(code);
    subject.setName(request.name().trim());
    subject.setCredits(request.credits());
    subject.setDescription(request.description());
    return mapper.toDto(subjects.save(subject));
  }

  public void deleteSubject(Long id) {
    Subject subject = requireSubject(id);
    if (!classSubjects.findAllBySubjectId(id).isEmpty()) {
      throw new ConflictException("Subject is still offered in classes: " + subject.getCode());
    }
    subjects.delete(subject);
  }

  /* --------------------------------- classes ------------------------------- */

  public AcademicClass createClass(ClassRequest request) {
    AcademicProgram program = requireProgram(request.programId());
    Semester semester = requireSemester(request.semesterId());
    String code = request.code().trim();
    if (classes.existsBySemesterIdAndCode(semester.getId(), code)) {
      throw new ConflictException("A class with code " + code + " already exists in that semester");
    }
    return classes.save(
        new AcademicClass(program, semester, code, request.name().trim(), request.capacity()));
  }

  public AcademicClass updateClass(Long id, ClassRequest request) {
    AcademicClass entity = requireClass(id);
    Semester semester = requireSemester(request.semesterId());
    String code = request.code().trim();
    classes
        .findBySemesterIdAndCode(semester.getId(), code)
        .filter(other -> !other.getId().equals(id))
        .ifPresent(other -> {
          throw new ConflictException("A class with code " + code + " already exists in that semester");
        });
    entity.setProgram(requireProgram(request.programId()));
    entity.setSemester(semester);
    entity.setCode(code);
    entity.setName(request.name().trim());
    entity.setCapacity(request.capacity());
    return classes.save(entity);
  }

  public void deleteClass(Long id) {
    AcademicClass entity = requireClass(id);
    long enrolled = enrollments.countByAcademicClassId(id);
    if (enrolled > 0) {
      throw new ConflictException("Class still has " + enrolled + " enrolled student(s)");
    }
    // Detach subjects and assignments first so no FK row is orphaned.
    classSubjects.deleteAllByAcademicClassId(id);
    assignments.deleteAll(
        assignments.findAllByAcademicClassIdOrderByIdAsc(id));
    classes.delete(entity);
  }

  /* ----------------------------- class subjects ---------------------------- */

  public ClassSubjectDto attachSubject(Long classId, ClassSubjectRequest request) {
    AcademicClass entity = requireClass(classId);
    Subject subject = requireSubject(request.subjectId());
    if (classSubjects.existsByAcademicClassIdAndSubjectId(classId, request.subjectId())) {
      throw new ConflictException(
          "Subject " + subject.getCode() + " is already attached to this class");
    }
    return mapper.toDto(classSubjects.save(new ClassSubject(entity, subject)));
  }

  public void detachSubject(Long classId, Long classSubjectId) {
    requireClass(classId);
    ClassSubject link =
        classSubjects
            .findById(classSubjectId)
            .filter(found -> found.getAcademicClass().getId().equals(classId))
            .orElseThrow(
                () -> new ResourceNotFoundException(
                    "Subject mapping not found in class " + classId));
    classSubjects.delete(link);
  }

  /* ------------------------------- enrollments ----------------------------- */

  public EnrollmentDto enrollStudent(EnrollmentRequest request) {
    User student = requireUserWithRole(request.studentId(), Role.STUDENT, "Student");
    AcademicClass entity = requireClass(request.classId());

    enrollments
        .findByStudentIdAndAcademicClassId(student.getId(), entity.getId())
        .ifPresent(existing -> {
          if (existing.getStatus() == EnrollmentStatus.ENROLLED) {
            throw new ConflictException(
                student.getEmail() + " is already enrolled in " + entity.getCode());
          }
          // Re-enrolling a dropped student reactivates the row.
          throw new ConflictException(
              student.getEmail() + " already has an enrollment record for " + entity.getCode());
        });

    long active = enrollments.countByAcademicClassIdAndStatus(entity.getId(), EnrollmentStatus.ENROLLED);
    if (active >= entity.getCapacity()) {
      throw new ConflictException("Class " + entity.getCode() + " is at capacity");
    }
    return mapper.toDto(enrollments.save(new Enrollment(student, entity)));
  }

  public void unenrollStudent(Long enrollmentId) {
    Enrollment enrollment =
        enrollments
            .findById(enrollmentId)
            .orElseThrow(() -> ResourceNotFoundException.of("Enrollment", enrollmentId));
    enrollments.delete(enrollment);
  }

  /* --------------------------- teaching assignments ------------------------ */

  public TeachingAssignmentDto assignTeacher(
      TeachingAssignmentRequest request, AppUserDetails actingAdmin) {
    User teacher = requireUserWithRole(request.teacherId(), Role.TEACHER, "Teacher");
    AcademicClass entity = requireClass(request.classId());
    Subject subject = requireSubject(request.subjectId());

    // The teacher can only be assigned to a subject the class actually offers.
    if (!classSubjects.existsByAcademicClassIdAndSubjectId(entity.getId(), subject.getId())) {
      throw new ConflictException(
          "Subject " + subject.getCode() + " is not offered in class " + entity.getCode());
    }
    if (assignments.existsByTeacherIdAndAcademicClassIdAndSubjectIdAndSemesterId(
        teacher.getId(), entity.getId(), subject.getId(), entity.getSemester().getId())) {
      throw new ConflictException(
          teacher.getEmail() + " is already assigned to " + subject.getCode()
              + " in " + entity.getCode());
    }

    User admin = actingAdmin == null ? null : requireUser(actingAdmin.getId());
    return mapper.toDto(
        assignments.save(
            new TeachingAssignment(teacher, entity, subject, entity.getSemester(), admin)));
  }

  public void removeAssignment(Long assignmentId) {
    TeachingAssignment assignment =
        assignments
            .findById(assignmentId)
            .orElseThrow(() -> ResourceNotFoundException.of("Teaching assignment", assignmentId));
    assignments.delete(assignment);
  }

  public List<Enrollment> enrollmentsOf(Long classId) {
    requireClass(classId);
    return enrollments.findAllByAcademicClassIdOrderByIdAsc(classId);
  }

  public List<TeachingAssignment> assignmentsOf(Long classId) {
    requireClass(classId);
    return assignments.findAllByAcademicClassIdOrderByIdAsc(classId);
  }

  /* ------------------------------- lookups -------------------------------- */

  private Department requireDepartment(Long id) {
    return departments
        .findById(id)
        .orElseThrow(() -> ResourceNotFoundException.of("Department", id));
  }

  private AcademicProgram requireProgram(Long id) {
    return programs.findById(id).orElseThrow(() -> ResourceNotFoundException.of("Program", id));
  }

  private AcademicYear requireAcademicYear(Long id) {
    return academicYears
        .findById(id)
        .orElseThrow(() -> ResourceNotFoundException.of("Academic year", id));
  }

  private Semester requireSemester(Long id) {
    return semesters.findById(id).orElseThrow(() -> ResourceNotFoundException.of("Semester", id));
  }

  private Subject requireSubject(Long id) {
    return subjects.findById(id).orElseThrow(() -> ResourceNotFoundException.of("Subject", id));
  }

  private AcademicClass requireClass(Long id) {
    return classes.findById(id).orElseThrow(() -> ResourceNotFoundException.of("Class", id));
  }

  private User requireUser(Long id) {
    return users.findById(id).orElseThrow(() -> ResourceNotFoundException.of("User", id));
  }

  private User requireUserWithRole(Long id, Role role, String label) {
    User user = requireUser(id);
    if (user.getRole() != role) {
      throw new IllegalArgumentException(
          "User " + user.getEmail() + " does not have the " + role + " role required as a "
              + label.toLowerCase());
    }
    if (user.getStatus() != com.professor.assistant.domain.AccountStatus.ACTIVE) {
      throw new IllegalArgumentException(
          "User " + user.getEmail() + " is suspended and cannot be assigned");
    }
    return user;
  }

  /** Codes are compared case-insensitively; store them uppercase. */
  private static String normalizeCode(String code) {
    return code.trim().toUpperCase(Locale.ROOT);
  }
}