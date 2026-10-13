package com.professor.assistant.service;

import com.professor.assistant.domain.AcademicClass;
import com.professor.assistant.domain.EnrollmentStatus;
import com.professor.assistant.domain.Role;
import com.professor.assistant.domain.Semester;
import com.professor.assistant.domain.Subject;
import com.professor.assistant.domain.Syllabus;
import com.professor.assistant.domain.SyllabusLearningOutcome;
import com.professor.assistant.domain.SyllabusStatus;
import com.professor.assistant.domain.SyllabusSubtopic;
import com.professor.assistant.domain.SyllabusTopic;
import com.professor.assistant.domain.SyllabusUnit;
import com.professor.assistant.domain.User;
import com.professor.assistant.dto.PageResponse;
import com.professor.assistant.dto.SyllabusDecisionRequest;
import com.professor.assistant.dto.SyllabusDetailDto;
import com.professor.assistant.dto.SyllabusDto;
import com.professor.assistant.dto.SyllabusStructureRequest;
import com.professor.assistant.exception.ConflictException;
import com.professor.assistant.exception.InvalidSyllabusStateException;
import com.professor.assistant.exception.InvalidUploadException;
import com.professor.assistant.exception.ResourceNotFoundException;
import com.professor.assistant.exception.SyllabusExtractionException;
import com.professor.assistant.repository.AcademicClassRepository;
import com.professor.assistant.repository.EnrollmentRepository;
import com.professor.assistant.repository.SubjectRepository;
import com.professor.assistant.repository.SyllabusRepository;
import com.professor.assistant.repository.TeachingAssignmentRepository;
import com.professor.assistant.repository.UserRepository;
import com.professor.assistant.security.AppUserDetails;
import com.professor.assistant.storage.FileStorageService;
import com.professor.assistant.syllabus.SyllabusExtractionService;
import com.professor.assistant.syllabus.SyllabusUploadValidator;
import com.professor.assistant.domain.SyllabusFileType;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * Syllabus upload, extraction, review and visibility.
 *
 * <p>Authorization rules, all enforced here and never in the controller:
 *
 * <ul>
 *   <li><b>ADMIN</b> — may read any syllabus.
 *   <li><b>TEACHER</b> — must hold a {@code teaching_assignment} for the
 *       (class, subject) pair <em>and</em> be the uploader. Client-supplied
 *       teacher ids are ignored; the principal is authoritative.
 *   <li><b>STUDENT</b> — may only read {@code APPROVED} syllabi for classes
 *       they are actively enrolled in.
 * </ul>
 *
 * <p>Unauthorized records surface as 404 rather than 403, matching the
 * Phase 3 convention: a caller must not learn that a record exists.
 */
@Service
public class SyllabusService {

  private static final Logger log = LoggerFactory.getLogger(SyllabusService.class);

  private final SyllabusRepository syllabi;
  private final AcademicClassRepository classes;
  private final SubjectRepository subjects;
  private final UserRepository users;
  private final TeachingAssignmentRepository assignments;
  private final EnrollmentRepository enrollments;
  private final FileStorageService storage;
  private final SyllabusUploadValidator validator;
  private final SyllabusExtractionService extraction;
  private final SyllabusMapper mapper;

  public SyllabusService(
      SyllabusRepository syllabi,
      AcademicClassRepository classes,
      SubjectRepository subjects,
      UserRepository users,
      TeachingAssignmentRepository assignments,
      EnrollmentRepository enrollments,
      FileStorageService storage,
      SyllabusUploadValidator validator,
      SyllabusExtractionService extraction,
      SyllabusMapper mapper) {
    this.syllabi = syllabi;
    this.classes = classes;
    this.subjects = subjects;
    this.users = users;
    this.assignments = assignments;
    this.enrollments = enrollments;
    this.storage = storage;
    this.validator = validator;
    this.extraction = extraction;
    this.mapper = mapper;
  }

  /* ------------------------------- upload ---------------------------------- */

