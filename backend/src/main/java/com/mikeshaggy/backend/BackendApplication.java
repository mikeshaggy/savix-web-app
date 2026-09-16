package com.mikeshaggy.backend;

import com.mikeshaggy.backend.migration.MigrationCommand;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(exclude = FlywayAutoConfiguration.class)
@EnableScheduling
public class BackendApplication {

    public static void main(String[] args) {
        // Dispatch before creating any Spring context (including scheduling and integrations).
        if (args.length > 0 && "db".equals(args[0])) {
            System.exit(MigrationCommand.run(java.util.Arrays.copyOfRange(args, 1, args.length),
                    System.getenv(), System.out, System.err));
            return;
        }
        SpringApplication.run(BackendApplication.class, args);
    }

}
