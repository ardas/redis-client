package ua.ardas.redis.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnNotWebApplication;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;

@Configuration
@AutoConfigureBefore(RedisAutoConfiguration.class)
@EnableConfigurationProperties(RedisClientProperties.class)
public class RedisClientAutoConfiguration {

    @Bean
    @ConditionalOnNotWebApplication
    public ObjectMapper objectMapper() {
        return new ObjectMapper();
    }

    @Bean("redisClientTemplate")
    @ConditionalOnClass({ObjectMapper.class})
    public RedisClientTemplate redisClientTemplate(RedisConnectionFactory connectionFactory,
                                                   RedisClientProperties properties,
                                                   ObjectMapper objectMapper) {
        return new RedisClientTemplate(connectionFactory, properties, objectMapper);
    }
}
