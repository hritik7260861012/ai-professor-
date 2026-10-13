package com.professor.assistant.repository;

import com.professor.assistant.domain.Enrollment;
import com.professor.assistant.domain.EnrollmentStatus;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EnrollmentRepository extends JpaRepository<Enrollment, Long> {

  Optional<Enrollment> findByStudentIdAndAcademicClassId(Long studentId, Long classId);

  boolean existsByStudentIdAndAcademicClassId(Long studentId, Long classId);

  List<Enrollment> findAllByStudentIdOrderByIdAsc(Long studentId);

  List<Enrollment> findAllByAcademicClassIdOrderByIdAsc(Long classId);

  /** Classes this student is actively enrolled in. */
  @Query(
      """
      select e.academicClass.id
      from Enrollment e
      where e.student.id = :studentId and e.status = :status
      """)
  List<Long> findClassIdsByStudentIdAndStatus(
      @Param("studentId") Long studentId, @Param("status") EnrollmentStatus status);

  /** Students enrolled in any of the given classes. */
  @Query(
      """
      select distinct e.student.id
      from Enrollment e
      where e.academicClass.id in :classIds and e.status = :status
      """)
  List<Long> findStudentIdsByClassIdsAndStatus(
      @Param("classIds") Collection<Long> classIds, @Param("status") EnrollmentStatus status);

  long countByAcademicClassIdAndStatus(Long classId, EnrollmentStatus status);

  long countByAcademicClassId(Long classId);

  /**
   * Bulk active-enrollment counts for a page of classes, so rendering a page
   * does not trigger one COUNT query per row.
   */
  @Query(
      """
      select e.academicClass.id, count(e)
      from Enrollment e
      where e.academicClass.id in :classIds and e.status = :status
      group by e.academicClass.id
      """)
  List<Object[]> countActiveByClassIds(
      @Param("classIds") Collection<Long> classIds, @Param("status") EnrollmentStatus status);
}