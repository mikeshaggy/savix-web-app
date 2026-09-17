package com.mikeshaggy.backend.migration;

import com.github.dockerjava.api.model.ExposedPort;
import com.github.dockerjava.api.model.PortBinding;
import com.github.dockerjava.api.model.Ports;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mikeshaggy.backend.user.domain.User;
import com.mikeshaggy.backend.wallet.domain.Wallet;
import jakarta.persistence.EntityManager;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.flywaydb.core.api.callback.Callback;
import org.flywaydb.core.api.callback.Context;
import org.flywaydb.core.api.callback.Event;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.boot.orm.jpa.hibernate.SpringImplicitNamingStrategy;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Real PostgreSQL only; Failsafe runs this class exclusively with -Ppostgres-it. */
@Testcontainers
class PostgresMigrationIT {
    // Exact minor recorded by SHG-14, with a deliberate Debian variant; never a floating 16/latest tag.
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(DockerImageName.parse("postgres:16.11-bookworm"))
            .withDatabaseName("shg15").withUsername("shg15").withPassword("synthetic-test-password")
            .withReuse(false)
            // Host override selects the client address, not Docker's published interface.
            // The create modifier sets loopback while leaving the host port Docker-assigned.
            .withCreateContainerCmdModifier(cmd -> cmd.getHostConfig().withPortBindings(
                    new PortBinding(Ports.Binding.bindIp("127.0.0.1"), ExposedPort.tcp(5432))));

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000015");
    private String schema;

    @BeforeEach
    void isolatedSchema() throws Exception {
        schema = "shg15_" + UUID.randomUUID().toString().replace("-", "");
        try (var connection = DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
             var statement = connection.createStatement()) {
            assertThat(connection.getMetaData().getDatabaseProductVersion()).startsWith("16.11");
            statement.execute("CREATE SCHEMA " + schema);
        }
        // Schema lifetime is bounded by the JUnit-managed container, even on test failure.
    }

    @Test
    void postgresPublishesRandomPortOnlyOnLoopbackAndJdbcConnects() throws Exception {
        var inspection = DockerClientFactory.instance().client()
                .inspectContainerCmd(POSTGRES.getContainerId()).exec();
        var bindings = inspection.getNetworkSettings().getPorts().getBindings().get(ExposedPort.tcp(5432));
        assertThat(bindings).as("Running Docker container must publish 5432/tcp").isNotEmpty();
        for (var binding : bindings) {
            assertThat(binding.getHostIp()).as("Actual Docker HostIp").isEqualTo("127.0.0.1");
            assertThat(Integer.parseInt(binding.getHostPortSpec()))
                    .isBetween(1, 65535).isEqualTo(POSTGRES.getMappedPort(5432));
        }
        // Docker must have been asked to allocate the host port, not given a fixed one.
        var requested = inspection.getHostConfig().getPortBindings().getBindings().get(ExposedPort.tcp(5432));
        assertThat(requested).hasSize(1);
        assertThat(requested[0].getHostPortSpec()).isIn(null, "", "0");
        try (var connection = DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
             var statement = connection.createStatement();
             var rows = statement.executeQuery("SELECT 1")) {
            assertThat(connection.getMetaData().getURL()).contains(":" + POSTGRES.getMappedPort(5432) + "/");
            assertThat(rows.next()).isTrue();
            assertThat(rows.getInt(1)).isEqualTo(1);
        }
        System.out.printf("Verified Docker 5432/tcp -> 127.0.0.1:%d (dynamic host port); JDBC connected%n",
                POSTGRES.getMappedPort(5432));
    }

    @Test
    void freshDatabaseAppliesBothMigrationsAndRepeatIsNoOp() throws Exception {
        assertThat(query("SELECT tablename FROM pg_tables WHERE schemaname = current_schema()")).isEmpty();
        assertThat(command("migrate")).contains("Migrations executed: 2");
        assertHistory("1|SQL|true", "2|SQL|true");
        assertThat(query("SELECT script FROM flyway_schema_history ORDER BY installed_rank"))
                .containsExactly("V1__baseline.sql", "V2__widen_user_columns.sql");
        assertThat(query("SELECT version FROM flyway_schema_history WHERE checksum IS NULL")).isEmpty();
        assertCatalog(255);
        var history = history();
        var structure = structure();
        assertThat(command("migrate")).contains("Migrations executed: 0");
        command("validate");
        assertThat(history()).isEqualTo(history);
        assertThat(structure()).isEqualTo(structure);
    }

