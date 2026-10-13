package com.professor.assistant.controller;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.professor.assistant.domain.AcademicClass;
import com.professor.assistant.domain.Subject;
import com.professor.assistant.domain.User;
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
import com.professor.assistant.support.AcademicFixture;
import com.professor.assistant.domain.Department;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Phase 3 authorization matrix.
 *
 * <p>Proves that a student cannot reach another student's class and a
 * teacher cannot reach classes or subjects they were not assigned — the two
 * cross-tenant leaks this phase exists to prevent.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AcademicScopeTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private DepartmentRepository departments;
  @Autowired private AcademicProgramRepository programs;
  @Autowired private AcademicYearRepository academicYears;
  @Autowired private SemesterRepository semesters;
  @Autowired private SubjectRepository subjects;
  @Autowired private AcademicClassRepository classes;
  @Autowired private ClassSubjectRepository classSubjects;
  @Autowired private EnrollmentRepository enrollments;
  @Autowired private TeachingAssignmentRepository assignments;
  @Autowired private UserRepository users;
  @Autowired private AuthService authService;
  @Autowired private PasswordEncoder passwordEncoder;

  private AcademicFixture fixture;

  @BeforeEach
  void setUp() {
    fixture =
        new AcademicFixture(
            departments, programs, academicYears, semesters, subjects, classes,
            classSubjects, enrollments, assignments, users, authService, passwordEncoder);
  }

  /* --------------------------------- STUDENT -------------------------------- */

  @Test
  @DisplayName("A student sees only the classes they are enrolled in")
  void studentSeesOnlyEnrolledClasses() throws Exception {
    Department department = fixture.department();
    AcademicClass enrolled = fixture.standaloneClass();
    AcademicClass other = fixture.academicClass(fixture.program(department), fixture.semester(fixture.academicYear()));

    AppUserDetails student = fixture.principal(com.professor.assistant.domain.Role.STUDENT);
    fixture.enroll(users.findById(student.getId()).orElseThrow(), enrolled);

    mockMvc
        .perform(get("/api/v1/classes").with(user(student)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(1))
        .andExpect(jsonPath("$.content[0].id").value(enrolled.getId()));

    // The class they are not enrolled in must be invisible.
    mockMvc
        .perform(get("/api/v1/classes/{id}", other.getId()).with(user(student)))
        .andExpect(status().isNotFound());
  }

  @Test
  @DisplayName("A student cannot read another student's class by direct id")
  void studentCannotReadUnenrolledClass() throws Exception {
    AcademicClass target = fixture.standaloneClass();
    AppUserDetails student = fixture.principal(com.professor.assistant.domain.Role.STUDENT);

    mockMvc
        .perform(get("/api/v1/classes/{id}", target.getId()).with(user(student)))
        .andExpect(status().isNotFound());

    mockMvc
        .perform(get("/api/v1/classes/{id}/subjects", target.getId()).with(user(student)))
        .andExpect(status().isNotFound());
  }

  @Test
  @DisplayName("A student sees the subjects of their enrolled classes and nothing else")
  void studentSubjectVisibilityIsLimitedToEnrolledClasses() throws Exception {
    Department department = fixture.department();
    AcademicClass enrolled = fixture.standaloneClass();
    Subject enrolledSubject = fixture.subject(department, null);
    Subject otherSubject = fixture.subject(department, null);
    fixture.attach(enrolled, enrolledSubject);

    AppUserDetails student = fixture.principal(com.professor.assistant.domain.Role.STUDENT);
    fixture.enroll(users.findById(student.getId()).orElseThrow(), enrolled);

    mockMvc
        .perform(get("/api/v1/subjects").with(user(student)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(1))
        .andExpect(jsonPath("$.content[0].code").value(enrolledSubject.getCode()));

    mockMvc
        .perform(get("/api/v1/subjects/{id}", enrolledSubject.getId()).with(user(student)))
        .andExpect(status().isOk());

    // A subject from a class they are not enrolled in is hidden.
    mockMvc
        .perform(get("/api/v1/subjects/{id}", otherSubject.getId()).with(user(student)))
        .andExpect(status().isNotFound());
  }

  @Test
  @DisplayName("A student cannot read another student's roster")
  void studentCannotReadRoster() throws Exception {
    AcademicClass target = fixture.standaloneClass();
    AppUserDetails student = fixture.principal(com.professor.assistant.domain.Role.STUDENT);

    mockMvc
        .perform(get("/api/v1/classes/{id}/students", target.getId()).with(user(student)))
        .andExpect(status().isNotFound());
  }

  /* --------------------------------- TEACHER -------------------------------- */

  @Test
  @DisplayName("A teacher sees only assigned classes")
  void teacherSeesOnlyAssignedClasses() throws Exception {
    Department department = fixture.department();
    AcademicClass assigned = fixture.standaloneClass();
    AcademicClass notAssigned = fixture.academicClass(fixture.program(department), fixture.semester(fixture.academicYear()));

    AppUserDetails teacher = fixture.principal(com.professor.assistant.domain.Role.TEACHER);
    Subject subject = fixture.subject(department, null);
    fixture.attach(assigned, subject);
    fixture.assign(assigned, subject, teacher);

    mockMvc
        .perform(get("/api/v1/classes").with(user(teacher)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(1))
        .andExpect(jsonPath("$.content[0].id").value(assigned.getId()));

    mockMvc
        .perform(get("/api/v1/classes/{id}", notAssigned.getId()).with(user(teacher)))
        .andExpect(status().isNotFound());
  }

  @Test
  @DisplayName("A teacher sees only their own subjects inside an assigned class")
  void teacherSeesOnlyTheirSubjectsWithinAnAssignedClass() throws Exception {
    Department department = fixture.department();
    AcademicClass academicClass = fixture.standaloneClass();
    Subject mine = fixture.subject(department, null);
    Subject someoneElses = fixture.subject(department, null);
    fixture.attach(academicClass, mine);
    fixture.attach(academicClass, someoneElses);

    AppUserDetails teacher = fixture.principal(com.professor.assistant.domain.Role.TEACHER);
    fixture.assign(academicClass, mine, teacher);

    mockMvc
        .perform(get("/api/v1/classes/{id}/subjects", academicClass.getId()).with(user(teacher)))
        .andExpect(status().isOk())
        // One subject only: the rest of the syllabus must stay hidden.
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].subjectCode").value(mine.getCode()));

    mockMvc
        .perform(get("/api/v1/subjects/{id}", someoneElses.getId()).with(user(teacher)))
        .andExpect(status().isNotFound());

    mockMvc
        .perform(get("/api/v1/subjects/{id}", mine.getId()).with(user(teacher)))
        .andExpect(status().isOk());
  }

  @Test
  @DisplayName("A teacher cannot read the roster of a class they are not assigned to")
  void teacherCannotReadUnassignedRoster() throws Exception {
    AcademicClass target = fixture.standaloneClass();
    AppUserDetails teacher = fixture.principal(com.professor.assistant.domain.Role.TEACHER);

    mockMvc
        .perform(get("/api/v1/classes/{id}/students", target.getId()).with(user(teacher)))
        .andExpect(status().isNotFound());
  }

  @Test
  @DisplayName("A teacher can read the roster of a class they are assigned to")
  void teacherCanReadAssignedRoster() throws Exception {
    Department department = fixture.department();
    AcademicClass academicClass = fixture.standaloneClass();
    Subject subject = fixture.subject(department, null);
    fixture.attach(academicClass, subject);

    AppUserDetails teacher = fixture.principal(com.professor.assistant.domain.Role.TEACHER);
    fixture.assign(academicClass, subject, teacher);

    User student = fixture.createUser(com.professor.assistant.domain.Role.STUDENT);
    fixture.enroll(student, academicClass);

    mockMvc
        .perform(get("/api/v1/classes/{id}/students", academicClass.getId()).with(user(teacher)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].studentEmail").value(student.getEmail()));
  }

  @Test
  @DisplayName("A teacher sees only their own teaching assignments")
  void teacherSeesOnlyOwnAssignments() throws Exception {
    Department department = fixture.department();
    AcademicClass mine = fixture.standaloneClass();
    AcademicClass other = fixture.standaloneClass();

    AppUserDetails teacher = fixture.principal(com.professor.assistant.domain.Role.TEACHER);
    AppUserDetails colleague = fixture.principal(com.professor.assistant.domain.Role.TEACHER);

    Subject mySubject = fixture.subject(department, null);
    Subject theirSubject = fixture.subject(department, null);
    fixture.attach(mine, mySubject);
    fixture.attach(other, theirSubject);
    fixture.assign(mine, mySubject, teacher);
    fixture.assign(other, theirSubject, colleague);

    mockMvc
        .perform(get("/api/v1/me/assignments").with(user(teacher)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].subjectCode").value(mySubject.getCode()));
  }

  /* ---------------------------------- ADMIN --------------------------------- */

  @Test
  @DisplayName("An administrator sees every class and subject")
  void adminSeesEverything() throws Exception {
    Department department = fixture.department();
    fixture.standaloneClass();
    AcademicClass second = fixture.academicClass(fixture.program(department), fixture.semester(fixture.academicYear()));
    fixture.subject(department, null);

    AppUserDetails admin = fixture.principal(com.professor.assistant.domain.Role.ADMIN);

    mockMvc
        .perform(get("/api/v1/classes").param("size", "100").with(user(admin)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content[?(@.id == " + second.getId() + ")]").exists());

    mockMvc
        .perform(get("/api/v1/classes/{id}", second.getId()).with(user(admin)))
        .andExpect(status().isOk());
  }

  @Test
  @DisplayName("An administrator sees the full syllabus of a class")
  void adminSeesFullSyllabus() throws Exception {
    Department department = fixture.department();
    AcademicClass academicClass = fixture.standaloneClass();
    Subject first = fixture.subject(department, null);
    Subject second = fixture.subject(department, null);
    fixture.attach(academicClass, first);
    fixture.attach(academicClass, second);

    AppUserDetails admin = fixture.principal(com.professor.assistant.domain.Role.ADMIN);

    mockMvc
        .perform(get("/api/v1/classes/{id}/subjects", academicClass.getId()).with(user(admin)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(2));
  }

  /* ------------------------------- catalogue -------------------------------- */

  @Test
  @DisplayName("All authenticated roles can read the reference catalogue")
  void everyoneCanReadCatalogue() throws Exception {
    fixture.department();

    for (com.professor.assistant.domain.Role role :
        com.professor.assistant.domain.Role.values()) {
      AppUserDetails principal = fixture.principal(role);
      mockMvc
          .perform(get("/api/v1/departments").with(user(principal)))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.content").isArray());
    }
  }

  @Test
  @DisplayName("Anonymous callers get 401 on academic endpoints")
  void anonymousIsRejected() throws Exception {
    mockMvc.perform(get("/api/v1/classes")).andExpect(status().isUnauthorized());
    mockMvc.perform(get("/api/v1/subjects")).andExpect(status().isUnauthorized());
    mockMvc.perform(get("/api/v1/me/classes")).andExpect(status().isUnauthorized());
  }

  @Test
  @DisplayName("Pagination metadata is returned and bounded")
  void paginationIsApplied() throws Exception {
    AppUserDetails admin = fixture.principal(com.professor.assistant.domain.Role.ADMIN);
    for (int i = 0; i < 3; i++) {
      fixture.department();
    }

    mockMvc
        .perform(get("/api/v1/departments").param("size", "2").param("page", "0").with(user(admin)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content.length()").value(2))
        .andExpect(jsonPath("$.size").value(2))
        .andExpect(jsonPath("$.first").value(true))
        .andExpect(jsonPath("$.last").value(Matchers.not(true)));
  }
}