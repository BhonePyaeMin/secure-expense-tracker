package com.example.expenses;

import com.example.expenses.model.User;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class UserLockoutTest {

    private static final Duration FIFTEEN_MINUTES = Duration.ofMinutes(15);
    private static final Instant NOW = Instant.parse("2026-10-08T12:00:00Z");

    @Test
    void fourFailuresDoNotLock() {
        User user = new User("alice", "hash");
        for (int i = 0; i < 4; i++) {
            assertThat(user.recordFailedLogin(5, FIFTEEN_MINUTES, NOW)).isFalse();
        }
        assertThat(user.isLocked(NOW)).isFalse();
        assertThat(user.getFailedAttempts()).isEqualTo(4);
    }

    @Test
    void fifthFailureLocksForTheConfiguredDuration() {
        User user = new User("alice", "hash");
        for (int i = 0; i < 4; i++) {
            user.recordFailedLogin(5, FIFTEEN_MINUTES, NOW);
        }

        assertThat(user.recordFailedLogin(5, FIFTEEN_MINUTES, NOW)).isTrue();
        assertThat(user.isLocked(NOW.plus(Duration.ofMinutes(14)))).isTrue();
        assertThat(user.isLocked(NOW.plus(FIFTEEN_MINUTES))).isFalse();
    }

    @Test
    void successfulLoginResetsTheCounter() {
        User user = new User("alice", "hash");
        user.recordFailedLogin(5, FIFTEEN_MINUTES, NOW);
        user.recordFailedLogin(5, FIFTEEN_MINUTES, NOW);

        user.recordSuccessfulLogin();

        assertThat(user.getFailedAttempts()).isZero();
        assertThat(user.getLockedUntil()).isNull();
    }

    @Test
    void usernamesAreStoredLowercase() {
        assertThat(new User("  Alice ", "hash").getUsername()).isEqualTo("alice");
    }
}
