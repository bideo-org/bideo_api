package com.video_streaming.project_video.ServiceImplementation;

import com.amazonaws.services.s3.AmazonS3;
import com.google.firebase.FirebaseApp;
import com.video_streaming.project_video.DTOs.SystemHealthDTO;
import com.video_streaming.project_video.DTOs.SystemHealthDTO.ServiceStatusDTO;
import com.video_streaming.project_video.Service.SystemHealthService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.lang.management.ManagementFactory;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class SystemHealthServiceImpl implements SystemHealthService {

    private static final Logger logger = LoggerFactory.getLogger(SystemHealthServiceImpl.class);

    @Autowired(required = false)
    private JdbcTemplate jdbcTemplate;

    @Autowired(required = false)
    private RedisConnectionFactory redisConnectionFactory;

    @Autowired(required = false)
    private ConnectionFactory rabbitConnectionFactory;

    @Autowired(required = false)
    private AmazonS3 amazonS3;

    @Value("${aws.s3.bucket:}")
    private String s3BucketName;

    @Override
    public SystemHealthDTO getSystemHealth() {
        Map<String, ServiceStatusDTO> services = new LinkedHashMap<>();

        // 1. PostgreSQL check
        services.put("postgres", checkPostgres());

        // 2. Redis check
        services.put("redis", checkRedis());

        // 3. RabbitMQ check
        services.put("rabbitmq", checkRabbitMQ());

        // 4. Firebase check
        services.put("firebase", checkFirebase());

        // 5. AWS S3 check
        services.put("s3", checkS3());

        // Compute overall status
        long downCount = services.values().stream()
                .filter(s -> "DOWN".equalsIgnoreCase(s.getStatus()))
                .count();

        String overallStatus;
        if (downCount == 0) {
            overallStatus = "UP";
        } else if (downCount < services.size()) {
            overallStatus = "DEGRADED";
        } else {
            overallStatus = "DOWN";
        }

        long uptimeSec = ManagementFactory.getRuntimeMXBean().getUptime() / 1000;

        return SystemHealthDTO.builder()
                .status(overallStatus)
                .timestamp(Instant.now().toString())
                .uptimeSeconds(uptimeSec)
                .version("0.0.1")
                .services(services)
                .build();
    }

    private ServiceStatusDTO checkPostgres() {
        if (jdbcTemplate == null) {
            return ServiceStatusDTO.builder().status("DOWN").message("JdbcTemplate bean not available").build();
        }
        long start = System.currentTimeMillis();
        try {
            Integer result = jdbcTemplate.queryForObject("SELECT 1", Integer.class);
            long latency = System.currentTimeMillis() - start;
            if (result != null && result == 1) {
                return ServiceStatusDTO.builder()
                        .status("UP")
                        .latencyMs(latency)
                        .message("Connected to PostgreSQL")
                        .build();
            } else {
                return ServiceStatusDTO.builder()
                        .status("DOWN")
                        .latencyMs(latency)
                        .message("Unexpected query result")
                        .build();
            }
        } catch (Exception e) {
            long latency = System.currentTimeMillis() - start;
            logger.warn("PostgreSQL health check failed: {}", e.getMessage());
            return ServiceStatusDTO.builder()
                    .status("DOWN")
                    .latencyMs(latency)
                    .message("Connection failed: " + e.getMessage())
                    .build();
        }
    }

    private ServiceStatusDTO checkRedis() {
        if (redisConnectionFactory == null) {
            return ServiceStatusDTO.builder().status("DOWN").message("RedisConnectionFactory not configured").build();
        }
        long start = System.currentTimeMillis();
        try (RedisConnection connection = redisConnectionFactory.getConnection()) {
            String ping = connection.ping();
            long latency = System.currentTimeMillis() - start;
            if ("PONG".equalsIgnoreCase(ping)) {
                return ServiceStatusDTO.builder()
                        .status("UP")
                        .latencyMs(latency)
                        .message("Redis PONG received")
                        .build();
            } else {
                return ServiceStatusDTO.builder()
                        .status("DOWN")
                        .latencyMs(latency)
                        .message("Unexpected ping response: " + ping)
                        .build();
            }
        } catch (Exception e) {
            long latency = System.currentTimeMillis() - start;
            logger.warn("Redis health check failed: {}", e.getMessage());
            return ServiceStatusDTO.builder()
                    .status("DOWN")
                    .latencyMs(latency)
                    .message("Redis connection failed: " + e.getMessage())
                    .build();
        }
    }

    private ServiceStatusDTO checkRabbitMQ() {
        if (rabbitConnectionFactory == null) {
            return ServiceStatusDTO.builder().status("DOWN").message("RabbitMQ ConnectionFactory not configured").build();
        }
        long start = System.currentTimeMillis();
        try (org.springframework.amqp.rabbit.connection.Connection connection = rabbitConnectionFactory.createConnection()) {
            boolean isOpen = connection.isOpen();
            long latency = System.currentTimeMillis() - start;
            if (isOpen) {
                return ServiceStatusDTO.builder()
                        .status("UP")
                        .latencyMs(latency)
                        .message("RabbitMQ broker connected")
                        .build();
            } else {
                return ServiceStatusDTO.builder()
                        .status("DOWN")
                        .latencyMs(latency)
                        .message("RabbitMQ connection is closed")
                        .build();
            }
        } catch (Exception e) {
            long latency = System.currentTimeMillis() - start;
            logger.warn("RabbitMQ health check failed: {}", e.getMessage());
            return ServiceStatusDTO.builder()
                    .status("DOWN")
                    .latencyMs(latency)
                    .message("RabbitMQ connection failed: " + e.getMessage())
                    .build();
        }
    }

    private ServiceStatusDTO checkFirebase() {
        long start = System.currentTimeMillis();
        try {
            boolean initialized = !FirebaseApp.getApps().isEmpty();
            long latency = System.currentTimeMillis() - start;
            if (initialized) {
                return ServiceStatusDTO.builder()
                        .status("UP")
                        .latencyMs(latency)
                        .message("Firebase Admin SDK initialized (" + FirebaseApp.getInstance().getName() + ")")
                        .build();
            } else {
                return ServiceStatusDTO.builder()
                        .status("DOWN")
                        .latencyMs(latency)
                        .message("No active FirebaseApp instances found")
                        .build();
            }
        } catch (Exception e) {
            long latency = System.currentTimeMillis() - start;
            logger.warn("Firebase health check failed: {}", e.getMessage());
            return ServiceStatusDTO.builder()
                    .status("DOWN")
                    .latencyMs(latency)
                    .message("Firebase check failed: " + e.getMessage())
                    .build();
        }
    }

    private volatile ServiceStatusDTO cachedS3Status;
    private volatile long lastS3CheckTime = 0;
    private static final long S3_CACHE_DURATION_MS = 15_000;

    private ServiceStatusDTO checkS3() {
        if (amazonS3 == null) {
            return ServiceStatusDTO.builder().status("DOWN").message("AmazonS3 client not configured").build();
        }
        long now = System.currentTimeMillis();
        ServiceStatusDTO cached = this.cachedS3Status;
        if (cached != null && (now - lastS3CheckTime < S3_CACHE_DURATION_MS)) {
            return cached;
        }

        long start = System.currentTimeMillis();
        try {
            if (s3BucketName != null && !s3BucketName.isBlank()) {
                boolean exists = amazonS3.doesBucketExistV2(s3BucketName);
                long latency = System.currentTimeMillis() - start;
                ServiceStatusDTO result = ServiceStatusDTO.builder()
                        .status(exists ? "UP" : "DOWN")
                        .latencyMs(latency)
                        .message(exists ? "S3 bucket '" + s3BucketName + "' verified" : "Bucket does not exist")
                        .build();
                this.cachedS3Status = result;
                this.lastS3CheckTime = now;
                return result;
            } else {
                amazonS3.getS3AccountOwner();
                long latency = System.currentTimeMillis() - start;
                ServiceStatusDTO result = ServiceStatusDTO.builder()
                        .status("UP")
                        .latencyMs(latency)
                        .message("AWS S3 client authenticated")
                        .build();
                this.cachedS3Status = result;
                this.lastS3CheckTime = now;
                return result;
            }
        } catch (Exception e) {
            long latency = System.currentTimeMillis() - start;
            logger.warn("AWS S3 health check failed: {}", e.getMessage());
            ServiceStatusDTO result = ServiceStatusDTO.builder()
                    .status("DOWN")
                    .latencyMs(latency)
                    .message("S3 check failed: " + e.getMessage())
                    .build();
            this.cachedS3Status = result;
            this.lastS3CheckTime = now;
            return result;
        }
    }
}
