package com.mikeshaggy.backend.migration;

import org.flywaydb.core.Flyway;

import java.io.PrintStream;
import java.net.URI;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.Map;
import java.util.Set;

/** Deliberately independent of Spring: no web server, scheduling, Redis or mail. */
public final class MigrationCommand {
    private static final Set<String> OPERATIONS = Set.of("info", "validate", "migrate", "baseline");

    private MigrationCommand() {}

    public static int run(String[] args, Map<String, String> environment, PrintStream out, PrintStream err) {
        final Settings settings;
        try {
            settings = Settings.parse(args, environment);
        } catch (IllegalArgumentException e) {
            // Parser messages are fixed strings and never interpolate configuration values.
            err.println("Migration configuration rejected: " + e.getMessage());
            return 2;
        }
        try {
            checkDatabase(settings);
            Flyway flyway = Flyway.configure()
                    .dataSource(settings.url(), settings.username(), settings.password())
                    .locations("classpath:db/migration")
                    .defaultSchema(settings.schema()).schemas(settings.schema())
                    .createSchemas(false)
                    .baselineOnMigrate(false).baselineVersion("1")
                    .baselineDescription("Accepted production schema (SHG-14)")
                    .cleanDisabled(true).validateOnMigrate(true).validateMigrationNaming(true)
                    // Pre-migration validation accepts pending files but rejects missing/future history.
                    .ignoreMigrationPatterns("*:pending")
                    .failOnMissingLocations(true)
                    // JDBC exceptions can contain credentials or server-provided text. Emit only
                    // our bounded status output, including on failure, instead of library logs.
                    .loggers(new String[0])
                    .load();
            switch (settings.operation()) {
                case "info" -> {
                    for (var migration : flyway.info().all()) {
                        out.printf("%s | %s | %s%n", migration.getVersion(),
                                migration.getDescription(), migration.getState());
                    }
                }
                case "validate" -> flyway.validate();
                case "migrate" -> out.println("Migrations executed: " + flyway.migrate().migrationsExecuted);
                case "baseline" -> flyway.baseline();
                default -> throw new IllegalStateException("Unsupported operation");
            }
            out.println("Migration command completed: " + settings.operation());
            return 0;
        } catch (Exception e) {
            // Do not print exception messages, URLs, usernames, passwords or stack traces.
            err.println("Migration command failed. Check connectivity, schema, permissions and migration history.");
            return 1;
        }
    }

    private static void checkDatabase(Settings settings) throws Exception {
        try (Connection connection = DriverManager.getConnection(settings.url(), settings.username(), settings.password())) {
            if (connection.getMetaData().getDatabaseMajorVersion() != 16) {
                throw new IllegalStateException("PostgreSQL 16 required");
            }
            try (var statement = connection.prepareStatement("SELECT 1 FROM pg_namespace WHERE nspname = ?")) {
                statement.setString(1, settings.schema());
                try (var rows = statement.executeQuery()) {
                    if (!rows.next()) throw new IllegalStateException("Schema must already exist");
                }
            }
            if ("baseline".equals(settings.operation())) {
                // Never mark an empty DB as V1; it must execute V1. A baseline also requires
                // human comparison of the full catalog and a verified backup (see runbook).
                try (var statement = connection.prepareStatement("""
                        SELECT table_name FROM information_schema.tables
                        WHERE table_schema = ? AND table_type = 'BASE TABLE'
                        """)) {
                    statement.setString(1, settings.schema());
                    Set<String> tables = new java.util.HashSet<>();
                    try (var rows = statement.executeQuery()) {
                        while (rows.next()) tables.add(rows.getString(1));
                    }
                    if (!tables.equals(Set.of("users", "wallets", "categories", "transactions", "transfers",
                            "wallet_entries", "fixed_payments", "fixed_payment_occurrences", "category_budgets", "funds"))) {
                        throw new IllegalStateException("Baseline requires accepted tables and no existing history");
                    }
                }
                try (var statement = connection.prepareStatement("""
                        SELECT count(*) FROM information_schema.columns
                        WHERE table_schema = ? AND table_name = 'users'
                          AND column_name IN ('email', 'password_hash')
                          AND data_type = 'character varying' AND character_maximum_length = 100
                        """)) {
                    statement.setString(1, settings.schema());
                    try (var rows = statement.executeQuery()) {
                        rows.next();
                        if (rows.getInt(1) != 2) throw new IllegalStateException("Baseline requires V1 widths");
                    }
                }
            }
        }
    }

