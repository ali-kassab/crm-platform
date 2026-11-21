package com.crmplatform.identity.application;

import com.crmplatform.identity.domain.PlatformMarker;
import com.crmplatform.identity.domain.PlatformMarkerRepository;
import com.crmplatform.identity.domain.User;
import com.crmplatform.identity.domain.UserRepository;
import com.crmplatform.shared.events.AuditTrail;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Creates the first platform admin from the environment at startup — once.
 *
 * <ul>
 *   <li>There is no HTTP endpoint for this; only whoever controls the server environment can do it.</li>
 *   <li>It runs only if no platform admin exists <em>and</em> the {@code PLATFORM_ADMIN_BOOTSTRAPPED}
 *       marker is absent. The marker (primary key) also makes concurrent starts safe and prevents
 *       re-use if the account is later removed.</li>
 *   <li>It refuses to promote an existing account with the same email (someone could have
 *       registered that address first).</li>
 *   <li>The password must meet the platform-admin rule (at least 14 characters, then the normal
 *       password policy), is stored hashed, and is never logged. There is no default or generated
 *       password in any environment: production must set both variables explicitly.</li>
 * </ul>
 */
@Component
class PlatformAdminBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(PlatformAdminBootstrap.class);

    private final PlatformAdminProperties properties;
    private final UserRepository users;
    private final PlatformMarkerRepository markers;
    private final PasswordEncoder passwordEncoder;
    private final PasswordPolicy passwordPolicy;
    private final AuditTrail audit;
    private final TransactionTemplate tx;
    private final Clock clock;

    PlatformAdminBootstrap(PlatformAdminProperties properties, UserRepository users, PlatformMarkerRepository markers,
            PasswordEncoder passwordEncoder, PasswordPolicy passwordPolicy, AuditTrail audit,
            PlatformTransactionManager transactionManager, Clock clock) {
        this.properties = properties;
        this.users = users;
        this.markers = markers;
        this.passwordEncoder = passwordEncoder;
        this.passwordPolicy = passwordPolicy;
        this.audit = audit;
        this.tx = new TransactionTemplate(transactionManager);
        this.clock = clock;
    }

    @Override
    public void run(ApplicationArguments args) {
        bootstrap();
    }

    /** @return the outcome, for logging and tests */
    Outcome bootstrap() {
        PlatformAdminProperties.Bootstrap config = properties.bootstrap();
        boolean alreadyDone = markers.existsById(PlatformMarker.ADMIN_BOOTSTRAPPED) || users.existsBySuperAdminTrue();
        if (alreadyDone) {
            if (config.configured()) {
                log.warn("Platform admin bootstrap ignored: it has already run. Remove PLATFORM_ADMIN_* "
                        + "variables from the environment.");
            }
            return Outcome.ALREADY_DONE;
        }
        if (!config.configured()) {
            log.info("No platform admin yet. Set PLATFORM_ADMIN_EMAIL and PLATFORM_ADMIN_PASSWORD to create one.");
            return Outcome.NOT_CONFIGURED;
        }
        String email = User.normalizeEmail(config.email());
        if (users.existsByEmail(email)) {
            log.error("Platform admin bootstrap refused: an account with the configured email already exists. "
                    + "Use a different email.");
            return Outcome.EMAIL_TAKEN;
        }
        try {
            // the same rule as every later admin password change (at least 14 characters, then the policy)
            if (config.password().length() < PlatformAdminService.MIN_ADMIN_PASSWORD_LENGTH) {
                throw new IllegalArgumentException("too short");
            }
            passwordPolicy.validate(config.password(), email);
        } catch (RuntimeException e) {
            log.error("Platform admin bootstrap refused: the configured password does not meet the password policy.");
            return Outcome.WEAK_PASSWORD;
        }
        try {
            tx.executeWithoutResult(status -> {
                Instant now = Instant.now(clock);
                User admin = new User(email, passwordEncoder.encode(config.password()), config.firstName(),
                        config.lastName());
                admin.setSuperAdmin(true);
                users.save(admin);
                markers.saveAndFlush(new PlatformMarker(PlatformMarker.ADMIN_BOOTSTRAPPED, now,
                        Map.of("userId", admin.getId().toString())));
                audit.platform("ADMIN_BOOTSTRAPPED", "PLATFORM_ADMIN", admin.getId(), null, "bootstrap",
                        Map.of("email", email));
            });
        } catch (DataIntegrityViolationException e) {
            log.warn("Platform admin bootstrap skipped: another instance completed it concurrently.");
            return Outcome.ALREADY_DONE;
        }
        log.info("Platform admin account created from the environment. Remove PLATFORM_ADMIN_PASSWORD now.");
        return Outcome.CREATED;
    }

    enum Outcome { CREATED, ALREADY_DONE, NOT_CONFIGURED, EMAIL_TAKEN, WEAK_PASSWORD }
}
