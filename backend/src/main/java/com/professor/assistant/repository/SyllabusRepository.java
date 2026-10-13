package com.professor.assistant.repository;

import com.professor.assistant.domain.Syllabus;
import com.professor.assistant.domain.SyllabusStatus;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SyllabusRepository extends JpaRepository<Syllabus, Long> {

  Optional<Syllabus> findByStorageKey(String storageKey);

  /** Highest existing version for this placement, or empty when new. */
  Optional<Syllabus> findFirstByAcademicClassIdAndSubjectIdAndSemesterIdOrderByVersionDesc(
      Long classId, Long subjectId, Long semesterId);

  // Note: the "Class" prefix must be spelled out. A derived query on
  // "ClassId" is parsed as java.lang.Class and fails to resolve.
  List<Syllabus> findAllByAcademicClassIdOrderByVersionDesc(Long classId);

  List<Syllabus> findAllByUploadedByIdOrderByIdDesc(Long uploaderId);

  Page<Syllabus> findAllByUploadedById(Long uploaderId, Pageable pageable);

  /**
   * Approved syllabi for a set of classes, newest version first. Used by the
   * student view, which must never return drafts or rejected records.
   */
  @EntityGraph(attributePaths = {"academicClass", "subject", "semester"})
  @Query(
      """
      select s from Syllabus s
      where s.academicClass.id in :classIds
        and s.status = :status
      order by s.academicClass.id asc, s.version desc
      """)
  List<Syllabus> findApprovedForClasses(
      @Param("classIds") Collection<Long> classIds, @Param("status") SyllabusStatus status);
}