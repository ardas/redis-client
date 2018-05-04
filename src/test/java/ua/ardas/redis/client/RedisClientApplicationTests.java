package ua.ardas.redis.client;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import ua.ardas.redis.client.dto.RedisResponse;
import ua.ardas.redis.client.dto.ResponseKey;

import java.io.IOException;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

@SpringBootApplication
@RunWith(SpringJUnit4ClassRunner.class)
@SpringBootTest(classes = TestConfigs.class)
public class RedisClientApplicationTests {

    @Autowired
    private RedisClientTemplate clientTemplate;

    @Autowired
    private RedisClientProperties properties;

    public static final String TEST_CHANNEL = "TEST_CHANNEL";

    @Test
    public void testSendRedisRequestAndGetResponse() throws IOException {
        clientTemplate.listenChannel(TEST_CHANNEL, item -> {
            Assert.assertEquals("PING", item);
            return "PONG";
        }, String.class);

        RedisResponse<String> message = clientTemplate.send(TEST_CHANNEL, "PING", String.class);

        Assert.assertEquals(ResponseKey.OK, message.getKey());
        Assert.assertEquals("PONG", message.getBody());
        Assert.assertNull(message.getMessage());
    }

    @Test
    public void testSendRequestAndThrowException() throws IOException {
        clientTemplate.listenChannel(TEST_CHANNEL, item -> {
            if (null != item) {
                throw new RuntimeException("Test exception!");
            }
            return "PONG";
        }, String.class);

        RedisResponse<String> message = clientTemplate.send(TEST_CHANNEL, "PING", String.class);

        Assert.assertEquals(ResponseKey.INTERNAL_ERROR, message.getKey());
        Assert.assertTrue(message.isFailure());
        Assert.assertEquals("Test exception!", message.getMessage());
    }

    @Test
    public void testListenerTimeoutAndResponseShouldNotSendBack() throws IOException, InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        clientTemplate.listenChannel(TEST_CHANNEL, item -> {
            Assert.assertEquals("PING", item);
            sleep();
            latch.countDown();
            return "PONG";
        }, String.class);

        RedisResponse<String> message = clientTemplate.send(TEST_CHANNEL, "PING", String.class);

        Assert.assertEquals(ResponseKey.TIMEOUT, message.getKey());
        Assert.assertTrue(message.isFailure());

        latch.await(properties.getTimeout() + 2, TimeUnit.SECONDS);
        if (latch.getCount() != 0) {
            Assert.fail("Listener callback has not been invoke!");
        }

        Set<String> keys = clientTemplate.keys("");
        Assert.assertEquals(0, keys.size());
    }

    private void sleep() {
        try {
            Thread.sleep(properties.getTimeout() * 1000 + 1000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
}
