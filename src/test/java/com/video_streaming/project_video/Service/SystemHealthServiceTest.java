package com.video_streaming.project_video.Service;

import com.video_streaming.project_video.DTOs.SystemHealthDTO;
import com.video_streaming.project_video.ServiceImplementation.SystemHealthServiceImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Proxy;

import static org.junit.jupiter.api.Assertions.*;

class SystemHealthServiceTest {

    @Test
    @DisplayName("Should report DEGRADED when some services are unreachable or unconfigured")
    void testGetSystemHealth_unconfiguredDefaults() {
        SystemHealthServiceImpl healthService = new SystemHealthServiceImpl();

        SystemHealthDTO health = healthService.getSystemHealth();

        assertNotNull(health);
        assertNotNull(health.getTimestamp());
        assertTrue(health.getUptimeSeconds() >= 0);
        assertNotNull(health.getServices());

        // Should include all 5 service keys
        assertTrue(health.getServices().containsKey("postgres"));
        assertTrue(health.getServices().containsKey("redis"));
        assertTrue(health.getServices().containsKey("rabbitmq"));
        assertTrue(health.getServices().containsKey("firebase"));
        assertTrue(health.getServices().containsKey("s3"));

        // Without injected beans, status should be DEGRADED or DOWN, not throw exceptions
        assertNotEquals("UP", health.getStatus());
    }

    @Test
    @DisplayName("Should correctly report UP when backing services respond with healthy status")
    @SuppressWarnings("unchecked")
    void testGetSystemHealth_healthyServices() {
        SystemHealthServiceImpl healthService = new SystemHealthServiceImpl();

        // Subclass JdbcTemplate (since it's a class)
        JdbcTemplate mockJdbc = new JdbcTemplate() {
            @Override
            public <T> T queryForObject(String sql, Class<T> requiredType) {
                return (T) Integer.valueOf(1);
            }
        };
        ReflectionTestUtils.setField(healthService, "jdbcTemplate", mockJdbc);

        // Mock RedisConnectionFactory (since it's an interface)
        RedisConnection mockRedisConnection = (RedisConnection) Proxy.newProxyInstance(
                RedisConnection.class.getClassLoader(),
                new Class<?>[]{RedisConnection.class},
                (proxy, method, args) -> {
                    if ("ping".equals(method.getName())) return "PONG";
                    return null;
                }
        );
        RedisConnectionFactory mockRedisFactory = (RedisConnectionFactory) Proxy.newProxyInstance(
                RedisConnectionFactory.class.getClassLoader(),
                new Class<?>[]{RedisConnectionFactory.class},
                (proxy, method, args) -> {
                    if ("getConnection".equals(method.getName())) return mockRedisConnection;
                    return null;
                }
        );
        ReflectionTestUtils.setField(healthService, "redisConnectionFactory", mockRedisFactory);

        SystemHealthDTO health = healthService.getSystemHealth();

        assertEquals("UP", health.getServices().get("postgres").getStatus());
        assertEquals("UP", health.getServices().get("redis").getStatus());
        assertTrue(health.getServices().get("postgres").getLatencyMs() >= 0);
    }
}
