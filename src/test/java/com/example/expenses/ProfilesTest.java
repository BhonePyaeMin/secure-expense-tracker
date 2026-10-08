package com.example.expenses;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.core.env.Environment;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/** Which settings each profile turns on. */
class ProfilesTest {

    @Nested
    @SpringBootTest
    class DefaultSettings {

        @Autowired
        private ApplicationContext context;

        @Autowired
        private Environment environment;

        @Test
        void testsRunWithTheTestProfileOnAnInMemoryDatabase() {
            assertThat(environment.getActiveProfiles()).containsExactly("test");
            assertThat(environment.getProperty("spring.datasource.url")).isEmpty();
        }

        @Test
        void theH2ConsoleIsOff() {
            assertThat(environment.getProperty("spring.h2.console.enabled")).isEqualTo("false");
            assertThat(context.containsBean("h2Console")).isFalse();
        }

        @Test
        void noDatabasePasswordIsStoredInTheConfigFiles() {
            assertThat(environment.getProperty("spring.datasource.password")).isEmpty();
        }
    }

    @Nested
    @SpringBootTest
    @ActiveProfiles({"dev", "test"})
    class DevProfile {

        @Autowired
        private ApplicationContext context;

        @Test
        void turnsTheH2ConsoleOn() {
            assertThat(context.containsBean("h2Console")).isTrue();
        }
    }
}
