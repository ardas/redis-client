package ua.ardas.redis.client;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "redis.client")
public class RedisClientProperties {

    private int timeout = 5;
    private int threadPool = 5;
}
