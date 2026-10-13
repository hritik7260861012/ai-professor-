package com.professor.assistant.repository;

import com.professor.assistant.domain.Subject;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SubjectRepository
    extends JpaRepository<Subject, Long>, JpaSpecificationExecutor<Subject> {

  Optional<Subject> findByCode(String code);

  boolean existsByCode(String code);

  long countByDepartmentId(Long departmentId);

  /**
   * Subjects offered to a student through their enrollments. This is the
   * authority for what a STUDENT may read.
   */
  @Query(
      """
      select distinct cs.subject.id
      from ClassSubject cs
      where cs.academicClass.id in :classIds
      """)
  java.util.List<Long> findSubjectIdsForClasses(
      @Param("classIds") java.util.Collection<Long> classIds);
}