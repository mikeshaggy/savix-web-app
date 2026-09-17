package com.mikeshaggy.backend.migration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration;
import com.mikeshaggy.backend.BackendApplication;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MigrationCommandTest {
    private Map<String, String> environment() {
        return new HashMap<>(Map.of("MIGRATION_ENV", "local", "DB_URL", "jdbc:postgresql://127.0.0.1:5432/savix",
                "DB_USERNAME", "test", "DB_PASSWORD", "secret-canary", "DB_SCHEMA", "public"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"info", "validate", "migrate"})
    void acceptsOnlySupportedRoutineOperations(String operation) {
        assertThat(MigrationCommand.Settings.parse(new String[]{operation}, environment()).operation()).isEqualTo(operation);
    }

    @ParameterizedTest
    @ValueSource(strings = {"clean", "repair", "undo", "", "MIGRATE"})
    void rejectsUnsupportedOperations(String operation) {
        assertThatThrownBy(() -> MigrationCommand.Settings.parse(new String[]{operation}, environment()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"MIGRATION_ENV", "DB_URL", "DB_USERNAME", "DB_PASSWORD", "DB_SCHEMA"})
    void requiresExplicitConfiguration(String key) {
        var env = environment();
        env.remove(key);
        assertThatThrownBy(() -> MigrationCommand.Settings.parse(new String[]{"migrate"}, env))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining(key);
    }

    @ParameterizedTest
    @ValueSource(strings = {"jdbc:h2:mem:test", "jdbc:postgresql:savix", "jdbc:postgresql://localhost/savix",
            "jdbc:postgresql://user:secret@localhost:5432/savix", "jdbc:postgresql://localhost:5432/savix?password=secret",
            "jdbc:postgresql://localhost:5432/savix?currentSchema=other", "${DB_URL}",
            "jdbc:postgresql://remote:5432/savix", "jdbc:postgresql://localhost:5432/savix#fragment"})
    void rejectsAmbiguousOrUnsafeUrls(String url) {
        var env = environment();
        env.put("DB_URL", url);
        assertThatThrownBy(() -> MigrationCommand.Settings.parse(new String[]{"migrate"}, env))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void productionRequiresConfirmedTarget() {
        var env = environment();
        env.put("MIGRATION_ENV", "prod");
        assertThatThrownBy(() -> MigrationCommand.Settings.parse(new String[]{"info"}, env)).isInstanceOf(IllegalArgumentException.class);
        env.put("MIGRATION_EXPECTED_HOST", "different");
        assertThatThrownBy(() -> MigrationCommand.Settings.parse(new String[]{"info"}, env)).isInstanceOf(IllegalArgumentException.class);
        env.put("MIGRATION_EXPECTED_HOST", "127.0.0.1");
        assertThat(MigrationCommand.Settings.parse(new String[]{"info"}, env).schema()).isEqualTo("public");
        env.put("DB_SCHEMA", "other");
        assertThatThrownBy(() -> MigrationCommand.Settings.parse(new String[]{"info"}, env)).isInstanceOf(IllegalArgumentException.class);
        env.put("DB_SCHEMA", "public");
        env.put("DB_URL", "jdbc:postgresql://127.0.0.1:5432/other");
        assertThatThrownBy(() -> MigrationCommand.Settings.parse(new String[]{"info"}, env)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void baselineRequiresVersionOneAndAcknowledgement() {
        for (String[] args : new String[][]{{"baseline"}, {"baseline", "--baseline-version=0", "--confirm-baseline"},
                {"baseline", "--baseline-version=1"}, {"migrate", "--baselineOnMigrate=true"}}) {
            assertThatThrownBy(() -> MigrationCommand.Settings.parse(args, environment())).isInstanceOf(IllegalArgumentException.class);
        }
        assertThat(MigrationCommand.Settings.parse(new String[]{"baseline", "--baseline-version=1", "--confirm-baseline"},
                environment()).operation()).isEqualTo("baseline");
    }

    @Test
    void supportsExplicitLocalSchemaAndSslMode() {
        var env = environment();
        env.put("DB_SCHEMA", "isolated_test");
        env.put("DB_URL", "jdbc:postgresql://localhost:5432/savix?sslmode=require");
        assertThat(MigrationCommand.Settings.parse(new String[]{"info"}, env).schema()).isEqualTo("isolated_test");
    }

    @Test
    void rejectsMalformedConfigNonZeroWithoutPrintingSecrets() {
        var env = environment();
        env.put("DB_URL", "jdbc:postgresql://secret-canary@localhost:5432/savix");
        var output = new ByteArrayOutputStream();
        var stream = new PrintStream(output);
        assertThat(MigrationCommand.run(new String[]{"migrate"}, env, stream, stream)).isEqualTo(2);
        assertThat(output.toString()).contains("configuration rejected").doesNotContain("secret-canary");
    }

    @Test
    void normalApplicationCannotEnableFlywayAutoConfiguration() {
        assertThat(BackendApplication.class.getAnnotation(SpringBootApplication.class).exclude())
                .contains(FlywayAutoConfiguration.class);
    }
}
