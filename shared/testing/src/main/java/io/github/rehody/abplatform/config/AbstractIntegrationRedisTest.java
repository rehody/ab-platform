package io.github.rehody.abplatform.config;

import com.redis.testcontainers.RedisContainer;
import org.springframework.boot.data.redis.test.autoconfigure.DataRedisTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@DataRedisTest
@Testcontainers(disabledWithoutDocker = true)
@ActiveProfiles("integration-redis")
public abstract class AbstractIntegrationRedisTest {

    private static final DockerImageName IMAGE = DockerImageName.parse("redis:8.4-alpine");

    @Container
    private static final RedisContainer REDIS = new RedisContainer(IMAGE).withStartupAttempts(3);

    @DynamicPropertySource
    static void configureRedis(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", REDIS::getFirstMappedPort);
    }
}
