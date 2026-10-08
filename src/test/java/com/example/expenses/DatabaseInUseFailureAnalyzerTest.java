package com.example.expenses;

import com.example.expenses.config.DatabaseInUseFailureAnalyzer;
import org.junit.jupiter.api.Test;
import org.springframework.boot.diagnostics.FailureAnalysis;

import static org.assertj.core.api.Assertions.assertThat;

class DatabaseInUseFailureAnalyzerTest {

    private final DatabaseInUseFailureAnalyzer analyzer = new DatabaseInUseFailureAnalyzer();

    @Test
    void explainsTheErrorHibernateThrowsWhenTheDatabaseFileIsLocked() {
        // The real shape: Hibernate logs H2's lock error and throws this one without it as the cause
        Exception failure = new RuntimeException("Error creating bean with name 'entityManagerFactory'",
                new IllegalStateException("Unable to create requested service [JdbcEnvironment]",
                        new RuntimeException("Unable to determine Dialect without JDBC metadata (please set "
                                + "'jakarta.persistence.jdbc.url' for common cases)")));

        FailureAnalysis analysis = analyzer.analyze(failure);

        assertThat(analysis).isNotNull();
        assertThat(analysis.getDescription()).contains("another copy of this app");
        assertThat(analysis.getAction()).contains("Ctrl+C");
    }

    @Test
    void alsoRecognisesH2sOwnMessage() {
        Exception failure = new RuntimeException("startup failed",
                new Exception("Database may be already in use: \"data/expenses.mv.db\""));

        assertThat(analyzer.analyze(failure)).isNotNull();
    }

    @Test
    void leavesOtherFailuresAlone() {
        assertThat(analyzer.analyze(new RuntimeException("Port 8080 was already in use"))).isNull();
    }
}
