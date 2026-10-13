package com.professor.assistant.repository;

import com.professor.assistant.domain.ClassSubject;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ClassSubjectRepository extends JpaRepository<ClassSubject, Long> {

  Optional<ClassSubject> findByAcademicClassIdAndSubjectId(Long classId, Long subjectId);

  boolean existsByAcademicClassIdAndSubjectId(Long classId, Long subjectId);

  List<ClassSubject> findAllByAcademicClassIdOrderBySubjectCodeAsc(Long classId);

  List<ClassSubject> findAllBySubjectId(Long subjectId);

  long countByAcademicClassId(Long classId);

  void deleteAllByAcademicClassId(Long classId);

  List<ClassSubject> findAllByAcademicClassIdIn(Collection<Long> classIds);
}