    @Test
    void acceptedExistingDatabaseRequiresExplicitBaselineAndOnlyWidensTwoColumns() throws Exception {
        // Execute the accepted DDL as an untracked existing schema, not as a Flyway migration.
        // The independent SHG-14 catalog below prevents V1 itself becoming the expected-schema oracle.
        script("db/migration/V1__baseline.sql");
        assertCatalog(100);
        script("postgres-it/synthetic-data.sql");
        var data = data();
        var catalog = catalog();
        var structure = structure();
        assertThat(commandResult("migrate").exitCode()).isEqualTo(1);
        assertThat(data()).isEqualTo(data);
        assertThat(query("SELECT to_regclass('flyway_schema_history')::text")).containsExactly((String) null);
        command("baseline", "--baseline-version=1", "--confirm-baseline");
        assertHistory("1|BASELINE|true");
        assertThat(query("SELECT description FROM flyway_schema_history"))
                .containsExactly("Accepted production schema (SHG-14)");
        assertThat(query("SELECT checksum::text FROM flyway_schema_history")).containsExactly((String) null);
        assertThat(catalog()).containsExactlyInAnyOrderElementsOf(catalog);
        assertThat(data()).isEqualTo(data);
        command("validate");
        assertThat(command("migrate")).contains("Migrations executed: 1");
        assertHistory("1|BASELINE|true", "2|SQL|true");
        assertThat(query("SELECT script FROM flyway_schema_history WHERE type = 'SQL'"))
                .containsExactly("V2__widen_user_columns.sql");
        assertCatalog(255);
        assertThat(data()).isEqualTo(data);
        // Includes physical column order, sequence settings and ownership, beyond the accepted catalog.
        assertThat(structure()).isEqualTo(structure);
        assertThat(command("migrate")).contains("Migrations executed: 0");
    }

    @Test
    void failingMigrationRollsBackDdlAndDataWithoutRepairOrClean() throws Exception {
        command("migrate");
        script("postgres-it/synthetic-data.sql");
        var data = data();
        var catalog = catalog();
        var history = history();
        var events = new ArrayList<Event>();
        var flyway = flyway(events, "classpath:db/migration", "classpath:postgres-it/failure");
        for (int attempt = 0; attempt < 2; attempt++) {
            assertThatThrownBy(flyway::migrate).isInstanceOf(FlywayException.class)
                    .hasMessageContaining("V3__controlled_failure.sql")
                    .hasStackTraceContaining("22012"); // PostgreSQL division_by_zero, not an unrelated setup error.
            assertThat(history()).isEqualTo(history);
            assertThat(query("SELECT version FROM flyway_schema_history WHERE version = '3' OR NOT success")).isEmpty();
            assertThat(catalog()).containsExactlyInAnyOrderElementsOf(catalog);
            assertThat(data()).isEqualTo(data);
        }
        assertThat(events).contains(Event.BEFORE_MIGRATE);
        assertThat(events).noneMatch(event -> event.getId().toLowerCase().contains("repair")
                || event.getId().toLowerCase().contains("clean"));
        assertThat(flyway.getConfiguration().isCleanDisabled()).isTrue();
        // Test teardown destroys the disposable container; no Flyway repair/clean is used.
    }

