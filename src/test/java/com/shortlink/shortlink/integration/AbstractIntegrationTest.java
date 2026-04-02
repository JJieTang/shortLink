package com.shortlink.shortlink.integration;

import com.shortlink.shortlink.model.User;
import com.shortlink.shortlink.repository.UserRepository;
import com.shortlink.shortlink.security.JwtTokenProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;

@SpringBootTest
abstract class AbstractIntegrationTest {

    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("shortlink")
            .withUsername("shortlink")
            .withPassword("shortlink");

    static final GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine")
            .withExposedPorts(6379);

    static {
        postgres.start();
        redis.start();
    }

    @Autowired
    protected UserRepository userRepository;

    @Autowired
    protected PasswordEncoder passwordEncoder;

    @Autowired
    protected JwtTokenProvider jwtTokenProvider;

    @DynamicPropertySource
    static void configure(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", redis::getFirstMappedPort);
        registry.add("app.base-url", () -> "http://localhost:8080");
    }

    protected String issueAccessToken(String email, String name) {
        userRepository.findByEmail(email).ifPresent(userRepository::delete);

        User user = new User();
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode("SecretPass123"));
        user.setName(name);
        user.setRole("USER");
        user.setDailyQuota(100);

        User savedUser = userRepository.save(user);
        return jwtTokenProvider.generateAccessToken(savedUser.getId(), savedUser.getEmail(), savedUser.getRole());
    }

    protected String bearer(String accessToken) {
        return "Bearer " + accessToken;
    }
}
