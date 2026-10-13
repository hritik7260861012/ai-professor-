package com.professor.assistant.controller;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.professor.assistant.domain.AcademicClass;
import com.professor.assistant.domain.AcademicProgram;
import com.professor.assistant.domain.AcademicYear;
import com.professor.assistant.domain.ClassSubject;
import com.professor.assistant.domain.Department;
import com.professor.assistant.domain.Enrollment;
import com.professor.assistant.domain.Role;
import com.professor.assistant.domain.Semester;
import com.professor.assistant.domain.Subject;
import com.professor.assistant.domain.TeachingAssignment;
import com.professor.assistant.domain.User;
import com.professor.assistant.dto.AcademicYearRequest;
import com.professor.assistant.dto.ClassRequest;
import com.professor.assistant.dto.ClassSubjectRequest;
import com.professor.assistant.dto.DepartmentRequest;
import com.professor.assistant.dto.EnrollmentRequest;
import com.professor.assistant.dto.ProgramRequest;
import com.professor.assistant.dto.SemesterRequest;
import com.professor.assistant.dto.SubjectRequest;
import com.professor.assistant.dto.TeachingAssignmentRequest;
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
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/** Phase 3 — ADMIN management of the academic structure. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AcademicAdministrationTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;
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
  private AppUserDetails admin;

  @BeforeEach
  void setUp() {
    fixture =
        new AcademicFixture(
            departments, programs, academicYears, semesters, subjects, classes,
            classSubjects, enrollments, assignments, users, authService, passwordEncoder);
    admin = fixture.principal(Role.ADMIN);
  }

  private static String uniqueCode(String prefix) {
    return prefix + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
  }

  private String json(Object value) throws Exception {
    return objectMapper.writeValueAsString(value);
  }

  /* ------------------------------- happy paths ------------------------------ */

  @Test
  @DisplayName("ADMIN can create the whole academic structure end to end")
  void adminBuildsFullStructure() throws Exception {
    String deptCode = uniqueCode("CS");

    String departmentJson =
        mockMvc
            .perform(
                post("/api/v1/admin/academic/departments")
                    .with(user(admin))
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(json(new DepartmentRequest(deptCode, "Computer Science", "CS dept"))))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.code").value(deptCode))
            .andReturn()
            .getResponse()
            .getContentAsString();
    long departmentId = objectMapper.readTree(departmentJson).get("id").asLong();

    String programJson =
        mockMvc
            .perform(
                post("/api/v1/admin/academic/programs")
                    .with(user(admin))
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        json(
                            new ProgramRequest(
                                departmentId, uniqueCode("BS"), "BSc Computer Science",
                                "UNDERGRADUATE", 3, null))))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.departmentCode").value(deptCode))
            .andReturn()
            .getResponse()
            .getContentAsString();
    long programId = objectMapper.readTree(programJson).get("id").asLong();

    String yearJson =
        mockMvc
            .perform(
                post("/api/v1/admin/academic/academic-years")
                    .with(user(admin))
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        json(
                            new AcademicYearRequest(
                                uniqueCode("AY"), LocalDate.of(2026, 8, 1),
                                LocalDate.of(2027, 7, 31), true))))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.current").value(true))
            .andReturn()
            .getResponse()
            .getContentAsString();
    long yearId = objectMapper.readTree(yearJson).get("id").asLong();

    long semesterId =
        objectMapper
            .readTree(
                mockMvc
                    .perform(
                        post("/api/v1/admin/academic/semesters")
                            .with(user(admin))
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(
                                json(
                                    new SemesterRequest(
                                        yearId, "Semester 1", 1, null, null))))
                    .andExpect(status().isCreated())
                    .andReturn()
                    .getResponse()
                    .getContentAsString())
            .get("id")
            .asLong();

    String subjectCode = uniqueCode("CS101");
    long subjectId =
        objectMapper
            .readTree(
                mockMvc
                    .perform(
                        post("/api/v1/admin/academic/subjects")
                            .with(user(admin))
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(
                                json(
                                    new SubjectRequest(
                                        departmentId, programId, subjectCode,
                                        "Intro to CS", 4, null))))
                    .andExpect(status().isCreated())
                    .andReturn()
                    .getResponse()
                    .getContentAsString())
            .get("id")
            .asLong();

    long classId =
        objectMapper
            .readTree(
                mockMvc
                    .perform(
                        post("/api/v1/admin/academic/classes")
                            .with(user(admin))
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(
                                json(
                                    new ClassRequest(
                                        programId, semesterId, uniqueCode("SEC"),
                                        "Section A", 30))))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.academicYearName").exists())
                    .andExpect(jsonPath("$.enrolledCount").value(0))
                    .andReturn()
                    .getResponse()
                    .getContentAsString())
            .get("id")
            .asLong();

    mockMvc
        .perform(
            post("/api/v1/admin/academic/classes/{classId}/subjects", classId)
                .with(user(admin))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(new ClassSubjectRequest(subjectId))))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.subjectCode").value(subjectCode));

    mockMvc
        .perform(get("/api/v1/classes/{id}/subjects", classId).with(user(admin)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1));
  }

  @Test
  @DisplayName("ADMIN can enroll a student and a teacher sees the roster")
  void adminEnrollsStudent() throws Exception {
    Department department = fixture.department();
    AcademicClass academicClass = fixture.standaloneClass();
    Subject subject = fixture.subject(department, null);
    fixture.attach(academicClass, subject);
    User student = fixture.createUser(Role.STUDENT);

    Enrollment enrollment =
        enrollments.findByStudentIdAndAcademicClassId(
            student.getId(), academicClass.getId())
            .orElse(null);
    if (enrollment == null) {
      mockMvc
          .perform(
              post("/api/v1/admin/academic/enrollments")
                  .with(user(admin))
                  .with(csrf())
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(
                      json(new EnrollmentRequest(student.getId(), academicClass.getId()))))
          .andExpect(status().isCreated())
          .andExpect(jsonPath("$.studentEmail").value(student.getEmail()))
          .andExpect(jsonPath("$.status").value("ENROLLED"));

      // Duplicate enrollment is rejected.
      mockMvc
          .perform(
              post("/api/v1/admin/academic/enrollments")
                  .with(user(admin))
                  .with(csrf())
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(
                      json(new EnrollmentRequest(student.getId(), academicClass.getId()))))
          .andExpect(status().isConflict());
    }

    mockMvc
        .perform(
            get("/api/v1/classes/{id}/students", academicClass.getId()).with(user(admin)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1));
  }

  @Test
  @DisplayName("A class at capacity cannot take another student")
  void enrollmentRespectsCapacity() throws Exception {
    Department department = fixture.department();
    AcademicProgram program = fixture.program(department);
    Semester semester = fixture.semester(fixture.academicYear());
    AcademicClass academicClass =
        classes.save(new AcademicClass(program, semester, uniqueCode("SMALL"), "Small", 1));
    User first = fixture.createUser(Role.STUDENT);
    fixture.enroll(first, academicClass);

    User second = fixture.createUser(Role.STUDENT);
    mockMvc
        .perform(
            post("/api/v1/admin/academic/enrollments")
                .with(user(admin))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(new EnrollmentRequest(second.getId(), academicClass.getId()))))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("capacity")));
  }

  @Test
  @DisplayName("ADMIN can assign a teacher only to a subject the class offers")
  void adminAssignsTeacher() throws Exception {
    Department department = fixture.department();
    AcademicClass academicClass = fixture.standaloneClass();
    Subject offered = fixture.subject(department, null);
    Subject notOffered = fixture.subject(department, null);
    fixture.attach(academicClass, offered);

    AppUserDetails teacher = fixture.principal(Role.TEACHER);

    mockMvc
        .perform(
            post("/api/v1/admin/academic/teaching-assignments")
                .with(user(admin))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    json(
                        new TeachingAssignmentRequest(
                            teacher.getId(), academicClass.getId(), notOffered.getId()))))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("not offered")));

    mockMvc
        .perform(
            post("/api/v1/admin/academic/teaching-assignments")
                .with(user(admin))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    json(
                        new TeachingAssignmentRequest(
                            teacher.getId(), academicClass.getId(), offered.getId()))))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.subjectCode").value(offered.getCode()));

    // Duplicate assignment is rejected.
    mockMvc
        .perform(
            post("/api/v1/admin/academic/teaching-assignments")
                .with(user(admin))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    json(
                        new TeachingAssignmentRequest(
                            teacher.getId(), academicClass.getId(), offered.getId()))))
        .andExpect(status().isConflict());
  }

  @Test
  @DisplayName("A non-TEACHER account cannot be assigned as a teacher")
  void onlyTeachersCanBeAssigned() throws Exception {
    Department department = fixture.department();
    AcademicClass academicClass = fixture.standaloneClass();
    Subject subject = fixture.subject(department, null);
    fixture.attach(academicClass, subject);
    AppUserDetails student = fixture.principal(Role.STUDENT);

    mockMvc
        .perform(
            post("/api/v1/admin/academic/teaching-assignments")
                .with(user(admin))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    json(
                        new TeachingAssignmentRequest(
                            student.getId(), academicClass.getId(), subject.getId()))))
        .andExpect(status().isNotFound());
  }

  /* -------------------------------- validation ------------------------------ */

  @Test
  @DisplayName("Invalid payloads produce structured 400s")
  void validationIsEnforced() throws Exception {
    Map<String, Object> invalidDepartment = new HashMap<>();
    invalidDepartment.put("code", "");
    invalidDepartment.put("name", "");

    mockMvc
        .perform(
            post("/api/v1/admin/academic/departments")
                .with(user(admin))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(invalidDepartment)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value("Validation failed"))
        .andExpect(jsonPath("$.details.fields").isArray());
  }

  @Test
  @DisplayName("Duplicate codes are rejected with 409")
  void duplicateCodesRejected() throws Exception {
    Department department = fixture.department();
    mockMvc
        .perform(
            post("/api/v1/admin/academic/departments")
                .with(user(admin))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    json(
                        new DepartmentRequest(
                            department.getCode(), "Duplicate", null))))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.status").value(409));
  }

  @Test
  @DisplayName("Codes are normalized to uppercase")
  void codesAreNormalized() throws Exception {
    String code = uniqueCode("low").toLowerCase();
    mockMvc
        .perform(
            post("/api/v1/admin/academic/departments")
                .with(user(admin))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(new DepartmentRequest(code, "Mixed case", null))))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.code").value(code.toUpperCase()));
  }

  @Test
  @DisplayName("Referencing an unknown parent returns 404")
  void unknownParentsReturn404() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/admin/academic/programs")
                .with(user(admin))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    json(
                        new ProgramRequest(
                            987654321L, uniqueCode("ORPH"), "Orphan", "UNDERGRADUATE", 3, null))))
        .andExpect(status().isNotFound());
  }

  @Test
  @DisplayName("A department with programs cannot be deleted")
  void deleteIsGuarded() throws Exception {
    Department department = fixture.department();
    fixture.program(department);

    mockMvc
        .perform(delete("/api/v1/admin/academic/departments/{id}", department.getId())
            .with(user(admin)).with(csrf()))
        .andExpect(status().isConflict());
  }

  @Test
  @DisplayName("An empty class can be deleted")
  void deleteClassSucceeds() throws Exception {
    Department department = fixture.department();
    AcademicClass academicClass = fixture.standaloneClass();
    Subject subject = fixture.subject(department, null);
    fixture.attach(academicClass, subject);
    Subject another = fixture.subject(department, null);
    fixture.attach(academicClass, another);

    mockMvc
        .perform(delete("/api/v1/admin/academic/classes/{id}", academicClass.getId())
            .with(user(admin)).with(csrf()))
        .andExpect(status().isNoContent());
  }

  @Test
  @DisplayName("Updates are applied")
  void updateDepartment() throws Exception {
    Department department = fixture.department();
    mockMvc
        .perform(
            put("/api/v1/admin/academic/departments/{id}", department.getId())
                .with(user(admin))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(new DepartmentRequest(department.getCode(), "Renamed", "Updated"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("Renamed"));
  }

  @Test
  @DisplayName("Exactly one academic year stays current")
  void onlyOneAcademicYearIsCurrent() throws Exception {
    // Created through the API, because the "only one current" invariant is
    // enforced by AcademicStructureService, not by the database.
    long firstId = createYearThroughApi(true);
    long secondId = createYearThroughApi(true);

    assert academicYears.findById(secondId).orElseThrow().isCurrent()
        : "the newly created year must be current";
    assert !academicYears.findById(firstId).orElseThrow().isCurrent()
        : "the previously current year must be cleared";

    // Promoting a third year must clear the second, never leave two current.
    long thirdId = createYearThroughApi(true);
    assert !academicYears.findById(secondId).orElseThrow().isCurrent()
        : "only one academic year may be current at a time";
    assert academicYears.findById(thirdId).orElseThrow().isCurrent();
  }

  private long createYearThroughApi(boolean current) throws Exception {
    String body =
        mockMvc
            .perform(
                post("/api/v1/admin/academic/academic-years")
                    .with(user(admin))
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        json(
                            new AcademicYearRequest(
                                uniqueCode("AY"), LocalDate.of(2026, 8, 1),
                                LocalDate.of(2027, 7, 31), current))))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return objectMapper.readTree(body).get("id").asLong();
  }

  /* ------------------------------- authorization ---------------------------- */

  @Test
  @DisplayName("Only ADMIN may manage the academic structure")
  void nonAdminsCannotWrite() throws Exception {
    for (Role role : new Role[] {Role.STUDENT, Role.TEACHER}) {
      AppUserDetails principal = fixture.principal(role);
      mockMvc
          .perform(
              post("/api/v1/admin/academic/departments")
                  .with(user(principal))
                  .with(csrf())
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(json(new DepartmentRequest(uniqueCode("X"), "Nope", null))))
          .andExpect(status().isForbidden());
    }
  }

  @Test
  @DisplayName("Anonymous callers cannot manage the academic structure")
  void anonymousCannotWrite() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/admin/academic/departments")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(new DepartmentRequest(uniqueCode("X"), "Nope", null))))
        .andExpect(status().isUnauthorized());
  }

  /* ------------------------------- reference ------------------------------- */

  @Test
  @DisplayName("Semesters can be listed per academic year")
  void listSemestersByYear() throws Exception {
    AcademicYear year = fixture.academicYear();
    semesters.save(new Semester(year, "Semester 1", 1, null, null));
    semesters.save(new Semester(year, "Semester 2", 2, null, null));

    mockMvc
        .perform(
            get("/api/v1/semesters")
                .param("academicYearId", String.valueOf(year.getId()))
                .with(user(admin)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(2))
        .andExpect(jsonPath("$[0].academicYearName").value(year.getName()));
  }

  @Test
  @DisplayName("Class and subject links are exposed with denormalised labels")
  void classSubjectCarriesLabels() throws Exception {
    Department department = fixture.department();
    AcademicClass academicClass = fixture.standaloneClass();
    Subject subject = fixture.subject(department, null);
    ClassSubject link = fixture.attach(academicClass, subject);

    mockMvc
        .perform(get("/api/v1/classes/{classId}/subjects", academicClass.getId()).with(user(admin)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].id").value(link.getId()))
        .andExpect(jsonPath("$[0].classId").value(academicClass.getId()))
        .andExpect(jsonPath("$[0].semesterId").value(academicClass.getSemester().getId()));
  }

  @Test
  @DisplayName("Teaching assignments record the semester derived from the class")
  void assignmentInheritsSemester() throws Exception {
    Department department = fixture.department();
    AcademicClass academicClass = fixture.standaloneClass();
    Subject subject = fixture.subject(department, null);
    fixture.attach(academicClass, subject);
    User teacher = fixture.createUser(Role.TEACHER);

    TeachingAssignment assignment = fixture.assign(teacher, academicClass, subject);

    assert assignment.getSemester().getId().equals(academicClass.getSemester().getId())
        : "assignment semester must match the class semester";
    assert assignments.findById(assignment.getId()).isPresent();
  }
}