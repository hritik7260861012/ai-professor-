package com.professor.assistant.controller;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.professor.assistant.domain.AcademicClass;
import com.professor.assistant.domain.Department;
import com.professor.assistant.domain.Role;
import com.professor.assistant.domain.SyllabusStatus;
import com.professor.assistant.domain.Subject;
import com.professor.assistant.dto.SyllabusStructureRequest;
import com.professor.assistant.repository.AcademicClassRepository;
import com.professor.assistant.repository.AcademicProgramRepository;
import com.professor.assistant.repository.AcademicYearRepository;
import com.professor.assistant.repository.ClassSubjectRepository;
import com.professor.assistant.repository.DepartmentRepository;
import com.professor.assistant.repository.EnrollmentRepository;
import com.professor.assistant.repository.SemesterRepository;
import com.professor.assistant.repository.SubjectRepository;
import com.professor.assistant.repository.SyllabusRepository;
import com.professor.assistant.repository.TeachingAssignmentRepository;
import com.professor.assistant.repository.UserRepository;
import com.professor.assistant.security.AppUserDetails;
import com.professor.assistant.service.AuthService;
import com.professor.assistant.support.AcademicFixture;
import com.professor.assistant.support.DocumentFixtures;
import java.util.List;
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
 * Phase 4 end-to-end: upload, lifecycle transitions and the authorization
 * matrix that keeps one teacher's syllabus away from everyone else.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SyllabusControllerTest {

  private static final String SYLLABUS_TEXT =
      String.join(
          "\n",
          "Unit I: Foundations",
          "1.1 History of computing",
          "1.2 Number systems",
          "Unit II: Programming",
          "2.1 Variables and types");

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
  @Autowired private UserRepository users;
  @Autowired private AuthService authService;
  @Autowired private PasswordEncoder passwordEncoder;

  private AcademicFixture fixture;
  private AppUserDetails teacher;
  private AppUserDetails student;
  private AppUserDetails admin;
  private AcademicClass academicClass;
  private Subject subject;

  @BeforeEach
  void setUp() {
    fixture =
        new AcademicFixture(
            departments, programs, academicYears, semesters, subjects, classes,
            classSubjects, enrollments, assignments, users, authService, passwordEncoder);

    teacher = fixture.principal(Role.TEACHER);
    student = fixture.principal(Role.STUDENT);
    admin = fixture.principal(Role.ADMIN);

    Department department = fixture.department();
    academicClass = fixture.standaloneClass();
    subject = fixture.subject(department, null);
    fixture.attach(academicClass, subject);
    fixture.assign(academicClass, subject, teacher);
    fixture.enroll(users.findById(student.getId()).orElseThrow(), academicClass);
  }

  private MockMultipartFile txtFile(String name) {
    return new MockMultipartFile(
        "file", name, "text/plain", DocumentFixtures.txt(SYLLABUS_TEXT));
  }

  private MvcResult uploadAs(AppUserDetails principal, MockMultipartFile file) throws Exception {
    return mockMvc
        .perform(
            multipart("/api/v1/teacher/syllabi")
                .file(file)
                .param("classId", String.valueOf(academicClass.getId()))
                .param("subjectId", String.valueOf(subject.getId()))
                .with(user(principal))
                .with(csrf()))
        .andReturn();
  }

  private Long uploadSyllabus(AppUserDetails principal) throws Exception {
    MvcResult result = uploadAs(principal, txtFile("syllabus.txt"));
    org.assertj.core.api.Assertions.assertThat(result.getResponse().getStatus())
        .as("upload should succeed, body=%s", result.getResponse().getContentAsString())
        .isEqualTo(201);
    return objectMapper.readTree(result.getResponse().getContentAsString()).get("syllabus").get("id").asLong();
  }

  /* -------------------------------- upload --------------------------------- */

  @Test
  @DisplayName("Teacher uploads a syllabus and it is extracted into units")
  void teacherUploadsSyllabus() throws Exception {
    MvcResult result = uploadAs(teacher, txtFile("syllabus.txt"));

    org.assertj.core.api.Assertions.assertThat(result.getResponse().getStatus()).isEqualTo(201);
    String body = result.getResponse().getContentAsString();
    org.assertj.core.api.Assertions.assertThat(body).contains("\"status\":\"REVIEW_REQUIRED\"");
    org.assertj.core.api.Assertions.assertThat(body).contains("Foundations");
    org.assertj.core.api.Assertions.assertThat(body).contains("Number systems");
    // Metadata persisted.
    org.assertj.core.api.Assertions.assertThat(body).contains("\"version\":1");
    org.assertj.core.api.Assertions.assertThat(body).contains("\"current\":true");
    // The storage key must never leak to the client.
    org.assertj.core.api.Assertions.assertThat(body).doesNotContain("storageKey");
  }

  @Test
  @DisplayName("PDF and DOCX uploads are both accepted")
  void acceptsPdfAndDocx() throws Exception {
    mockMvc
        .perform(
            multipart("/api/v1/teacher/syllabi")
                .file(
                    new MockMultipartFile(
                        "file",
                        "s.pdf",
                        "application/pdf",
                        DocumentFixtures.pdfWithText(SYLLABUS_TEXT.split("\n"))))
                .param("classId", String.valueOf(academicClass.getId()))
                .param("subjectId", String.valueOf(subject.getId()))
                .with(user(teacher))
                .with(csrf()))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.syllabus.contentType").value("application/pdf"));

    mockMvc
        .perform(
            multipart("/api/v1/teacher/syllabi")
                .file(
                    new MockMultipartFile(
                        "file",
                        "s.docx",
                        "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                        DocumentFixtures.docxWithParagraphs(SYLLABUS_TEXT.split("\n"))))
                .param("classId", String.valueOf(academicClass.getId()))
                .param("subjectId", String.valueOf(subject.getId()))
                .with(user(teacher))
                .with(csrf()))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.syllabus.contentType")
            .value("application/vnd.openxmlformats-officedocument.wordprocessingml.document"));
  }

  @Test
  @DisplayName("An unsupported file type is rejected with a clear message")
  void rejectsUnsupportedUpload() throws Exception {
    mockMvc
        .perform(
            multipart("/api/v1/teacher/syllabi")
                .file(
                    new MockMultipartFile(
                        "file", "s.exe", "application/x-msdownload", DocumentFixtures.corrupt()))
                .param("classId", String.valueOf(academicClass.getId()))
                .param("subjectId", String.valueOf(subject.getId()))
                .with(user(teacher))
                .with(csrf()))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Unsupported file type")));
  }

  @Test
  @DisplayName("Teacher cannot upload for a class they are not assigned to")
  void cannotUploadToUnassignedClass() throws Exception {
    AcademicClass otherClass = fixture.standaloneClass();
    Subject otherSubject = fixture.subject(departments.findAll().get(0), null);

    mockMvc
        .perform(
            multipart("/api/v1/teacher/syllabi")
                .file(txtFile("s.txt"))
                .param("classId", String.valueOf(otherClass.getId()))
                .param("subjectId", String.valueOf(otherSubject.getId()))
                .with(user(teacher))
                .with(csrf()))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message")
            .value(org.hamcrest.Matchers.containsString("not assigned to teach")));
  }

  @Test
  @DisplayName("Teacher cannot upload for a subject they are not assigned to")
  void cannotUploadToUnassignedSubject() throws Exception {
    Subject unassigned = fixture.subject(departments.findAll().get(0), null);

    mockMvc
        .perform(
            multipart("/api/v1/teacher/syllabi")
                .file(txtFile("s.txt"))
                .param("classId", String.valueOf(academicClass.getId()))
                .param("subjectId", String.valueOf(unassigned.getId()))
                .with(user(teacher))
                .with(csrf()))
        .andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("A student cannot upload a syllabus")
  void studentCannotUpload() throws Exception {
    mockMvc
        .perform(
            multipart("/api/v1/teacher/syllabi")
                .file(txtFile("s.txt"))
                .param("classId", String.valueOf(academicClass.getId()))
                .param("subjectId", String.valueOf(subject.getId()))
                .with(user(student))
                .with(csrf()))
        .andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("Anonymous callers cannot upload")
  void anonymousCannotUpload() throws Exception {
    mockMvc
        .perform(
            multipart("/api/v1/teacher/syllabi")
                .file(txtFile("s.txt"))
                .param("classId", String.valueOf(academicClass.getId()))
                .param("subjectId", String.valueOf(subject.getId()))
                .with(csrf()))
        .andExpect(status().isUnauthorized());
  }

  /* ------------------------------ versioning ------------------------------- */

  @Test
  @DisplayName("Re-uploading the same placement creates a new version")
  void reUploadCreatesNewVersion() throws Exception {
    Long first = uploadSyllabus(teacher);
    Long second = uploadSyllabus(teacher);

    org.assertj.core.api.Assertions.assertThat(second).isNotEqualTo(first);
    org.assertj.core.api.Assertions.assertThat(syllabi.findById(second).orElseThrow().getVersion())
        .isEqualTo(2);
    // The original upload is retained.
    org.assertj.core.api.Assertions.assertThat(syllabi.findById(first).orElseThrow()).isNotNull();
    org.assertj.core.api.Assertions.assertThat(syllabi.findById(first).orElseThrow().isCurrent()).isFalse();
    org.assertj.core.api.Assertions.assertThat(syllabi.findById(second).orElseThrow().isCurrent()).isTrue();
  }

  /* ------------------------------ lifecycle -------------------------------- */

  @Test
  @DisplayName("Teacher edits, then approves the structure")
  void approveMakesItVisibleToStudents() throws Exception {
    Long id = uploadSyllabus(teacher);

    String structure =
        objectMapper.writeValueAsString(
            new SyllabusStructureRequest(
                List.of(
                    new SyllabusStructureRequest.UnitRequest(
                        "Unit I: Edited",
                        List.of(
                            new SyllabusStructureRequest.TopicRequest(
                                "1.1 Edited topic", List.of("detail one", "detail two"))))),
                List.of(new SyllabusStructureRequest.OutcomeRequest("Students can explain X", null))));

    mockMvc
        .perform(
            put("/api/v1/teacher/syllabi/{id}/structure", id)
                .with(user(teacher))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(structure))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.units[0].title").value("Unit I: Edited"))
        .andExpect(jsonPath("$.units[0].topics[0].subtopics.length()").value(2))
        .andExpect(jsonPath("$.learningOutcomes[0].statement").value("Students can explain X"));

    mockMvc
        .perform(post("/api/v1/teacher/syllabi/{id}/approve", id).with(user(teacher)).with(csrf()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.syllabus.status").value("APPROVED"))
        .andExpect(jsonPath("$.syllabus.reviewedById").value(teacher.getId()));

    // Now the enrolled student can read it.
    mockMvc
        .perform(get("/api/v1/student/syllabi").with(user(student)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].syllabus.status").value("APPROVED"));

    mockMvc
        .perform(get("/api/v1/student/syllabi/{id}", id).with(user(student)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.units[0].title").value("Unit I: Edited"));
  }

  @Test
  @DisplayName("An approved syllabus can no longer be edited")
  void editingAfterApprovalIsRejected() throws Exception {
    Long id = uploadSyllabus(teacher);
    mockMvc
        .perform(post("/api/v1/teacher/syllabi/{id}/approve", id).with(user(teacher)).with(csrf()))
        .andExpect(status().isOk());

    String structure =
        objectMapper.writeValueAsString(
            new SyllabusStructureRequest(
                List.of(new SyllabusStructureRequest.UnitRequest("Unit I", List.of())),
                List.of()));

    mockMvc
        .perform(
            put("/api/v1/teacher/syllabi/{id}/structure", id)
                .with(user(teacher))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(structure))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.message")
            .value(org.hamcrest.Matchers.containsString("already approved")));
  }

  @Test
  @DisplayName("A rejected syllabus is not visible to students")
  void rejectedSyllabusIsHidden() throws Exception {
    Long id = uploadSyllabus(teacher);

    mockMvc
        .perform(
            post("/api/v1/teacher/syllabi/{id}/reject", id)
                .with(user(teacher))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"Unit headings are wrong\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.syllabus.status").value("REJECTED"))
        .andExpect(jsonPath("$.syllabus.reviewNote").value("Unit headings are wrong"));

    mockMvc
        .perform(get("/api/v1/student/syllabi/{id}", id).with(user(student)))
        .andExpect(status().isNotFound());

    mockMvc
        .perform(get("/api/v1/student/syllabi").with(user(student)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(0));
  }

  @Test
  @DisplayName("Approving a syllabus with no units is refused")
  void cannotApproveEmptyStructure() throws Exception {
    // A text file with no unit headings produces no units.
    MockMultipartFile plain =
        new MockMultipartFile(
            "file",
            "s.txt",
            "text/plain",
            DocumentFixtures.txt(
                "Just a course description with no numbered units at all in this document."));
    Long id = objectMapper
        .readTree(uploadAs(teacher, plain).getResponse().getContentAsString())
        .get("syllabus")
        .get("id")
        .asLong();

    org.assertj.core.api.Assertions.assertThat(syllabi.findById(id).orElseThrow().getStatus())
        .isEqualTo(SyllabusStatus.REVIEW_REQUIRED);

    mockMvc
        .perform(post("/api/v1/teacher/syllabi/{id}/approve", id).with(user(teacher)).with(csrf()))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.message")
            .value(org.hamcrest.Matchers.containsString("at least one unit")));
  }

  @Test
  @DisplayName("A failed extraction can be retried without re-uploading")
  void failedExtractionCanBeRetried() throws Exception {
    // A scanned-image-style PDF: valid file, no extractable text.
    MockMultipartFile scanned =
        new MockMultipartFile(
            "file", "scan.pdf", "application/pdf", DocumentFixtures.pdfWithoutText());

    Long id = objectMapper
        .readTree(uploadAs(teacher, scanned).getResponse().getContentAsString())
        .get("syllabus")
        .get("id")
        .asLong();

    org.assertj.core.api.Assertions.assertThat(syllabi.findById(id).orElseThrow().getStatus())
        .isEqualTo(SyllabusStatus.EXTRACTION_FAILED);

    // Honest about OCR rather than pretending it worked.
    mockMvc
        .perform(get("/api/v1/teacher/syllabi/{id}", id).with(user(teacher)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.warning")
            .value(org.hamcrest.Matchers.containsString("OCR")));

    // Students never see it.
    mockMvc
        .perform(get("/api/v1/student/syllabi/{id}", id).with(user(student)))
        .andExpect(status().isNotFound());

    mockMvc
        .perform(
            post("/api/v1/teacher/syllabi/{id}/retry-extraction", id)
                .with(user(teacher))
                .with(csrf()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.syllabus.status").value("EXTRACTION_FAILED"));
  }

  @Test
  @DisplayName("Retry is refused when extraction did not fail")
  void retryRefusedWhenNotFailed() throws Exception {
    Long id = uploadSyllabus(teacher);

    mockMvc
        .perform(
            post("/api/v1/teacher/syllabi/{id}/retry-extraction", id)
                .with(user(teacher))
                .with(csrf()))
        .andExpect(status().isConflict());
  }

  /* ---------------------------- authorization ------------------------------ */

  @Test
  @DisplayName("A teacher cannot see another teacher's syllabus")
  void teacherCannotAccessAnotherTeachersSyllabus() throws Exception {
    Long id = uploadSyllabus(teacher);
    AppUserDetails colleague = fixture.principal(Role.TEACHER);

    mockMvc
        .perform(get("/api/v1/teacher/syllabi/{id}", id).with(user(colleague)))
        .andExpect(status().isNotFound());
    mockMvc
        .perform(get("/api/v1/teacher/syllabi/{id}/file", id).with(user(colleague)))
        .andExpect(status().isNotFound());
    mockMvc
        .perform(post("/api/v1/teacher/syllabi/{id}/approve", id).with(user(colleague)).with(csrf()))
        .andExpect(status().isNotFound());
  }

  @Test
  @DisplayName("A student cannot reach the teacher syllabus API")
  void studentCannotUseTeacherApi() throws Exception {
    Long id = uploadSyllabus(teacher);

    mockMvc
        .perform(get("/api/v1/teacher/syllabi/{id}", id).with(user(student)))
        .andExpect(status().isNotFound());
    mockMvc
        .perform(get("/api/v1/teacher/syllabi/{id}/file", id).with(user(student)))
        .andExpect(status().isNotFound());
    mockMvc
        .perform(delete("/api/v1/teacher/syllabi/{id}", id).with(user(student)).with(csrf()))
        .andExpect(status().isNotFound());
  }

  @Test
  @DisplayName("A draft syllabus is invisible to an enrolled student")
  void studentCannotSeeDraft() throws Exception {
    Long id = uploadSyllabus(teacher);

    mockMvc
        .perform(get("/api/v1/student/syllabi/{id}", id).with(user(student)))
        .andExpect(status().isNotFound());
  }

  @Test
  @DisplayName("A student cannot read an approved syllabus for a class they are not enrolled in")
  void studentCannotReadOtherClassesSyllabus() throws Exception {
    Long id = uploadSyllabus(teacher);
    mockMvc
        .perform(post("/api/v1/teacher/syllabi/{id}/approve", id).with(user(teacher)).with(csrf()))
        .andExpect(status().isOk());

    AppUserDetails outsider = fixture.principal(Role.STUDENT);

    mockMvc
        .perform(get("/api/v1/student/syllabi/{id}", id).with(user(outsider)))
        .andExpect(status().isNotFound());
    mockMvc
        .perform(get("/api/v1/student/syllabi").with(user(outsider)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(0));
  }

  @Test
  @DisplayName("Anonymous callers are rejected on both syllabus APIs")
  void anonymousIsRejected() throws Exception {
    mockMvc.perform(get("/api/v1/teacher/syllabi")).andExpect(status().isUnauthorized());
    mockMvc.perform(get("/api/v1/student/syllabi")).andExpect(status().isUnauthorized());
  }

  @Test
  @DisplayName("A revoked assignment removes access")
  void revokedAssignmentRemovesAccess() throws Exception {
    Long id = uploadSyllabus(teacher);

    assignments.deleteAll(assignments.findAllByTeacherIdOrderByIdAsc(teacher.getId()));

    mockMvc
        .perform(get("/api/v1/teacher/syllabi/{id}", id).with(user(teacher)))
        .andExpect(status().isNotFound());
  }

  /* ------------------------------- download -------------------------------- */

  @Test
  @DisplayName("An authorized teacher downloads the original file")
  void teacherDownloadsFile() throws Exception {
    Long id = uploadSyllabus(teacher);

    MvcResult result =
        mockMvc
            .perform(get("/api/v1/teacher/syllabi/{id}/file", id).with(user(teacher)))
            .andExpect(status().isOk())
            .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("attachment")))
            .andReturn();

    org.assertj.core.api.Assertions.assertThat(result.getResponse().getContentAsByteArray())
        .isEqualTo(DocumentFixtures.txt(SYLLABUS_TEXT));
  }

  @Test
  @DisplayName("The download response never exposes a filesystem path")
  void downloadDoesNotLeakPaths() throws Exception {
    Long id = uploadSyllabus(teacher);

    MvcResult result =
        mockMvc
            .perform(get("/api/v1/teacher/syllabi/{id}/file", id).with(user(teacher)))
            .andReturn();

    String disposition = result.getResponse().getHeader("Content-Disposition");
    org.assertj.core.api.Assertions.assertThat(disposition).doesNotContain("/");
    org.assertj.core.api.Assertions.assertThat(disposition).doesNotContain("storage");
    org.assertj.core.api.Assertions.assertThat(disposition).doesNotContain("\\");
  }

  /* ------------------------------ listing ---------------------------------- */

  @Test
  @DisplayName("A teacher sees only their own uploads")
  void teacherListsOnlyOwnSyllabi() throws Exception {
    uploadSyllabus(teacher);

    mockMvc
        .perform(get("/api/v1/teacher/syllabi").with(user(teacher)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(1));

    AppUserDetails colleague = fixture.principal(Role.TEACHER);
    mockMvc
        .perform(get("/api/v1/teacher/syllabi").with(user(colleague)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(0));
  }

  @Test
  @DisplayName("An admin can read any syllabus")
  void adminCanReadAnySyllabus() throws Exception {
    Long id = uploadSyllabus(teacher);

    mockMvc
        .perform(get("/api/v1/teacher/syllabi/{id}", id).with(user(admin)))
        .andExpect(status().isOk());
  }

  /* -------------------------------- delete --------------------------------- */

  @Test
  @DisplayName("Deleting removes the record")
  void teacherDeletesSyllabus() throws Exception {
    Long id = uploadSyllabus(teacher);

    mockMvc
        .perform(delete("/api/v1/teacher/syllabi/{id}", id).with(user(teacher)).with(csrf()))
        .andExpect(status().isNoContent());

    org.assertj.core.api.Assertions.assertThat(syllabi.findById(id)).isEmpty();
  }

  @Test
  @DisplayName("Only the newest approved version is offered to students")
  void studentSeesNewestApprovedVersionOnly() throws Exception {
    Long first = uploadSyllabus(teacher);
    mockMvc
        .perform(post("/api/v1/teacher/syllabi/{id}/approve", first).with(user(teacher)).with(csrf()))
        .andExpect(status().isOk());

    Long second = uploadSyllabus(teacher);
    mockMvc
        .perform(post("/api/v1/teacher/syllabi/{id}/approve", second).with(user(teacher)).with(csrf()))
        .andExpect(status().isOk());

    mockMvc
        .perform(get("/api/v1/student/syllabi").with(user(student)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].syllabus.version").value(2));
  }
}