    @Test
    void changedRecordedChecksumFailsValidationAndMigrate(@TempDir Path migrations) throws Exception {
        for (String name : List.of("V1__baseline.sql", "V2__widen_user_columns.sql")) {
            try (var input = new ClassPathResource("db/migration/" + name).getInputStream()) {
                Files.copy(input, migrations.resolve(name), StandardCopyOption.REPLACE_EXISTING);
            }
        }
        String location = "filesystem:" + migrations;
        var events = new ArrayList<Event>();
        assertThat(flyway(events, location).migrate().migrationsExecuted).isEqualTo(2);
        script("postgres-it/synthetic-data.sql");
        var history = history();
        var data = data();
        Files.writeString(migrations.resolve("V2__widen_user_columns.sql"),
                "ALTER TABLE users ADD COLUMN checksum_tampering text;\n");
        var changed = flyway(events, location);
        assertThatThrownBy(changed::validate).isInstanceOf(FlywayException.class)
                .hasMessageContaining("Migration checksum mismatch for migration version 2");
        assertThatThrownBy(changed::migrate).isInstanceOf(FlywayException.class)
                .hasMessageContaining("Migration checksum mismatch for migration version 2");
        assertThat(history()).isEqualTo(history);
        assertThat(data()).isEqualTo(data);
        assertCatalog(255);
        assertThat(events).noneMatch(event -> event.getId().toLowerCase().contains("repair")
                || event.getId().toLowerCase().contains("clean"));
    }

    @Test
    void productionMappingsReadAllEntitiesAndWriteWithoutHibernateDdl() throws Exception {
        command("migrate");
        script("postgres-it/synthetic-data.sql");
        var catalog = catalog();
        var structure = structure();
        var history = history();
        String role = schema + "_app";
        try (var connection = connection(); var statement = connection.createStatement()) {
            statement.execute("CREATE ROLE " + role + " LOGIN PASSWORD 'synthetic-app-password'");
            statement.execute("GRANT USAGE ON SCHEMA " + schema + " TO " + role);
            statement.execute("GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA " + schema + " TO " + role);
            statement.execute("GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA " + schema + " TO " + role);
        }
        var datasource = new DriverManagerDataSource(POSTGRES.getJdbcUrl(), role, "synthetic-app-password");
        try (var connection = datasource.getConnection(); var statement = connection.createStatement()) {
            assertThatThrownBy(() -> statement.execute("CREATE TABLE " + schema + ".forbidden_ddl (id int)"))
                    .isInstanceOfSatisfying(SQLException.class, error -> assertThat(error.getSQLState()).isEqualTo("42501"));
        }
        var yaml = new YamlPropertiesFactoryBean();
        yaml.setResources(new ClassPathResource("application-prod.yml"));
        var production = yaml.getObject();
        assertThat(production.getProperty("spring.jpa.hibernate.ddl-auto")).isEqualTo("none");
        var factory = new LocalContainerEntityManagerFactoryBean();
        factory.setDataSource(datasource);
        factory.setPackagesToScan("com.mikeshaggy.backend");
        factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
        factory.setJpaPropertyMap(Map.of(
                "hibernate.hbm2ddl.auto", production.getProperty("spring.jpa.hibernate.ddl-auto"),
                "hibernate.default_schema", schema,
                "hibernate.physical_naming_strategy", "org.hibernate.boot.model.naming.CamelCaseToUnderscoresNamingStrategy",
                "hibernate.implicit_naming_strategy", SpringImplicitNamingStrategy.class.getName()));
        try {
            factory.afterPropertiesSet();
            try (EntityManager entityManager = factory.getObject().createEntityManager()) {
                var entities = entityManager.getMetamodel().getEntities();
                assertThat(entities).hasSize(10);
                for (var entity : entities) {
                    // Hydrate every mapped column and association against populated tables, not SELECT count(*).
                    assertThat(entityManager.createQuery("from " + entity.getName(), entity.getJavaType()).getResultList())
                            .as(entity.getName()).isNotEmpty();
                }
                var user = entityManager.find(User.class, USER_ID);
                assertThat(user.getEmail()).isEqualTo("shg15@example.invalid");
                assertThat(user.getCreatedAt()).isNotNull();
                var wallet = entityManager.find(Wallet.class, 1);
                assertThat(wallet.getUser().getId()).isEqualTo(USER_ID);
                assertThat(wallet.getBalance()).isEqualByComparingTo("123.45");
                entityManager.getTransaction().begin();
                String email = "a".repeat(239) + "@example.invalid"; // Exactly 255 characters.
                user.setEmail(email);
                user.setPasswordHash("p".repeat(255));
                wallet.setName("Updated via production mapping");
                var created = User.builder().email("new@example.invalid").username("new-user")
                        .passwordHash("synthetic-new-hash").build();
                entityManager.persist(created);
                entityManager.getTransaction().commit();
                entityManager.clear();
                var reloaded = entityManager.find(User.class, USER_ID);
                assertThat(reloaded.getEmail()).hasSize(255).isEqualTo(email);
                assertThat(reloaded.getPasswordHash()).isEqualTo("p".repeat(255));
                assertThat(entityManager.find(Wallet.class, 1).getVersion()).isEqualTo(1);
                assertThat(entityManager.find(User.class, created.getId()).getCreatedAt()).isNotNull();
            }
        } finally {
            factory.destroy();
        }
        assertThat(catalog()).containsExactlyInAnyOrderElementsOf(catalog);
        assertThat(structure()).isEqualTo(structure);
        assertThat(history()).isEqualTo(history);
        command("validate");
    }

