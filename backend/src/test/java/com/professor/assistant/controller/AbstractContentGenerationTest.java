package com.professor.assistant.controller;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.professor.assistant.domain.AcademicClass;
import com.professor.assistant.domain.Department;
import com.professor.assistant.domain.GenerationContentType;
import com.professor.assistant.domain.GenerationJobStatus;
import com.professor.assistant.domain.Role;
import com.professor.assistant.domain.Subject;
import com.professor.assistant.domain.Syllabus;
import com.professor.assistant.domain.User;
import com.professor.assistant.dto.GenerationRequestDto;
import com.professor.assistant.repository.AcademicClassRepository;
import com.professor.assistant.repository.AcademicProgramRepository;
import com.professor.assistant.repository.AcademicYearRepository;
import com.professor.assistant.repository.AiGenerationJobRepository;
import com.professor.assistant.repository.ClassSubjectRepository;
import com.professor.assistant.repository.DepartmentRepository;
import com.professor.assistant.repository.EnrollmentRepository;
import com.professor.assistant.repository.SemesterRepository;
import com.professor.assistant.repository.SyllabusRepository;
import com.professor.assistant.repository.SubjectRepository;
import com.professor.assistant.repository.TeachingAssignmentRepository;
import com.professor.assistant.repository.UserRepository;
import com.professor.assistant.security.AppUserDetails;
import com.professor.assistant.service.AuthService;
import com.professor.assistant.support.AcademicFixture;
import com.professor.assistant.support.DocumentFixtures;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Shared setup for the Phase 5 test classes.
 *
 * <p>Builds a realistic academic context — department, class, subject,
 * teacher assignment, student enrollment and an <em>approved</em> syllabus —
 * so individual tests can concentrate on job lifecycle and authorisation
 * rather than repeating fixture code.
 *
 * <p>Abstract on purpose: this class holds no tests of its own.
 */
@AutoConfigureMockMvc
abstract class AbstractContentGenerationTest {

  /** Long enough to clear the Phase 4 "usable text" threshold. */
  protected static final String SYLLABUS_TEXT =
      String.join(
          "\n",
          "CS101 Introduction to Computer Science",
          "Unit I: Foundations of computing",
          "1.1 History of computing machines",
          "1.2 Number systems and representation",
          "Unit II: Programming fundamentals",
          "2.1 Variables, types and expressions");

  @Autowired protected MockMvc mockMvc;
  @Autowired protected ObjectMapper objectMapper;
  @Autowired protected DepartmentRepository departments;
  @Autowired protected AcademicProgramRepository programs;
  @Autowired protected AcademicYearRepository academicYears;
  @Autowired protected SemesterRepository semesters;
  @Autowired protected SubjectRepository subjects;
  @Autowired protected AcademicClassRepository classes;
  @Autowired protected ClassSubjectRepository classSubjects;
  @Autowired protected EnrollmentRepository enrollments;
  @Autowired protected TeachingAssignmentRepository assignments;
  @Autowired protected SyllabusRepository syllabi;
  @Autowired protected AiGenerationJobRepository jobs;
  @Autowired protected UserRepository users;
  @Autowired protected AuthService authService;
  @Autowired protected PasswordEncoder passwordEncoder;
  @Autowired protected JdbcTemplate jdbc;

  protected AcademicFixture fixture;
  protected AppUserDetails teacher;
  protected AppUserDetails otherTeacher;
  protected AppUserDetails student;
  protected Syllabus approvedSyllabus;
  protected AcademicClass academicClass;
  /** The subject this teacher is assigned to teach in {@link #academicClass}. */
  protected Subject assignedSubject;

  @BeforeEach
  void setUpAcademicContext() {
    fixture =
        new AcademicFixture(
            departments, programs, academicYears, semesters, subjects, classes,
            classSubjects, enrollments, assignments, users, authService, passwordEncoder);

    teacher = fixture.principal(Role.TEACHER);
    otherTeacher = fixture.principal(Role.TEACHER);
    student = fixture.principal(Role.STUDENT);

    Department department = fixture.department();
    academicClass = fixture.standaloneClass();
    assignedSubject = fixture.subject(department, null);
    fixture.attach(academicClass, assignedSubject);
    fixture.assign(academicClass, assignedSubject, teacher);
    User studentUser = users.findById(student.getId()).orElseThrow();
    fixture.enroll(studentUser, academicClass);
  }

