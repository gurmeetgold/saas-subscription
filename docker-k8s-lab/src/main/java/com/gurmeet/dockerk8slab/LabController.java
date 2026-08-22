package com.gurmeet.dockerk8slab;

import java.net.InetAddress;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class LabController {

    private final StringRedisTemplate redis;

    @Value("${APP_ENV:local}")
    private String appEnv;

    @Value("${APP_MESSAGE:Hello from Java Spring Boot}")
    private String appMessage;

    public LabController(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @GetMapping("/")
    public Map<String, Object> home() throws Exception {
        Long visits = redis.opsForValue().increment("visits");
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("message", appMessage);
        response.put("environment", appEnv);
        response.put("hostname", InetAddress.getLocalHost().getHostName());
        response.put("visits", visits);
        return response;
    }

    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of("status", "UP");
    }

    @GetMapping("/ready")
    public ResponseEntity<Map<String, String>> ready() {
        try {
            String pong = redis.getConnectionFactory().getConnection().ping();
            if ("PONG".equalsIgnoreCase(pong)) {
                return ResponseEntity.ok(Map.of("status", "READY", "redis", "PONG"));
            }
        } catch (Exception ignored) {
        }
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(Map.of("status", "NOT_READY", "redis", "UNAVAILABLE"));
    }
}
