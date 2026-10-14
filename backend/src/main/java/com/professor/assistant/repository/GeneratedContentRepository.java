package com.professor.assistant.repository;

import com.professor.assistant.domain.ContentReviewStatus;
import com.professor.assistant.domain.GeneratedContent;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface GeneratedContentRepository extends JpaRepository<GeneratedContent, Long> {

  /** Association traversal must be spelled out for Spring Data. */
  Optional<GeneratedContent> findByJob_Id(Long jobId);

  List<GeneratedContent> findAllBySyllabusIdOrderByCreatedAtDesc(Long syllabusId);

  Page<GeneratedContent> findAllBySyllabusId(Long syllabusId, Pageable pageable);

  /**
   * Student-visible content: approved, explicitly released, and therefore
   * never teacher-only. Enrollment scope is applied by the caller, which
   * passes only the classes the student is actively enrolled in.
   */
  @Query(
      """
      select c from GeneratedContent c
      where c.syllabus.academicClass.id in :classIds
        and c.reviewStatus = :status
        and c.releasedToStudents = true
      order by c.createdAt desc
      """)
  List<GeneratedContent> findReleasedForClasses(
      @Param("classIds") Collection<Long> classIds,
      @Param("status") ContentReviewStatus status);

  long countBySyllabusId(Long syllabusId);
}