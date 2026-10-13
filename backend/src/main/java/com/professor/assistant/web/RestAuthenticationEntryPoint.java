package com.professor.assistant.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.professor.assistant.exception.ApiError;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;

/**
 * Returns 401 with the standard {@link ApiError} envelope
 * instead of Spring Security's default login redirect.
 *
 * <p>The {@link ObjectMapper} is the Spring-managed one, so the
 * {@code ApiError} envelope serializes identically to the one produced
 * by {@code GlobalExceptionHandler} (a bare {@code new ObjectMapper()}
 * lacks the JavaTimeModule and would fail on the {@code Instant}
 * timestamp, turning a 401 into a 500).
 */
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {

  private final ObjectMapper objectMapper;

  public RestAuthenticationEntryPoint(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  @Override
  public void commence(
      HttpServletRequest request,
      HttpServletResponse response,
      AuthenticationException authException)
      throws IOException, ServletException {
    response.setStatus(HttpStatus.UNAUTHORIZED.value());
    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
    response.setCharacterEncoding("UTF-8");
    objectMapper.writeValue(
        response.getWriter(),
        ApiError.of(HttpStatus.UNAUTHORIZED, "Authentication required", request.getRequestURI()));
  }
}