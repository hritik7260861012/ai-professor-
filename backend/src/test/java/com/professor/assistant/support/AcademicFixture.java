package com.professor.assistant.support;

import com.professor.assistant.domain.AcademicClass;
import com.professor.assistant.domain.AcademicProgram;
import com.professor.assistant.domain.AcademicYear;
import com.professor.assistant.domain.ClassSubject;
import com.professor.assistant.domain.Department;
import com.professor.assistant.domain.Enrollment;
import com.professor.assistant.domain.ProgramLevel;
import com.professor.assistant.domain.Role;
import com.professor.assistant.domain.Semester;
import com.professor.assistant.domain.Subject;
import com.professor.assistant.domain.TeachingAssignment;
import com.professor.assistant.domain.User;
import com.professor.assistant.dto.AdminCreateUserRequest;
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
import com.professor.assistant.service.AuthService;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Builds the full academic structure for tests.
 *
 * <p>Every test class shares one Spring context and therefore one H2
 * database, so all generated codes and emails are suffixed with a random
 * token to keep fixtures isolated from one another.
 */
public class AcademicFixture {

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
  private final AuthService authService;
  private final PasswordEncoder passwordEncoder;

  private final String tag = UUID.randomUUID().toString().substring(0, 8);

  private int sequence = 0;

  public AcademicFixture(
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
      AuthService authService,
      PasswordEncoder passwordEncoder) {
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
    this.authService = authService;
    this.passwordEncoder = passwordEncoder;
  }

  public static final String PASSWORD = "super-secret";

  private String nextCode(String prefix) {
    return (prefix + sequence++ + tag).toUpperCase().replace("-", "");
  }

  /* ------------------------------- catalogue ------------------------------- */

  public Department department() {
    String code = nextCode("DEPT");
    return departments.save(new Department(code, "Department " + code, "Fixture department"));
  }

  public AcademicProgram program(Department department) {
    String code = nextCode("BSC");
    return programs.save(
        new AcademicProgram(department, code, "Program " + code, ProgramLevel.UNDERGRADUATE, 3, null));
  }

  public AcademicYear academicYear() {
    return academicYear(false);
  }

  public AcademicYear academicYear(boolean current) {
    String name = nextCode("AY");
    return academicYears.save(
        new AcademicYear(
            name, LocalDate.of(2026, 8, 1), LocalDate.of(2027, 7, 31), current));
  }

  public Semester semester(AcademicYear year) {
    return semesters.save(new Semester(year, "Semester 1", 1, null, null));
  }

  public Subject subject(Department department, AcademicProgram program) {
    String code = nextCode("SUBJ");
    return subjects.save(new Subject(department, program, code, "Subject " + code, 4, null));
  }

  public AcademicClass academicClass(AcademicProgram program, Semester semester) {
    String code = nextCode("CLS");
    return classes.save(new AcademicClass(program, semester, code, "Class " + code, 3));
  }

  /** Department → program → year → semester → class, in one call. */
  public AcademicClass standaloneClass() {
    Department department = department();
    return academicClass(program(department), semester(academicYear()));
  }

  public ClassSubject attach(AcademicClass academicClass, Subject subject) {
    return classSubjects.save(new ClassSubject(academicClass, subject));
  }

  /* --------------------------------- people -------------------------------- */

  public User createUser(Role role) {
    // Emails are stored lowercased by AuthService, so look them up the same way.
    String email = ("user-" + nextCode("X") + "@example.com").toLowerCase();
    authService.createUser(
        new AdminCreateUserRequest("Test " + role, email, PASSWORD, role.name()));
    return users.findByEmail(email).orElseThrow();
  }

  public AppUserDetails principal(Role role) {
    return new AppUserDetails(createUser(role));
  }

  public AppUserDetails principalFor(User user) {
    return new AppUserDetails(users.findById(user.getId()).orElseThrow());
  }

  public Enrollment enroll(User student, AcademicClass academicClass) {
    return enrollments.save(new Enrollment(student, academicClass));
  }

  public TeachingAssignment assign(User teacher, AcademicClass academicClass, Subject subject) {
    return assignments.save(
        new TeachingAssignment(
            teacher, academicClass, subject, academicClass.getSemester(), null));
  }

  /** A teacher assigned to {@code subject} in {@code academicClass}. */
  public TeachingAssignment assign(
      AcademicClass academicClass, Subject subject, AppUserDetails teacherPrincipal) {
    User teacher = users.findById(teacherPrincipal.getId()).orElseThrow();
    return assign(teacher, academicClass, subject);
  }

  public String hash(String raw) {
    return passwordEncoder.encode(raw);
  }
}