    private Flyway flyway(List<Event> events, String... locations) {
        return Flyway.configure().dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .defaultSchema(schema).schemas(schema).createSchemas(false)
                .locations(locations)
                .baselineOnMigrate(false).cleanDisabled(true).validateOnMigrate(true)
                .validateMigrationNaming(true).ignoreMigrationPatterns("*:pending").failOnMissingLocations(true)
                .callbacks(new Callback() {
                    public boolean supports(Event event, Context context) { return true; }
                    public boolean canHandleInTransaction(Event event, Context context) { return true; }
                    public void handle(Event event, Context context) { events.add(event); }
                    public String getCallbackName() { return "observe-only"; }
                }).load();
    }

    private record CommandResult(int exitCode, String output) {}

    private CommandResult commandResult(String... args) {
        var output = new ByteArrayOutputStream();
        // Construct exclusively from the disposable container; never consult application environment/DB_URL.
        // Testcontainers appends loggerLevel=OFF; the production command intentionally forbids driver options.
        String commandUrl = "jdbc:postgresql://" + POSTGRES.getHost() + ":" + POSTGRES.getMappedPort(5432)
                + "/" + POSTGRES.getDatabaseName();
        var environment = Map.of("MIGRATION_ENV", "local", "DB_URL", commandUrl,
                "DB_USERNAME", POSTGRES.getUsername(), "DB_PASSWORD", POSTGRES.getPassword(), "DB_SCHEMA", schema);
        try (var stream = new PrintStream(output, true, StandardCharsets.UTF_8)) {
            int result = MigrationCommand.run(args, environment, stream, stream);
            return new CommandResult(result, output.toString(StandardCharsets.UTF_8));
        }
    }

    private String command(String... args) {
        var result = commandResult(args);
        assertThat(result.exitCode()).as(result.output()).isZero();
        return result.output();
    }

    private Connection connection() throws SQLException {
        var connection = DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        connection.setSchema(schema);
        return connection;
    }

    private void script(String resource) throws SQLException {
        try (var connection = connection()) {
            ScriptUtils.executeSqlScript(connection, new ClassPathResource(resource));
        }
    }

    private List<String> query(String sql) throws SQLException {
        try (var connection = connection(); var statement = connection.createStatement(); var rows = statement.executeQuery(sql)) {
            var result = new ArrayList<String>();
            while (rows.next()) result.add(rows.getString(1));
            return result;
        }
    }

    private void assertHistory(String... expected) throws SQLException {
        assertThat(query("SELECT version || '|' || type || '|' || success FROM flyway_schema_history ORDER BY installed_rank"))
                .containsExactly(expected);
    }

    private List<String> history() throws SQLException {
        return query("SELECT to_jsonb(h)::text FROM flyway_schema_history h ORDER BY installed_rank");
    }

    private Map<String, List<String>> data() throws SQLException {
        var data = new HashMap<String, List<String>>();
        for (String table : query("SELECT tablename FROM pg_tables WHERE schemaname = current_schema() AND tablename <> 'flyway_schema_history'")) {
            data.put(table, query("SELECT to_jsonb(t)::text FROM " + table + " t ORDER BY id"));
        }
        return data;
    }

