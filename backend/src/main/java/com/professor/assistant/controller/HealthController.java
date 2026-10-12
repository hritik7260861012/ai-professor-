package com.professor.assistant.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.sql.DataSource;

/**
 * Liveness / dependency health for the API. Used by the frontend status
 * indicator and by future container orchestrators.
 */
@RestController
@RequestMapping("/api/v1/health")
@Tag(name = "Health", description = "Service liveness and dependency checks")
public class HealthController {

  private static final Logger log = LoggerFactory.getLogger(HealthController.class);

  private final DataSource dataSource;
  private final String version;

  public HealthController(DataSource dataSource, @Value("${app.api.version}") String version) {
    this.dataSource = dataSource;
    this.version = version;
  }

  @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
  @Operation(summary = "Service health", description = "Returns UP only when the API and its database are reachable.")
  public ResponseEntity<HealthResponse> health() {
    String database = checkDatabase();
    boolean up = "UP".equals(database);
    HealthResponse body = new HealthResponse(up ? "UP" : "DOWN", version, database, Instant.now().toString());
    log.debug("Health check: {}", body);
    return ResponseEntity.status(up ? HttpStatus.OK : HttpStatus.SERVICE_UNAVAILABLE).body(body);
  }

  private String checkDatabase() {
    try (Connection connection = dataSource.getConnection();
         Statement statement = connection.createStatement();
         ResultSet resultSet = statement.executeQuery("SELECT 1")) {
      resultSet.next();
      return "UP";
    } catch (Exception e) {
      log.warn("Database health check failed: {}", e.toString());
      return "DOWN";
    }
  }

  public record HealthResponse(String status, String version, String database, String timestamp) {}
}
