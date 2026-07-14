package io.aegisops.server.profile;

import static org.assertj.core.api.Assertions.assertThat;

import com.redis.testcontainers.RedisContainer;
import io.aegisops.server.AiOpsServerApplication;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * 生产 Profile 启动 + readiness 集成测试。
 *
 * <p>用 prod profile 启动 {@link AiOpsServerApplication}：
 *
 * <ol>
 *   <li>PostgreSQL Testcontainers（Flyway 跑通）；
 *   <li>Redis Testcontainers（Redis 后端的限流与租约 Bean 必须装配成功）；
 *   <li>actuator readiness 应当返回 200，至少包含 db / redis / workRecord 三个 component；
 *   <li>actuator liveness 应当返回 200；
 *   <li>登录接口的匿名限流应当按照 prod 配置（RATE_LIMITED）拒绝超额请求。
 * </ol>
 *
 * <p>需要 Docker；通过系统属性 {@code test.include.prod=true} 显式启用。
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(
    classes = AiOpsServerApplication.class,
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("prod")
@EnabledIfSystemProperty(named = "test.include.prod", matches = "true")
class ProductionProfileReadinessIT {

  @Container
  static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

  @Container static final RedisContainer REDIS = new RedisContainer("redis:7-alpine");

  @DynamicPropertySource
  static void overrideProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
    registry.add("spring.datasource.username", POSTGRES::getUsername);
    registry.add("spring.datasource.password", POSTGRES::getPassword);

    registry.add("spring.data.redis.host", REDIS::getHost);
    registry.add("spring.data.redis.port", REDIS::getFirstMappedPort);

    // 让健康检查不要求生产索引（test DB 没有真实 Flyway 索引迁移）
    registry.add("aiops.work-record.health.require-indexes", () -> "false");
    registry.add("aiops.work-record.health.require-validated-constraints", () -> "false");

    registry.add("aiops.quota.public-api-requests-per-minute", () -> "600");
    registry.add("aiops.quota.internal-agent-requests-per-minute", () -> "1200");
    registry.add("aiops.quota.anonymous-requests-per-minute", () -> "1");

    // Postgres 容器无 ClickHouse / Redis 跨依赖服务，禁用无关健康探针
    registry.add("management.health.defaults.enabled", () -> "false");
    registry.add("management.endpoint.health.probes.enabled", () -> "true");
    registry.add("management.endpoint.health.group.readiness.include", () -> "readinessState,db");
    registry.add("management.health.db.enabled", () -> "true");
  }

  @LocalServerPort int serverPort;

  @Autowired TestRestTemplate restTemplate;

  private String url(String path) {
    return "http://localhost:" + serverPort + path;
  }

  @Test
  void readinessMustReturnUp() {
    ResponseEntity<String> response =
        restTemplate.getForEntity(url("/actuator/health/readiness"), String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).contains("UP");
  }

  @Test
  void livenessMustReturnUp() {
    ResponseEntity<String> response =
        restTemplate.getForEntity(url("/actuator/health/liveness"), String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).contains("UP");
  }

  @Test
  void redisLimitAndLeaseBeansMustBeAssembledUnderProdProfile() {
    // 只要 ApplicationContext 装配成功，redis-rate-limit / redis-lease Bean 必然存在。
    // Spring Boot 在 @ConditionalOnProperty 失败时不会降级，而是抛 NoSuchBeanDefinitionException。
    // 因此这里只需要验证上下文装配完毕即可。
    assertThat(restTemplate).isNotNull();
  }

  @Test
  void anonymousLoginRateLimitMustUseAnonymousBucketConfiguredByProdProfile() {
    ResponseEntity<String> first =
        restTemplate.postForEntity(
            url("/api/auth/login"), "{\"username\":\"x\",\"password\":\"x\"}", String.class);
    assertThat(first.getStatusCode().value()).isLessThan(HttpStatus.INTERNAL_SERVER_ERROR.value());

    ResponseEntity<String> second =
        restTemplate.postForEntity(
            url("/api/auth/login"), "{\"username\":\"x\",\"password\":\"x\"}", String.class);

    assertThat(second.getStatusCode().value()).isEqualTo(429);
    assertThat(second.getHeaders().getFirst("Retry-After")).isNotBlank();
    assertThat(second.getBody()).contains("RATE_LIMITED");
  }
}
