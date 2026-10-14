package com.professor.assistant.domain;

/** Who a generated artifact is written for. */
public enum ContentAudience {
  /** Can be released to enrolled students once approved. */
  STUDENT,
  /** Never released to students under any circumstances. */
  TEACHER
}