  /** Uploads a syllabus for the assigned class + subject and approves it. */
  protected Syllabus uploadAndApproveSyllabus() throws Exception {
    MvcResult upload = uploadSyllabus("approved.txt", SYLLABUS_TEXT);
    long id = syllabusIdOf(upload);
    mockMvc
        .perform(
            post("/api/v1/teacher/syllabi/{id}/approve", id).with(user(teacher)).with(csrf()))
        .andExpect(status().isOk());
    return syllabi.findById(id).orElseThrow();
  }

  protected MvcResult uploadSyllabus(String filename, String text) throws Exception {
    return mockMvc
        .perform(
            multipart("/api/v1/teacher/syllabi")
                .file(new MockMultipartFile("file", filename, "text/plain", DocumentFixtures.txt(text)))
                .param("classId", String.valueOf(academicClass.getId()))
                .param("subjectId", String.valueOf(assignedSubject.getId()))
                .with(user(teacher))
                .with(csrf()))
        .andReturn();
  }

  protected long syllabusIdOf(MvcResult upload) throws Exception {
    return objectMapper
        .readTree(upload.getResponse().getContentAsString())
        .get("syllabus")
        .get("id")
        .asLong();
  }

  /** Creates and approves a syllabus in {@link #setUpAcademicContext}'s class. */
  protected void createApprovedSyllabus() throws Exception {
    approvedSyllabus = uploadAndApproveSyllabus();
  }

  protected GenerationRequestDto request(GenerationContentType type) {
    return request(type, null, null);
  }

  protected GenerationRequestDto request(
      GenerationContentType type, Integer itemCount, String instructions) {
    return new GenerationRequestDto(
        approvedSyllabus.getId(),
        null,
        type,
        null,
        null,
        null,
        itemCount == null ? (type.isCountable() ? 3 : 1) : itemCount,
        null,
        instructions);
  }

  /** Submits a generation request and asserts the HTTP status. */
  protected MvcResult submit(
      AppUserDetails principal, GenerationRequestDto request, int expectedStatus)
      throws Exception {
    MvcResult result =
        mockMvc
            .perform(
                post("/api/v1/teacher/content/generations")
                    .with(user(principal))
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
            .andReturn();
    Assertions.assertThat(result.getResponse().getStatus())
        .as("body=%s", result.getResponse().getContentAsString())
        .isEqualTo(expectedStatus);
    return result;
  }

  protected long jobIdOf(MvcResult result) throws Exception {
    return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
  }

  protected long submitAndReadId(GenerationRequestDto request) throws Exception {
    return jobIdOf(submit(teacher, request, 202));
  }

  /**
   * Waits for a job to reach a terminal state.
   *
   * <p>The controller hands the job straight to the executor, so it is often
   * already RUNNING by the time this is called. Claiming is atomic, so
   * invoking the worker again would be a no-op; polling for the status is
   * what a real client does anyway.
   */
  protected void awaitTerminal(Long jobId) throws Exception {
    for (int attempt = 0; attempt < 200; attempt++) {
      GenerationJobStatus status = jobs.findById(jobId).orElseThrow().getStatus();
      if (status.isTerminal()) {
        return;
      }
      Thread.sleep(50L);
    }
    throw new AssertionError("Generation job " + jobId + " never reached a terminal state");
  }

  /** Backdates a job so it looks abandoned by a previous run. */
  protected void backdate(Long jobId, int minutesAgo) {
    jdbc.update(
        "update ai_generation_job set updated_at = ? where id = ?",
        java.sql.Timestamp.valueOf(
            java.time.LocalDateTime.now().minusMinutes(minutesAgo)),
        jobId);
  }
}