    private List<JsonNode> catalog() throws Exception {
        // Reuse the SHG-14 read-only inspection query and metadata, copied into test resources by Maven.
        String inspection = new ClassPathResource("postgres-it/accepted/inspect-catalog.sql")
                .getContentAsString(StandardCharsets.UTF_8);
        String sql = inspection.substring(inspection.indexOf("WITH entries AS"), inspection.indexOf("COMMIT;"))
                .replace(":'schema'", "'" + schema + "'");
        var result = new ArrayList<JsonNode>();
        for (JsonNode row : JSON.readTree(query(sql).getFirst())) {
            if (!row.path("table").asText().equals("flyway_schema_history")) result.add(normalize(row));
        }
        return result;
    }

    private JsonNode normalize(JsonNode original) {
        ObjectNode row = original.deepCopy();
        if (row.has("schema")) row.put("schema", "public");
        if (row.has("definition")) {
            String definition = row.get("definition").asText().replace("ON " + schema + ".", "ON public.");
            // Same two equivalent PostgreSQL renderings explicitly accepted by SHG-14's comparator.
            if (row.path("name").asText().equals("funds_status_check")) {
                definition = definition.replace(
                        "((ARRAY['ACTIVE'::character varying, 'COMPLETED'::character varying, 'ARCHIVED'::character varying])::text[])",
                        "(ARRAY[('ACTIVE'::character varying)::text, ('COMPLETED'::character varying)::text, ('ARCHIVED'::character varying)::text])");
            }
            row.put("definition", definition);
        }
        return row;
    }

    private void assertCatalog(int userWidth) throws Exception {
        var expected = new ArrayList<JsonNode>();
        try (var input = new ClassPathResource("postgres-it/accepted/accepted-v1-catalog.json").getInputStream()) {
            for (JsonNode original : JSON.readTree(input)) {
                var row = (ObjectNode) normalize(original);
                if (row.path("section").asText().equals("columns") && row.path("table").asText().equals("users")
                        && List.of("email", "password_hash").contains(row.path("column").asText())) {
                    row.put("type", "character varying(" + userWidth + ")");
                }
                expected.add(row);
            }
        }
        assertThat(expected).hasSize(86 + 46 + 42);
        assertThat(catalog()).containsExactlyInAnyOrderElementsOf(expected);
        assertThat(query("SELECT tablename FROM pg_tables WHERE schemaname = current_schema() AND tablename <> 'flyway_schema_history'"))
                .hasSize(10);
        assertThat(query("SELECT column_name || ':' || data_type || ':' || character_maximum_length FROM information_schema.columns "
                + "WHERE table_schema = current_schema() AND table_name = 'users' AND column_name IN ('email', 'password_hash') ORDER BY column_name"))
                .containsExactly("email:character varying:" + userWidth, "password_hash:character varying:" + userWidth);
        assertThat(query("SELECT sequencename FROM pg_sequences WHERE schemaname = current_schema()")).hasSize(9);
    }

    private List<String> structure() throws SQLException {
        return query("""
                SELECT jsonb_build_array(table_name, column_name, ordinal_position)::text
                FROM information_schema.columns
                WHERE table_schema = current_schema() AND table_name <> 'flyway_schema_history'
                UNION ALL
                SELECT jsonb_build_array(s.sequencename, s.data_type::text, s.start_value, s.min_value,
                    s.max_value, s.increment_by, s.cycle, s.cache_size, t.relname, a.attname)::text
                FROM pg_sequences s
                JOIN pg_class c ON c.relname = s.sequencename AND c.relnamespace = current_schema()::regnamespace
                JOIN pg_depend d ON d.objid = c.oid AND d.classid = 'pg_class'::regclass AND d.deptype = 'a'
                JOIN pg_class t ON t.oid = d.refobjid
                JOIN pg_attribute a ON a.attrelid = t.oid AND a.attnum = d.refobjsubid
                WHERE s.schemaname = current_schema()
                ORDER BY 1
                """);
    }
}
