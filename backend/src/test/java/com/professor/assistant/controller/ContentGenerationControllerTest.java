package com.professor.assistant.controller;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
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
import com.professor.assistant.repository.GeneratedContentRepository;
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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Phase 5 end to end. Runs entirely on the mock provider: no API key and
 * no live AI call is involved.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ContentGenerationControllerTest {

  private static final String SYLLABUS_TEXT =
      String.join(
          "\n",
          "CS101 Introduction to Computer Science",
          "Unit I: Foundations of computing",
          "1.1 History of computing machines",
          "1.2 Number systems and representation",
          "Unit II: Programming fundamentals",
          "2.1 Variables, types and expressions");

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
  @Autowired private SyllabusRepository syllabi;
  @Autowired private AiGenerationJobRepository jobs;
  @Autowired private GeneratedContentRepository contents;
  @Autowired private UserRepository users;
  @Autowired private AuthService authService;
  @Autowired private PasswordEncoder passwordEncoder;

  private AcademicFixture fixture;
  private AppUserDetails teacher;
  private AppUserDetails otherTeacher;
  private AppUserDetails student;
  private AppUserDetails admin;
  private Syllabus approvedSyllabus;
  private AcademicClass academicClass;
  /** The subject this teacher is assigned to teach in {@link #academicClass}. */
  private Subject assignedSubject;

  @BeforeEach
  void setUp() throws Exception {
    fixture =
        new AcademicFixture(
            departments, programs, academicYears, semesters, subjects, classes,
            classSubjects, enrollments, assignments, users, authService, passwordEncoder);

    teacher = fixture.principal(Role.TEACHER);
    otherTeacher = fixture.principal(Role.TEACHER);
    student = fixture.principal(Role.STUDENT);
    admin = fixture.principal(Role.ADMIN);

    Department department = fixture.department();
    academicClass = fixture.standaloneClass();
    assignedSubject = fixture.subject(department, null);
    fixture.attach(academicClass, assignedSubject);
    fixture.assign(academicClass, assignedSubject, teacher);
    User studentUser = users.findById(student.getId()).orElseThrow();
    fixture.enroll(studentUser, academicClass);

    approvedSyllabus =
        uploadAndApproveSyllabus(teacher, academicClass, assignedSubject, SYLLABUS_TEXT);
  }

  private Syllabus uploadAndApproveSyllabus(
      AppUserDetails principal, AcademicClass academicClass, Subject subject, String text)
      throws Exception {
    MvcResult upload =
        mockMvc
            .perform(
                multipart("/api/v1/teacher/syllabi")
                    .file(
                        new MockMultipartFile(
                            "file", "s.txt", "text/plain", DocumentFixtures.txt(text)))
                    .param("classId", String.valueOf(academicClass.getId()))
                    .param("subjectId", String.valueOf(subject.getId()))
                    .with(user(principal))
                    .with(csrf()))
            .andReturn();
    long id =
        objectMapper
            .readTree(upload.getResponse().getContentAsString())
            .get("syllabus")
            .get("id")
            .asLong();

    mockMvc
        .perform(post("/api/v1/teacher/syllabi/{id}/approve", id).with(user(principal)).with(csrf()))
        .andExpect(status().isOk());
    return syllabi.findById(id).orElseThrow();
  }

  private MvcResult submit(
      AppUserDetails principal, GenerationRequestDto request, int expectedStatus) throws Exception {
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

  private GenerationRequestDto request(GenerationContentType type) {
    return new GenerationRequestDto(
        approvedSyllabus.getId(), null, type, null, null, null, 3, null, null);
  }

  /**
   * Waits for a job to reach a terminal state.
   *
   * <p>The controller hands the job straight to the executor, so it is often
   * already RUNNING by the time this is called. Claiming is atomic, so
   * invoking the worker again here would be a no-op; polling for the status
   * is what a real client does anyway.
   */
  private void drain(Long jobId) throws Exception {
    for (int attempt = 0; attempt < 200; attempt++) {
      GenerationJobStatus status = jobs.findById(jobId).orElseThrow().getStatus();
      if (status.isTerminal()) {
        return;
      }
      Thread.sleep(50L);
    }
    throw new AssertionError("Generation job " + jobId + " never reached a terminal state");
  }

  private long jobIdOf(MvcResult result) throws Exception {
    return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
  }

  /* ------------------------------- happy path ------------------------------- */

  @Test
  @DisplayName("A generation request is accepted, runs and produces content")
  void generationCompletes() throws Exception {
    MvcResult accepted = submit(teacher, request(GenerationContentType.UNIT_NOTES), 202);
    long jobId = jobIdOf(accepted);

    drain(jobId);

    mockMvc
        .perform(get("/api/v1/teacher/content/generations/{jobId}", jobId).with(user(teacher)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("COMPLETED"))
        .andExpect(jsonPath("$.contentId").isNumber())
        .andExpect(jsonPath("$.providerName").value("MOCK"))
        // The API key must never appear anywhere.
        .andExpect(jsonPath("$.apiKey").doesNotExist());
  }

  @Test
  @DisplayName("Every supported content type generates")
  void everyContentTypeGenerates() throws Exception {
    for (GenerationContentType type : GenerationContentType.values()) {
      // Vary the unit so duplicate suppression does not collapse the cases.
      GenerationRequestDto req =
          new GenerationRequestDto(
              approvedSyllabus.getId(), null, type, null, null, null, 2, false, "case " + type);
      MvcResult accepted = submit(teacher, req, 202);
      long jobId = jobIdOf(accepted);
      drain(jobId);

      mockMvc
          .perform(get("/api/v1/teacher/content/generations/{jobId}", jobId).with(user(teacher)))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.status").value("COMPLETED"));
    }
  }

  @Test
  @DisplayName("MCQs produce validated structured output")
  void mcqProducesStructuredJson() throws Exception {
    long jobId = jobIdOf(submit(teacher, request(GenerationContentType.MCQS), 202));
    drain(jobId);

    Long contentId = jobs.findById(jobId).orElseThrow().getContentId();
    Assertions.assertThat(contentId).isNotNull();

    var content = contents.findById(contentId).orElseThrow();
    Assertions.assertThat(content.getStructuredJson()).contains("\"correctAnswer\"");
    Assertions.assertThat(content.getStructuredJson()).contains("\"options\"");
  }

  @Test
  @DisplayName("Teacher study material is teacher-only and can never be released")
  void teacherMaterialIsNeverReleasable() throws Exception {
    long jobId =
        jobIdOf(submit(teacher, request(GenerationContentType.TEACHER_STUDY_MATERIAL), 202));
    drain(jobId);

    Long contentId = jobs.findById(jobId).orElseThrow().getContentId();
    var content = contents.findById(contentId).orElseThrow();
    Assertions.assertThat(content.getAudience().name()).isEqualTo("TEACHER");

    // Even if the teacher asks for release, it must stay teacher-only.
    mockMvc
        .perform(
            post("/api/v1/teacher/content/{id}/approve", contentId)
                .with(user(teacher))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"releaseToStudents\":true}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.audience").value("TEACHER"))
        .andExpect(jsonPath("$.releasedToStudents").value(false));

    // And a student must never see it.
    mockMvc
        .perform(get("/api/v1/student/content/{id}", contentId).with(user(student)))
        .andExpect(status().isNotFound());
  }

  /* ------------------------------ validation ------------------------------- */

  @Test
  @DisplayName("Invalid options are rejected with 400")
  void invalidOptionsRejected() throws Exception {
    GenerationRequestDto tooManyItems =
        new GenerationRequestDto(
            approvedSyllabus.getId(), null, GenerationContentType.MCQS, null, null, null, 500, null, null);
    submit(teacher, tooManyItems, 400);

    GenerationRequestDto longInstructions =
        new GenerationRequestDto(
            approvedSyllabus.getId(), null, GenerationContentType.UNIT_NOTES, null, null, null, 1, null,
            "x".repeat(600));
    submit(teacher, longInstructions, 400);

    GenerationRequestDto missingSyllabus =
        new GenerationRequestDto(null, null, GenerationContentType.UNIT_NOTES, null, null, null, 1, null, null);
    submit(teacher, missingSyllabus, 400);
  }

  @Test
  @DisplayName("A unit from another syllabus is rejected")
  void foreignUnitRejected() throws Exception {
    Syllabus other =
        uploadAndApproveSyllabus(teacher, academicClass, assignedSubject, SYLLABUS_TEXT);
    GenerationRequestDto req =
        new GenerationRequestDto(
            other.getId(), 999999L, GenerationContentType.UNIT_NOTES, null, null, null, 1, null, null);

    // 404, matching the project convention: a unit outside the syllabus's
    // scope is reported as non-existent rather than as a bad request.
    submit(teacher, req, 404);
  }

  /* ----------------------------- grounding rules --------------------------- */

  @Test
  @DisplayName("Generation from an unapproved syllabus is refused")
  void requiresApprovedSyllabus() throws Exception {
    Syllabus draft =
        uploadDraftOnly(teacher);
    GenerationRequestDto req =
        new GenerationRequestDto(
            draft.getId(), null, GenerationContentType.UNIT_NOTES, null, null, null, 1, null, null);

    submit(teacher, req, 409);
  }

  private Syllabus uploadDraftOnly(AppUserDetails principal) throws Exception {
    MvcResult upload =
        mockMvc
            .perform(
                multipart("/api/v1/teacher/syllabi")
                    .file(
                        new MockMultipartFile(
                            "file", "draft.txt", "text/plain", DocumentFixtures.txt(SYLLABUS_TEXT)))
                    .param("classId", String.valueOf(academicClass.getId()))
                    .param("subjectId", String.valueOf(assignedSubject.getId()))
                    .with(user(principal))
                    .with(csrf()))
            .andReturn();
    long id =
        objectMapper
            .readTree(upload.getResponse().getContentAsString())
            .get("syllabus")
            .get("id")
            .asLong();
    return syllabi.findById(id).orElseThrow();
  }

  @Test
  @DisplayName("Content records the syllabus version it was generated from")
  void contentRecordsSyllabusVersion() throws Exception {
    long jobId = jobIdOf(submit(teacher, request(GenerationContentType.UNIT_NOTES), 202));
    drain(jobId);

    Long contentId = jobs.findById(jobId).orElseThrow().getContentId();
    mockMvc
        .perform(get("/api/v1/teacher/content/{id}", contentId).with(user(teacher)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.syllabusVersion").value(approvedSyllabus.getVersion()))
        .andExpect(jsonPath("$.stale").value(false));
  }

  /* --------------------------- stale on re-upload -------------------------- */

  @Test
  @DisplayName("A newer syllabus version makes older content OUTDATED")
  void newerSyllabusVersionMarksContentOutdated() throws Exception {
    long jobId = jobIdOf(submit(teacher, request(GenerationContentType.UNIT_NOTES), 202));
    drain(jobId);
    Long contentId = jobs.findById(jobId).orElseThrow().getContentId();

    // Upload and approve a v2 for the same class + subject.
    uploadAndApproveSyllabus(teacher, academicClass, assignedSubject, SYLLABUS_TEXT);

    mockMvc
        .perform(get("/api/v1/teacher/content/{id}", contentId).with(user(teacher)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.stale").value(true))
        .andExpect(jsonPath("$.reviewStatus").value("OUTDATED"));

    // Stale content can no longer be approved.
    mockMvc
        .perform(post("/api/v1/teacher/content/{id}/approve", contentId).with(user(teacher)).with(csrf()))
        .andExpect(status().isConflict());

    // And it disappears from the student view.
    mockMvc
        .perform(get("/api/v1/student/content/{id}", contentId).with(user(student)))
        .andExpect(status().isNotFound());
  }

  /* ---------------------------- authorization ------------------------------ */

  @Test
  @DisplayName("A teacher cannot generate from another teacher's syllabus")
  void otherTeacherCannotGenerate() throws Exception {
    submit(otherTeacher, request(GenerationContentType.UNIT_NOTES), 404);
  }

  @Test
  @DisplayName("A student cannot start a generation")
  void studentCannotGenerate() throws Exception {
    submit(student, request(GenerationContentType.UNIT_NOTES), 404);
  }

  @Test
  @DisplayName("Anonymous callers cannot start a generation")
  void anonymousCannotGenerate() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/teacher/content/generations")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request(GenerationContentType.UNIT_NOTES))))
        .andExpect(status().isUnauthorized());
  }

  @Test
  @DisplayName("A teacher cannot read another teacher's job or content")
  void otherTeacherCannotReadContent() throws Exception {
    long jobId = jobIdOf(submit(teacher, request(GenerationContentType.UNIT_NOTES), 202));
    drain(jobId);
    Long contentId = jobs.findById(jobId).orElseThrow().getContentId();

    mockMvc
        .perform(get("/api/v1/teacher/content/generations/{jobId}", jobId).with(user(otherTeacher)))
        .andExpect(status().isNotFound());
    mockMvc
        .perform(get("/api/v1/teacher/content/{id}", contentId).with(user(otherTeacher)))
        .andExpect(status().isNotFound());
    mockMvc
        .perform(delete("/api/v1/teacher/content/{id}", contentId).with(user(otherTeacher)).with(csrf()))
        .andExpect(status().isNotFound());
  }

  @Test
  @DisplayName("A revoked teaching assignment revokes content access")
  void revokedAssignmentRevokesAccess() throws Exception {
    long jobId = jobIdOf(submit(teacher, request(GenerationContentType.UNIT_NOTES), 202));
    drain(jobId);
    Long contentId = jobs.findById(jobId).orElseThrow().getContentId();

    assignments.deleteAll(assignments.findAllByTeacherIdOrderByIdAsc(teacher.getId()));

    mockMvc
        .perform(get("/api/v1/teacher/content/{id}", contentId).with(user(teacher)))
        .andExpect(status().isNotFound());
  }

  /* ------------------------------ student read ----------------------------- */

  @Test
  @DisplayName("A student sees approved released content for their class")
  void studentSeesReleasedContent() throws Exception {
    long jobId = jobIdOf(submit(teacher, request(GenerationContentType.QUICK_REVISION), 202));
    drain(jobId);
    Long contentId = jobs.findById(jobId).orElseThrow().getContentId();

    // Before release: invisible.
    mockMvc
        .perform(get("/api/v1/student/content/{id}", contentId).with(user(student)))
        .andExpect(status().isNotFound());

    mockMvc
        .perform(
            post("/api/v1/teacher/content/{id}/approve", contentId)
                .with(user(teacher))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"releaseToStudents\":true}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.releasedToStudents").value(true));

    mockMvc
        .perform(get("/api/v1/student/content/{id}", contentId).with(user(student)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.title").exists())
        .andExpect(jsonPath("$.body").exists());

    mockMvc
        .perform(get("/api/v1/student/content").with(user(student)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1));
  }

  @Test
  @DisplayName("A student in another class cannot read the content")
  void studentCannotReadOtherClassContent() throws Exception {
    long jobId = jobIdOf(submit(teacher, request(GenerationContentType.UNIT_NOTES), 202));
    drain(jobId);
    Long contentId = jobs.findById(jobId).orElseThrow().getContentId();
    approveAndRelease(contentId);

    AppUserDetails outsider = fixture.principal(Role.STUDENT);

    mockMvc
        .perform(get("/api/v1/student/content/{id}", contentId).with(user(outsider)))
        .andExpect(status().isNotFound());
    mockMvc
        .perform(get("/api/v1/student/content").with(user(outsider)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(0));
  }

  @Test
  @DisplayName("The student projection never carries an answer key")
  void answerKeysAreWithheldFromStudents() throws Exception {
    GenerationRequestDto withKey =
        new GenerationRequestDto(
            approvedSyllabus.getId(), null, GenerationContentType.ASSIGNMENTS_QUESTIONS,
            null, null, null, 3, true, "with answer key");
    long jobId = jobIdOf(submit(teacher, withKey, 202));
    drain(jobId);
    Long contentId = jobs.findById(jobId).orElseThrow().getContentId();
    approveAndRelease(contentId);

    Assertions.assertThat(contents.findById(contentId).orElseThrow().hasAnswerKey()).isTrue();

    mockMvc
        .perform(get("/api/v1/student/content/{id}", contentId).with(user(student)))
        .andExpect(status().isOk())
        // The structured payload carrying answers is withheld entirely.
        .andExpect(jsonPath("$.structuredJson").doesNotExist());
  }

  @Test
  @DisplayName("Rejected content is never visible to students")
  void rejectedContentHidden() throws Exception {
    long jobId = jobIdOf(submit(teacher, request(GenerationContentType.UNIT_NOTES), 202));
    drain(jobId);
    Long contentId = jobs.findById(jobId).orElseThrow().getContentId();

    mockMvc
        .perform(
            post("/api/v1/teacher/content/{id}/reject", contentId)
                .with(user(teacher))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"Needs work\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.reviewStatus").value("REJECTED"));

    mockMvc
        .perform(get("/api/v1/student/content/{id}", contentId).with(user(student)))
        .andExpect(status().isNotFound());
  }

  private void approveAndRelease(Long contentId) throws Exception {
    mockMvc
        .perform(
            post("/api/v1/teacher/content/{id}/approve", contentId)
                .with(user(teacher))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"releaseToStudents\":true}"))
        .andExpect(status().isOk());
  }

  /* --------------------------- editing workflow --------------------------- */

  @Test
  @DisplayName("A draft can be edited and saved")
  void draftCanBeEdited() throws Exception {
    long jobId = jobIdOf(submit(teacher, request(GenerationContentType.UNIT_NOTES), 202));
    drain(jobId);
    Long contentId = jobs.findById(jobId).orElseThrow().getContentId();

    mockMvc
        .perform(
            put("/api/v1/teacher/content/{id}", contentId)
                .with(user(teacher))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"Corrected title\",\"body\":\"Corrected body\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.title").value("Corrected title"))
        .andExpect(jsonPath("$.body").value("Corrected body"));
  }

  @Test
  @DisplayName("Approved content can no longer be edited")
  void approvedContentIsLocked() throws Exception {
    long jobId = jobIdOf(submit(teacher, request(GenerationContentType.UNIT_NOTES), 202));
    drain(jobId);
    Long contentId = jobs.findById(jobId).orElseThrow().getContentId();
    approveAndRelease(contentId);

    mockMvc
        .perform(
            put("/api/v1/teacher/content/{id}", contentId)
                .with(user(teacher))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"body\":\"should not stick\"}"))
        .andExpect(status().isConflict());
  }

  /* ------------------------------- job list ------------------------------- */

  @Test
  @DisplayName("A teacher sees only their own jobs")
  void jobListIsScoped() throws Exception {
    submit(teacher, request(GenerationContentType.UNIT_NOTES), 202);

    mockMvc
        .perform(get("/api/v1/teacher/content/generations").with(user(teacher)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(1));

    mockMvc
        .perform(get("/api/v1/teacher/content/generations").with(user(otherTeacher)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(0));
  }

  @Test
  @DisplayName("An identical resubmission reuses the existing job")
  void duplicateSubmissionIsSuppressed() throws Exception {
    long first = jobIdOf(submit(teacher, request(GenerationContentType.UNIT_NOTES), 202));
    long second = jobIdOf(submit(teacher, request(GenerationContentType.UNIT_NOTES), 202));

    Assertions.assertThat(second).isEqualTo(first);
  }

  @Test
  @DisplayName("A completed job cannot be retried")
  void completedJobCannotBeRetried() throws Exception {
    long jobId = jobIdOf(submit(teacher, request(GenerationContentType.UNIT_NOTES), 202));
    drain(jobId);

    mockMvc
        .perform(post("/api/v1/teacher/content/generations/{jobId}/retry", jobId)
            .with(user(teacher)).with(csrf()))
        .andExpect(status().isConflict());
  }

  /* --------------------------- generation history ------------------------- */

  @Test
  @DisplayName("Content is listed per syllabus")
  void contentListedPerSyllabus() throws Exception {
    long jobId = jobIdOf(submit(teacher, request(GenerationContentType.UNIT_NOTES), 202));
    drain(jobId);

    mockMvc
        .perform(
            get("/api/v1/teacher/content/syllabi/{syllabusId}/content", approvedSyllabus.getId())
                .with(user(teacher)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content.length()").value(1));

    mockMvc
        .perform(
            get("/api/v1/teacher/content/syllabi/{syllabusId}/content", approvedSyllabus.getId())
                .with(user(otherTeacher)))
        .andExpect(status().isNotFound());
  }

  /* -------------------------------- admin --------------------------------- */

  @Test
  @DisplayName("An admin may read content")
  void adminCanReadContent() throws Exception {
    long jobId = jobIdOf(submit(teacher, request(GenerationContentType.UNIT_NOTES), 202));
    drain(jobId);
    Long contentId = jobs.findById(jobId).orElseThrow().getContentId();

    mockMvc
        .perform(get("/api/v1/teacher/content/{id}", contentId).with(user(admin)))
        .andExpect(status().isOk());
  }

  @Test
  @DisplayName("No job is left RUNNING after execution")
  void jobsReachATerminalState() throws Exception {
    long jobId = jobIdOf(submit(teacher, request(GenerationContentType.UNIT_NOTES), 202));
    drain(jobId);

    var job = jobs.findById(jobId).orElseThrow();
    Assertions.assertThat(job.getStatus().isTerminal()).isTrue();
    Assertions.assertThat(job.getStatus()).isEqualTo(GenerationJobStatus.COMPLETED);
  }
}