  /**
   * Stores and extracts a syllabus for a class/subject the teacher is
   * assigned to.
   *
   * <p>Re-uploading the same placement creates a new version and demotes
   * previous ones, so history and the original files survive.
   */
  @Transactional
  public SyllabusDetailDto upload(
      AppUserDetails principal, Long classId, Long subjectId, MultipartFile file) {

    AcademicClass academicClass = requireClass(classId);
    Subject subject = requireSubject(subjectId);
    requireTeacherAssignment(principal, academicClass, subject);

    SyllabusFileType fileType = validator.validate(file);

    byte[] content;
    try {
      content = file.getBytes();
    } catch (java.io.IOException e) {
      throw new InvalidUploadException("The uploaded file could not be read", e);
    }

    Syllabus previous =
        syllabi
            .findFirstByAcademicClassIdAndSubjectIdAndSemesterIdOrderByVersionDesc(
                classId, subjectId, academicClass.getSemester().getId())
            .orElse(null);
    int nextVersion = previous == null ? 1 : previous.getVersion() + 1;

    // Storage key is random; the client filename never reaches the filesystem.
    String storageKey = storage.store(validator.extensionFor(fileType), content);
    String safeFilename = validator.sanitizeFilename(file.getOriginalFilename());
    String checksum = sha256(content);

    Syllabus syllabus =
        new Syllabus(
            academicClass,
            subject,
            academicClass.getSemester(),
            requireUser(principal.getId()),
            safeFilename,
            fileType.contentType(),
            (long) content.length,
            storageKey,
            checksum,
            nextVersion,
            true);

    // Only one version is current per placement.
    if (previous != null) {
      demoteAllVersions(classId, subjectId, academicClass.getSemester().getId());
    }

    Syllabus saved = syllabi.save(syllabus);
    runExtraction(saved);

    Syllabus reloaded = syllabi.findById(saved.getId()).orElseThrow();
    return mapper.toDetail(reloaded, currentWarning(reloaded));
  }

  /**
   * Runs extraction inline.
   *
   * <p>Synchronous by design for this phase: no broker is introduced just
   * for it. Bounds are enforced by the file size cap, the PDF page cap and
   * the extracted character cap, so a request cannot run away.
   */
  private void runExtraction(Syllabus syllabus) {
    syllabus.setStatus(SyllabusStatus.EXTRACTING);
    syllabi.save(syllabus);

    byte[] content;
    try {
      content = storage.load(syllabus.getStorageKey());
    } catch (RuntimeException e) {
      failExtraction(syllabus, "The stored file could not be read.");
      return;
    }

    SyllabusExtractionService.ExtractionResult result;
    try {
      SyllabusFileType type = SyllabusFileType.fromContentType(syllabus.getContentType());
      result = extraction.extract(type, content);
    } catch (SyllabusExtractionException e) {
      // Corrupt or encrypted: keep the upload so the teacher can retry.
      failExtraction(syllabus, e.getMessage());
      return;
    } catch (RuntimeException e) {
      log.warn("Unexpected extraction failure for syllabus {}", syllabus.getId());
      failExtraction(syllabus, "The document could not be processed.");
      return;
    }

    syllabus.setExtractedText(result.text());
    syllabus.setExtractionError(result.warning());

    if (result.hasText()) {
      applyUnits(syllabus, result.units());
      syllabus.setStatus(SyllabusStatus.REVIEW_REQUIRED);
    } else {
      // Readable file, no usable text (typically a scanned image).
      syllabus.setStatus(SyllabusStatus.EXTRACTION_FAILED);
    }
    syllabi.save(syllabus);
  }

  private void failExtraction(Syllabus syllabus, String message) {
    syllabus.setStatus(SyllabusStatus.EXTRACTION_FAILED);
    syllabus.setExtractionError(clip(message, 500));
    syllabus.setExtractedText(null);
    syllabi.save(syllabus);
  }

  /** Retries a failed extraction against the original stored file. */
  @Transactional
  public SyllabusDetailDto retryExtraction(AppUserDetails principal, Long syllabusId) {
    Syllabus syllabus = requireOwnedSyllabus(principal, syllabusId);
    if (syllabus.getStatus() != SyllabusStatus.EXTRACTION_FAILED) {
      throw new InvalidSyllabusStateException(
          "Only a failed extraction can be retried. This syllabus is "
              + syllabus.getStatus() + ".");
    }
    runExtraction(syllabus);
    Syllabus reloaded = syllabi.findById(syllabus.getId()).orElseThrow();
    return mapper.toDetail(reloaded, currentWarning(reloaded));
  }

  /* ------------------------------- teacher --------------------------------- */

  @Transactional(readOnly = true)
  public PageResponse<SyllabusDto> listMine(AppUserDetails principal, Pageable pageable) {
    Page<Syllabus> page = syllabi.findAllByUploadedById(principal.getId(), pageable);
    return PageResponse.from(page.map(mapper::toDto));
  }

  @Transactional(readOnly = true)
  public SyllabusDetailDto getForTeacher(AppUserDetails principal, Long syllabusId) {
    Syllabus syllabus = requireOwnedSyllabus(principal, syllabusId);
    return mapper.toDetail(syllabus, currentWarning(syllabus));
  }

