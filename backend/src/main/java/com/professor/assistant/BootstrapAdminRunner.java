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
 * Creates the initial administrator account for local development.
 *
 * <p>This is the only way a fresh deployment bootstraps its first admin.
 * There is deliberately no public admin-registration endpoint: public
 * sign-up is limited to STUDENT accounts, so a deployment that forgets to
 * run this bootstrap has no administrator rather than an open one.
 *
 * <p><b>Disabled by default.</b> All three properties must be supplied
 * explicitly:
 *
 * <ul>
 *   <li>{@code app.bootstrap.admin.enabled} — the master switch, default
 *       {@code false}. Setting the credentials alone does nothing.</li>
 *   <li>{@code app.bootstrap.admin.email}</li>
 *   <li>{@code app.bootstrap.admin.password}</li>
 * </ul>
 *
 * <p>Because the switch defaults to off, leaving the credential variables in
 * an environment does not by itself create an account; someone must also
 * turn the mechanism on.
 *
 * <p>Creation is idempotent and never destructive:
 *
 * <ul>
 *   <li>An existing account with the same email is left completely alone —
 *       the password is not overwritten.</li>
 *   <li>If any administrator already exists, nothing is created, so a
 *       restart cannot mint a second admin.</li>
 * </ul>
 *
 * <p>Credentials are read from the environment, are hashed with the same
 * BCrypt encoder used everywhere else, and are never logged. Only the
 * email address is written to the log.
 */
@Component
public class BootstrapAdminRunner implements ApplicationRunner {

  private static final Logger log = LoggerFactory.getLogger(BootstrapAdminRunner.class);

  private final AuthService authService;
  private final boolean enabled;
  private final String email;
  private final String password;

  public BootstrapAdminRunner(
      AuthService authService,
      @Value("${app.bootstrap.admin.enabled:false}") boolean enabled,
      @Value("${app.bootstrap.admin.email:}") String email,
      @Value("${app.bootstrap.admin.password:}") String password) {
    this.authService = authService;
    this.enabled = enabled;
    this.email = email;
    this.password = password;
  }

  @Override
  public void run(ApplicationArguments args) {
    if (!enabled) {
      // The common case, including production. Logged at debug so a normal
      // startup is not cluttered with an irrelevant notice.
      log.debug("Admin bootstrap is disabled (app.bootstrap.admin.enabled=false).");
      return;
    }

    if (!StringUtils.hasText(email) || !StringUtils.hasText(password)) {
      // Enabled but incomplete is a misconfiguration worth surfacing, not
      // silently ignoring. The password value is never included.
      log.warn(
          "Admin bootstrap is enabled but APP_BOOTSTRAP_ADMIN_EMAIL and/or "
              + "APP_BOOTSTRAP_ADMIN_PASSWORD is missing. No account was created.");
      return;
    }

    if (authService.adminExists()) {
      log.info(
          "Admin bootstrap skipped: this deployment already has an administrator. "
              + "Nothing was changed.");
      return;
    }

    if (authService.findByEmail(email).isPresent()) {
      // An account with this address already exists (as another role, or as
      // an admin created earlier). Leave its password untouched rather than
      // resetting it to the bootstrap value.
      log.info("Admin bootstrap skipped: an account already exists for {}.", email);
      return;
    }

    authService.createAdmin("Administrator", email, password);
    // Email only: the password must never reach the log.
    log.warn("Admin bootstrap created an administrator for {}.", email);
  }
}