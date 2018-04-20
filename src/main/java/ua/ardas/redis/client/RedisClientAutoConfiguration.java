package ua.ardas.redis.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnNotWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(RedisClientProperties.class)
public class RedisClientAutoConfiguration {

    @Bean
    @ConditionalOnNotWebApplication
    public ObjectMapper objectMapper() {
        return new ObjectMapper();
    }
}