  /** Replaces the unit tree. Only allowed while the structure is editable. */
  @Transactional
  public SyllabusDetailDto updateStructure(
      AppUserDetails principal, Long syllabusId, SyllabusStructureRequest request) {
    Syllabus syllabus = requireOwnedSyllabus(principal, syllabusId);

    if (syllabus.getStatus().isTerminalForEditing()) {
      throw new InvalidSyllabusStateException(
          "This syllabus is already approved and can no longer be edited. "
              + "Upload a new version to change it.");
    }
    if (syllabus.getStatus() == SyllabusStatus.EXTRACTING) {
      throw new InvalidSyllabusStateException(
          "Extraction is still running; try again in a moment.");
    }

    applyStructure(syllabus, request);
    // Saving a corrected structure puts it back into review, unless the
    // teacher had rejected it and is now fixing it.
    syllabus.setStatus(SyllabusStatus.REVIEW_REQUIRED);
    syllabus.setReviewNote(null);
    syllabus.setReviewedBy(null);
    syllabus.setReviewedAt(null);

    Syllabus saved = syllabi.save(syllabus);
    Syllabus reloaded = syllabi.findById(saved.getId()).orElseThrow();
    return mapper.toDetail(reloaded, currentWarning(reloaded));
  }

  @Transactional
  public SyllabusDetailDto approve(AppUserDetails principal, Long syllabusId) {
    Syllabus syllabus = requireOwnedSyllabus(principal, syllabusId);

    if (syllabus.getStatus() == SyllabusStatus.APPROVED) {
      throw new InvalidSyllabusStateException("This syllabus is already approved.");
    }
    if (syllabus.getStatus() == SyllabusStatus.EXTRACTING) {
      throw new InvalidSyllabusStateException(
          "Extraction is still running; approve once it finishes.");
    }
    if (syllabus.getUnits().isEmpty()) {
      throw new ConflictException(
          "Add at least one unit before approving this syllabus.");
    }

    syllabus.setStatus(SyllabusStatus.APPROVED);
    syllabus.setReviewNote(null);
    syllabus.setExtractionError(null);
    syllabus.setReviewedBy(requireUser(principal.getId()));
    syllabus.setReviewedAt(java.time.Instant.now());

    Syllabus saved = syllabi.save(syllabus);
    Syllabus reloaded = syllabi.findById(saved.getId()).orElseThrow();
    return mapper.toDetail(reloaded, null);
  }

  @Transactional
  public SyllabusDetailDto reject(
      AppUserDetails principal, Long syllabusId, SyllabusDecisionRequest request) {
    Syllabus syllabus = requireOwnedSyllabus(principal, syllabusId);

    if (syllabus.getStatus() == SyllabusStatus.EXTRACTING) {
      throw new InvalidSyllabusStateException(
          "Extraction is still running; reject once it finishes.");
    }
    syllabus.setStatus(SyllabusStatus.REJECTED);
    syllabus.setReviewNote(clip(request.reason(), 1000));
    syllabus.setReviewedBy(requireUser(principal.getId()));
    syllabus.setReviewedAt(java.time.Instant.now());

    Syllabus saved = syllabi.save(syllabus);
    Syllabus reloaded = syllabi.findById(saved.getId()).orElseThrow();
    return mapper.toDetail(reloaded, null);
  }

  @Transactional
  public void delete(AppUserDetails principal, Long syllabusId) {
    Syllabus syllabus = requireOwnedSyllabus(principal, syllabusId);

    if (syllabus.getStatus() == SyllabusStatus.EXTRACTING) {
      throw new InvalidSyllabusStateException(
          "Extraction is still running; deletion is not allowed right now.");
    }

    String storageKey = syllabus.getStorageKey();
    syllabi.delete(syllabus);
    // Best effort: a missing file must not block the database delete.
    try {
      storage.delete(storageKey);
    } catch (RuntimeException e) {
      log.warn("Syllabus {} deleted but its stored file could not be removed", syllabusId);
    }
  }

  /**
   * Authorized download of the original file.
   *
   * @return the bytes plus the sanitized display filename
   */
  @Transactional(readOnly = true)
  public DownloadPayload downloadForTeacher(AppUserDetails principal, Long syllabusId) {
    Syllabus syllabus = requireOwnedSyllabus(principal, syllabusId);
    return new DownloadPayload(
        storage.load(syllabus.getStorageKey()),
        syllabus.getOriginalFilename(),
        syllabus.getContentType());
  }

  /* ------------------------------- student --------------------------------- */

