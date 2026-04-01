package io.github.rehody.abplatform.config;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RedissonConfig {

    @Bean(destroyMethod = "shutdown")
    public RedissonClient redissonClient(
            @Value("${spring.data.redis.host:localhost}") String host,
            @Value("${spring.data.redis.port:6379}") int port,
            @Value("${spring.data.redis.password:}") String password) {
        return Redisson.create(buildConfig(host, port, password));
    }

    private String buildAddress(String host, int port, String password) {
        if (password == null || password.isBlank()) {
            return "redis://%s:%d".formatted(host, port);
        }

        String encodedPassword =
                URLEncoder.encode(password, StandardCharsets.UTF_8).replace("+", "%20");

        return "redis://:%s@%s:%d".formatted(encodedPassword, host, port);
    }

    private Config buildConfig(String host, int port, String password) {
        Config config = new Config();
        config.useSingleServer().setAddress(buildAddress(host, port, password));
        return config;
    }
}
