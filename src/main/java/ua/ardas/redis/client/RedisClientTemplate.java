package ua.ardas.redis.client;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.lettuce.core.RedisCommandInterruptedException;
import lombok.extern.apachecommons.CommonsLog;
import org.apache.commons.lang3.StringUtils;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.data.redis.RedisSystemException;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import ua.ardas.redis.client.dto.RedisRequest;
import ua.ardas.redis.client.dto.RedisResponse;
import ua.ardas.redis.client.dto.ResponseKey;

import java.io.Closeable;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Function;

@CommonsLog
public class RedisClientTemplate extends StringRedisTemplate implements Closeable {

    private static final String REQUEST_TEMPLATE = "%s-request";
    private static final String RESPONSE_TEMPLATE = "%s-%s-response";

    private final RedisClientProperties properties;
    private final ObjectMapper objectMapper;

    private ExecutorService executorService;
    private final Map<String, Future<Object>> activeListeners = new HashMap<>();

    public RedisClientTemplate(RedisConnectionFactory connectionFactory, RedisClientProperties properties, ObjectMapper objectMapper) {
        super(connectionFactory);
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.executorService = Executors.newFixedThreadPool(properties.getThreadPool());
    }

    public <R, V> RedisResponse<R> send(String channel, V value, Class<R> type) throws IOException {
        UUID requestId = UUID.randomUUID();
        String requestChannel = makeRequestChannel(channel);
        String responseChannel = makeResponseChannel(channel, requestId);
        log.info(String.format("Redis client is going to send message\n   Request channel: %s\n   Response channel: %s", requestChannel, responseChannel));

        RedisRequest<V> request = RedisRequest.<V>builder()
                .request_id(requestId)
                .body(value)
                .expire_time(new Date().getTime() + properties.getTimeout() * 1000)
                .build();
        opsForList().leftPush(requestChannel, objectMapper.writeValueAsString(request));
        String response = opsForList().rightPop(responseChannel, properties.getTimeout(), TimeUnit.SECONDS);

        if (StringUtils.isBlank(response)) {
            return RedisResponse.<R>builder()
                    .key(ResponseKey.TIMEOUT)
                    .message("Redis couldn't get message from channel " + responseChannel)
                    .body(null)
                    .build();
        }

        JavaType javaType = objectMapper.getTypeFactory().constructParametricType(RedisResponse.class, type);
        return objectMapper.readValue(response, javaType);
    }

    public <T, V> void listenChannel(String channel, Function<V, T> callback, Class<V> type) {
        String listenChannel = makeRequestChannel(channel);
        log.info(String.format("Start listen channel %s", listenChannel));
        stopListenChannel(channel);
        Future<Object> future = executorService.submit(() -> {
            while (Thread.currentThread().isAlive()) {
                RedisRequest<V> message = null;
                try {
                    message = waitMessage(listenChannel, type);
                    if (message.isExpired()) {
                        log.warn(String.format("message.isExpired on %s %s", message.getExpire_time(), message.getBody()));
                        continue;
                    }
                    T result = callback.apply(message.getBody());
                    if (message.isExpired()) {
                        log.warn(String.format("message.isExpired on %s %s", message.getExpire_time(), message.getBody()));
                        continue;
                    }
                    sendSuccessResponse(channel, result, message);
                } catch (RedisCommandInterruptedException e) {
                    log.warn("Redis listener was interrupted!");
                    return null;
                } catch (QueryTimeoutException e) {
                    log.debug("Don't have any message!");
                } catch (Exception e) {
                    log.error("Something wrong!", e);
                    sendRequestException(channel, message, ResponseKey.INTERNAL_ERROR, e);
                }
            }
            log.warn("exit from redis read cycle");
            return null;
        });
        activeListeners.put(listenChannel, future);
    }

    public void stopListenChannel(String channel) {
        String listenChannel = makeRequestChannel(channel);
        if (activeListeners.containsKey(listenChannel)) {
            activeListeners.get(listenChannel).cancel(true);
        }
    }

    private <T, V> void sendRequestException(String channel, RedisRequest<V> message, ResponseKey key, Exception e) {
        String responseChannel = makeResponseChannel(channel, message);

        if (StringUtils.isNotBlank(responseChannel)) {
            RedisResponse<T> response = RedisResponse.<T>builder()
                    .key(key)
                    .message(e.getMessage())
                    .build();
            try {
                opsForList().rightPush(responseChannel, objectMapper.writeValueAsString(response));
            } catch (JsonProcessingException exception) {
                log.error("Can't convert response as JSON!", exception);
            }
        }
    }

    public <V> void listenChannel(String channel, Consumer<V> callback, Class<V> type) {
        listenChannel(channel, item -> {
            callback.accept(item);
            return null;
        }, type);
    }

    private <V> RedisRequest<V> waitMessage(String channel, Class<V> type) throws IOException {
        String body;
        try {
            while (getConnectionFactory().getConnection().isClosed()) {
                sleep(1000);
            }
            body = opsForList().leftPop(channel, 0, TimeUnit.MILLISECONDS);
        } catch (IllegalStateException | RedisSystemException e) {
            throw new RedisCommandInterruptedException(e.getCause());
        }
        log.info(String.format("Receive message: channel = %s", channel));
        JavaType javaType = objectMapper.getTypeFactory().constructParametricType(RedisRequest.class, type);
        return objectMapper.readValue(body, javaType);
    }

    private <T, V> void sendSuccessResponse(String channel, T result, RedisRequest<V> message) throws JsonProcessingException {
        RedisResponse<T> response = RedisResponse.<T>builder()
                .key(ResponseKey.OK)
                .body(result)
                .build();
        String responseChannel = String.format(RESPONSE_TEMPLATE, channel, message.getRequest_id());
        log.info(String.format("Redis client is going to send response: \nchannel = %s", responseChannel));
        opsForList().rightPush(responseChannel, objectMapper.writeValueAsString(response));
    }

    private static String makeRequestChannel(String channel) {
        return String.format(REQUEST_TEMPLATE, channel);
    }

    private static String makeResponseChannel(String channel, UUID requestId) {
        return String.format(RESPONSE_TEMPLATE, channel, requestId);
    }

    private static String makeResponseChannel(String channel, RedisRequest message) {
        return Optional.ofNullable(message)
                .map(RedisRequest::getRequest_id)
                .map(item -> String.format(RESPONSE_TEMPLATE, channel, item))
                .orElse(null);
    }

    private void sleep(long timeout) {
        try {
            Thread.sleep(timeout);
        } catch (InterruptedException e) {
            log.warn("Thread sleep was interrupted!", e);
        }
    }

    @Override
    public void close() {
        executorService.shutdownNow();
    }
}