  /** Approved syllabi for classes the student is actively enrolled in. */
  @Transactional(readOnly = true)
  public List<SyllabusDetailDto> listForStudent(AppUserDetails principal) {
    Set<Long> classIds =
        new java.util.LinkedHashSet<>(
            enrollments.findClassIdsByStudentIdAndStatus(
                principal.getId(), EnrollmentStatus.ENROLLED));
    if (classIds.isEmpty()) {
      return List.of();
    }
    List<Syllabus> approved =
        syllabi.findApprovedForClasses(classIds, SyllabusStatus.APPROVED);
    // Keep only the newest approved version per placement.
    return newestPerPlacement(approved).stream().map(s -> mapper.toDetail(s, null)).toList();
  }

  @Transactional(readOnly = true)
  public SyllabusDetailDto getForStudent(AppUserDetails principal, Long syllabusId) {
    Syllabus syllabus =
        syllabi.findById(syllabusId).orElseThrow(() -> ResourceNotFoundException.of("Syllabus", syllabusId));

    // A draft, failed or rejected record must be indistinguishable from a
    // record that does not exist.
    if (syllabus.getStatus() != SyllabusStatus.APPROVED) {
      throw ResourceNotFoundException.of("Syllabus", syllabusId);
    }
    boolean enrolled =
        enrollments
            .findByStudentIdAndAcademicClassId(principal.getId(), syllabus.getAcademicClass().getId())
            .map(e -> e.getStatus() == EnrollmentStatus.ENROLLED)
            .orElse(false);
    if (!enrolled) {
      throw ResourceNotFoundException.of("Syllabus", syllabusId);
    }
    return mapper.toDetail(syllabus, null);
  }

  /* ------------------------------- helpers --------------------------------- */

  /**
   * A teacher may act only on a syllabus they uploaded, and only while they
   * still hold the teaching assignment for its (class, subject).
   */
  private Syllabus requireOwnedSyllabus(AppUserDetails principal, Long syllabusId) {
    Syllabus syllabus =
        syllabi.findById(syllabusId).orElseThrow(() -> ResourceNotFoundException.of("Syllabus", syllabusId));

    if (principal.getRole() == Role.ADMIN) {
      return syllabus;
    }
    if (principal.getRole() != Role.TEACHER) {
      throw ResourceNotFoundException.of("Syllabus", syllabusId);
    }
    if (!syllabus.getUploadedBy().getId().equals(principal.getId())) {
      // Another teacher's syllabus: reveal nothing.
      throw ResourceNotFoundException.of("Syllabus", syllabusId);
    }
    // Re-check the assignment, so a revoked teacher loses access immediately.
    if (!assignments.existsByTeacherIdAndAcademicClassIdAndSubjectIdAndSemesterId(
        principal.getId(),
        syllabus.getAcademicClass().getId(),
        syllabus.getSubject().getId(),
        syllabus.getSemester().getId())) {
      throw ResourceNotFoundException.of("Syllabus", syllabusId);
    }
    return syllabus;
  }

  private void requireTeacherAssignment(
      AppUserDetails principal, AcademicClass academicClass, Subject subject) {
    if (principal.getRole() == Role.ADMIN) {
      return;
    }
    if (principal.getRole() != Role.TEACHER) {
      throw new InvalidUploadException("Only teachers can upload syllabi");
    }
    boolean assigned =
        assignments.existsByTeacherIdAndAcademicClassIdAndSubjectIdAndSemesterId(
            principal.getId(),
            academicClass.getId(),
            subject.getId(),
            academicClass.getSemester().getId());
    if (!assigned) {
      throw new InvalidUploadException(
          "You are not assigned to teach this subject in this class");
    }
  }

  private void demoteAllVersions(Long classId, Long subjectId, Long semesterId) {
    for (Syllabus existing :
        syllabi.findAllByAcademicClassIdOrderByVersionDesc(classId)) {
      if (existing.getSubject().getId().equals(subjectId)
          && existing.getSemester().getId().equals(semesterId)
          && existing.isCurrent()) {
        existing.setCurrent(false);
        syllabi.save(existing);
      }
    }
  }