    // No generated toString: never render a record containing a password.
    static final class Settings {
        private final String operation, url, username, password, schema;

        private Settings(String operation, String url, String username, String password, String schema) {
            this.operation = operation;
            this.url = url;
            this.username = username;
            this.password = password;
            this.schema = schema;
        }

        static Settings parse(String[] args, Map<String, String> env) {
            if (args.length == 0 || !OPERATIONS.contains(args[0])) {
                throw new IllegalArgumentException("Use db info|validate|migrate|baseline.");
            }
            if ("baseline".equals(args[0])) {
                if (args.length != 3 || !"--baseline-version=1".equals(args[1])
                        || !"--confirm-baseline".equals(args[2])) {
                    throw new IllegalArgumentException("Baseline requires --baseline-version=1 --confirm-baseline.");
                }
            } else if (args.length != 1) {
                throw new IllegalArgumentException("This operation accepts no extra arguments.");
            }
            String mode = required(env, "MIGRATION_ENV");
            if (!Set.of("local", "prod").contains(mode)) {
                throw new IllegalArgumentException("MIGRATION_ENV must be local or prod.");
            }
            String url = required(env, "DB_URL");
            String username = required(env, "DB_USERNAME");
            String password = required(env, "DB_PASSWORD");
            String schema = required(env, "DB_SCHEMA");
            if (!schema.matches("[a-z_][a-z0-9_]{0,62}") || schema.startsWith("pg_")
                    || "information_schema".equals(schema)) {
                throw new IllegalArgumentException("DB_SCHEMA must be a simple application schema identifier.");
            }
            URI uri;
            try {
                if (!url.startsWith("jdbc:postgresql://")) throw new IllegalArgumentException();
                uri = URI.create(url.substring(5));
                if (uri.getHost() == null || uri.getUserInfo() != null || uri.getFragment() != null
                        || uri.getPort() < 1 || uri.getPort() > 65535
                        || uri.getPath() == null || !uri.getPath().matches("/[a-zA-Z_][a-zA-Z0-9_-]*")
                        || (uri.getRawQuery() != null && !uri.getRawQuery().matches(
                                "sslmode=(disable|allow|prefer|require|verify-ca|verify-full)"))) {
                    throw new IllegalArgumentException();
                }
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("DB_URL must be an explicit PostgreSQL host:port/database URL; only sslmode is allowed as a query parameter.");
            }
            if ("prod".equals(mode)) {
                String expectedHost = required(env, "MIGRATION_EXPECTED_HOST");
                if (!expectedHost.equals(uri.getHost()) || !"/savix".equals(uri.getPath()) || !"public".equals(schema)) {
                    throw new IllegalArgumentException("Production requires the confirmed host, database savix and schema public.");
                }
            } else if (!Set.of("localhost", "127.0.0.1", "[::1]").contains(uri.getHost())) {
                throw new IllegalArgumentException("Local mode requires a loopback host; use prod mode with a confirmed host for container networking.");
            }
            // Bounded connection attempts, without permitting arbitrary driver properties.
            url += (uri.getRawQuery() == null ? "?" : "&") + "connectTimeout=10";
            return new Settings(args[0], url, username, password, schema);
        }

        private static String required(Map<String, String> env, String name) {
            String value = env.get(name);
            if (value == null || value.isBlank() || value.contains("${")) {
                throw new IllegalArgumentException(name + " must be explicitly configured.");
            }
            return value;
        }

        String operation() { return operation; }
        String url() { return url; }
        String username() { return username; }
        String password() { return password; }
        String schema() { return schema; }
    }
}
