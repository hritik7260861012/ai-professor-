package com.professor.assistant.repository;

import com.professor.assistant.domain.TeachingAssignment;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TeachingAssignmentRepository extends JpaRepository<TeachingAssignment, Long> {

  Optional<TeachingAssignment> findByTeacherIdAndAcademicClassIdAndSubjectIdAndSemesterId(
      Long teacherId, Long classId, Long subjectId, Long semesterId);

  boolean existsByTeacherIdAndAcademicClassIdAndSubjectIdAndSemesterId(
      Long teacherId, Long classId, Long subjectId, Long semesterId);

  List<TeachingAssignment> findAllByTeacherIdOrderByIdAsc(Long teacherId);

  List<TeachingAssignment> findAllByAcademicClassIdOrderByIdAsc(Long classId);

  /** Classes this teacher is assigned to (any subject). */
  @Query("select distinct ta.academicClass.id from TeachingAssignment ta where ta.teacher.id = :teacherId")
  List<Long> findClassIdsByTeacherId(@Param("teacherId") Long teacherId);

  /** Subjects this teacher is assigned to (in any class). */
  @Query("select distinct ta.subject.id from TeachingAssignment ta where ta.teacher.id = :teacherId")
  List<Long> findSubjectIdsByTeacherId(@Param("teacherId") Long teacherId);

  /** Subjects this teacher teaches in one specific class. */
  @Query(
      """
      select distinct ta.subject.id
      from TeachingAssignment ta
      where ta.teacher.id = :teacherId and ta.academicClass.id = :classId
      """)
  List<Long> findSubjectIdsForClass(
      @Param("teacherId") Long teacherId, @Param("classId") Long classId);
}