  private void applyUnits(
      Syllabus syllabus, List<com.professor.assistant.syllabus.ParsedUnit> parsed) {
    List<SyllabusUnit> units = new ArrayList<>();
    int unitPosition = 0;
    for (com.professor.assistant.syllabus.ParsedUnit parsedUnit : parsed) {
      SyllabusUnit unit = new SyllabusUnit(parsedUnit.title(), unitPosition++);
      List<SyllabusTopic> topics = new ArrayList<>();
      int topicPosition = 0;
      for (com.professor.assistant.syllabus.ParsedTopic parsedTopic : parsedUnit.topics()) {
        SyllabusTopic topic = new SyllabusTopic(parsedTopic.title(), topicPosition++);
        List<SyllabusSubtopic> subtopics = new ArrayList<>();
        int subPosition = 0;
        for (String sub : parsedTopic.subtopics()) {
          subtopics.add(new SyllabusSubtopic(sub, subPosition++));
        }
        topic.replaceSubtopics(subtopics);
        topics.add(topic);
      }
      unit.replaceTopics(topics);
      units.add(unit);
    }
    syllabus.replaceUnits(units);
  }

  private void applyStructure(Syllabus syllabus, SyllabusStructureRequest request) {
    List<SyllabusUnit> units = new ArrayList<>();
    int unitPosition = 0;
    for (SyllabusStructureRequest.UnitRequest unitRequest :
        safeList(request == null ? null : request.units())) {
      SyllabusUnit unit = new SyllabusUnit(unitRequest.title().strip(), unitPosition);
      List<SyllabusTopic> topics = new ArrayList<>();
      int topicPosition = 0;
      for (SyllabusStructureRequest.TopicRequest topicRequest : unitRequest.topics()) {
        SyllabusTopic topic = new SyllabusTopic(topicRequest.title().strip(), topicPosition);
        List<SyllabusSubtopic> subtopics = new ArrayList<>();
        int subPosition = 0;
        for (String sub : topicRequest.subtopics()) {
          if (sub != null && !sub.isBlank()) {
            subtopics.add(new SyllabusSubtopic(sub.strip(), subPosition++));
          }
        }
        topic.replaceSubtopics(subtopics);
        topics.add(topic);
      }
      unit.replaceTopics(topics);
      units.add(unit);
      unitPosition++;
    }
    syllabus.replaceUnits(units);

    List<SyllabusLearningOutcome> outcomes = new ArrayList<>();
    int outcomePosition = 0;
    for (SyllabusStructureRequest.OutcomeRequest outcomeRequest :
        safeList(request == null ? null : request.learningOutcomes())) {
      SyllabusLearningOutcome outcome =
          new SyllabusLearningOutcome(outcomeRequest.statement().strip(), outcomePosition++);
      if (outcomeRequest.unitIndex() != null
          && outcomeRequest.unitIndex() >= 0
          && outcomeRequest.unitIndex() < units.size()) {
        outcome.setUnit(units.get(outcomeRequest.unitIndex()));
      }
      outcomes.add(outcome);
    }
    syllabus.replaceLearningOutcomes(outcomes);
  }

  private static <T> List<T> safeList(List<T> value) {
    return value == null ? List.of() : value;
  }

  /** Keeps only the highest version per (class, subject, semester). */
  private List<Syllabus> newestPerPlacement(List<Syllabus> candidates) {
    java.util.Set<String> seen = new java.util.HashSet<>();
    List<Syllabus> result = new ArrayList<>();
    // Caller passes newest-first, so the first hit per placement wins.
    for (Syllabus candidate : candidates) {
      String key =
          candidate.getAcademicClass().getId()
              + ":"
              + candidate.getSubject().getId()
              + ":"
              + candidate.getSemester().getId();
      if (seen.add(key)) {
        result.add(candidate);
      }
    }
    return result;
  }

  /**
   * The warning to show the teacher, if any. Extraction stores either the
   * OCR message or the "no units detected" guidance in the same column.
   */
  private String currentWarning(Syllabus syllabus) {
    return syllabus.getExtractionError();
  }

  private AcademicClass requireClass(Long id) {
    return classes.findById(id).orElseThrow(() -> ResourceNotFoundException.of("Class", id));
  }

  private Subject requireSubject(Long id) {
    return subjects.findById(id).orElseThrow(() -> ResourceNotFoundException.of("Subject", id));
  }

  private User requireUser(Long id) {
    return users.findById(id).orElseThrow(() -> ResourceNotFoundException.of("User", id));
  }

  private String sha256(byte[] content) {
    try {
      return HexFormat.of()
          .formatHex(MessageDigest.getInstance("SHA-256").digest(content));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 is unavailable", e);
    }
  }

  private String clip(String value, int max) {
    if (value == null) {
      return null;
    }
    return value.length() > max ? value.substring(0, max) : value;
  }

  /** A stored file plus the metadata needed to stream it safely. */
  public record DownloadPayload(byte[] content, String filename, String contentType) {}
}