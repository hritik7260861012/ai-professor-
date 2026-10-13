package com.professor.assistant;

import com.professor.assistant.service.AuthService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Creates the initial administrator account when
 * {@code app.bootstrap.admin.email} and
 * {@code app.bootstrap.admin.password} are provided.
 *
 * <p>This is the only way a fresh deployment bootstraps
 * its first admin — credentials come from the
 * environment, never from code.
 */
@Component
public class BootstrapAdminRunner implements ApplicationRunner {

  private static final Logger log = LoggerFactory.getLogger(BootstrapAdminRunner.class);

  private final AuthService authService;
  private final String email;
  private final String password;

  public BootstrapAdminRunner(
      AuthService authService,
      @Value("${app.bootstrap.admin.email:}") String email,
      @Value("${app.bootstrap.admin.password:}") String password) {
    this.authService = authService;
    this.email = email;
    this.password = password;
  }

  @Override
  public void run(ApplicationArguments args) {
    if (!StringUtils.hasText(email) || !StringUtils.hasText(password)) {
      log.info(
          "No bootstrap administrator configured (set APP_BOOTSTRAP_ADMIN_EMAIL "
              + "and APP_BOOTSTRAP_ADMIN_PASSWORD to create one on first startup)");
      return;
    }
    if (authService.findByEmail(email).isPresent()) {
      log.info("Bootstrap administrator already exists: {}", email);
      return;
    }
    authService.createAdmin("Administrator", email, password);
    log.warn(
        "Bootstrap administrator created: {}. Change the password after first sign-in.",
        email);
  }
}
