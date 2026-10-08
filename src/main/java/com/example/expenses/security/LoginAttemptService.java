package com.example.expenses.security;

import com.example.expenses.model.AuditAction;
import com.example.expenses.model.User;
import com.example.expenses.repository.UserRepository;
import com.example.expenses.service.AuditService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;

/** Counts failed logins in the database and locks an account after too many in a row. */
@Service
public class LoginAttemptService {

    private final UserRepository userRepository;
    private final AuditService auditService;
    private final int maxFailedLogins;
    private final Duration lockoutDuration;

    public LoginAttemptService(UserRepository userRepository, AuditService auditService,
                               @Value("${app.security.max-failed-logins:5}") int maxFailedLogins,
                               @Value("${app.security.lockout-duration:15m}") Duration lockoutDuration) {
        this.userRepository = userRepository;
        this.auditService = auditService;
        this.maxFailedLogins = maxFailedLogins;
        this.lockoutDuration = lockoutDuration;
    }

    @Transactional
    public void loginFailed(String username) {
        // Unknown usernames are ignored: there is no account to lock or log against
        userRepository.findByUsername(User.normalize(username)).ifPresent(user -> {
            if (user.recordFailedLogin(maxFailedLogins, lockoutDuration, Instant.now())) {
                auditService.record(user.getId(), AuditAction.ACCOUNT_LOCKED,
                        "Locked for " + lockoutDuration.toMinutes() + " minutes after "
                                + maxFailedLogins + " wrong passwords in a row");
            } else {
                auditService.record(user.getId(), AuditAction.LOGIN_FAILED,
                        "Wrong password (" + user.getFailedAttempts() + " of " + maxFailedLogins + ")");
            }
        });
    }

    @Transactional
    public void loginSucceeded(String username) {
        userRepository.findByUsername(User.normalize(username)).ifPresent(user -> {
            user.recordSuccessfulLogin();
            auditService.record(user.getId(), AuditAction.LOGIN_SUCCEEDED, null);
        });
    }
}
