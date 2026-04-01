package io.github.rehody.abplatform.config;

import org.springframework.boot.data.jdbc.test.autoconfigure.DataJdbcTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@DataJdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers(disabledWithoutDocker = true)
@ActiveProfiles("integration-db")
public abstract class AbstractIntegrationDatabaseTest {

    private static final DockerImageName IMAGE = DockerImageName.parse("postgres:18.2");
    private static final String DATABASE_NAME = "testdb";
    private static final String USERNAME = "test";
    private static final String PASSWORD = "test";

    @Container
    @SuppressWarnings("resource")
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(IMAGE)
            .withDatabaseName(DATABASE_NAME)
            .withUsername(USERNAME)
            .withPassword(PASSWORD)
            .withStartupAttempts(3);

    @DynamicPropertySource
    static void configurePostgres(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }
}
