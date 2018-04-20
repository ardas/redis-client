package ua.ardas.redis.client;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.testcontainers.containers.GenericContainer;

@Configuration
public class TestConfigs {

    private static final int REDIS_PORT = 6379;

    /**
     * Redis
     */
    private static GenericContainer redis = new GenericContainer("redis:3.0.2").withExposedPorts(REDIS_PORT);
    static { redis.start(); }

    @Bean
    public LettuceConnectionFactory redisConnectionFactory() {
        RedisStandaloneConfiguration configuration = new RedisStandaloneConfiguration();
        configuration.setHostName(redis.getContainerIpAddress());
        configuration.setPort(redis.getMappedPort(REDIS_PORT));
        return new LettuceConnectionFactory(configuration);
    }
}
