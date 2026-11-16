package ec.edu.udla.consultas.puntos.adapter.out.redis;

import com.fasterxml.jackson.databind.ObjectMapper;
import ec.edu.udla.consultas.puntos.application.port.out.PuntosLicenciaCachePort;
import ec.edu.udla.consultas.puntos.application.port.out.PuntosLicenciaSimulationCachePort;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.connection.RedisConnectionFactory;

import static org.assertj.core.api.Assertions.assertThat;

class RedisInfrastructureConfigurationTests {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(RedisInfrastructureConfiguration.class, ObjectMapperTestConfiguration.class);

    @Test
    void doesNotCreateRedisBeansWhenCacheIsDisabled() {
        contextRunner.withPropertyValues("app.cache.redis.enabled=false").run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBeansOfType(PuntosLicenciaCachePort.class)).isEmpty();
            assertThat(context.getBeansOfType(RedisConnectionFactory.class)).isEmpty();
        });
    }

    @Test
    void rejectsEnabledCacheWithoutHmacSecret() {
        contextRunner.withPropertyValues(
                "app.cache.redis.enabled=true",
                "app.cache.redis.host=127.0.0.1",
                "app.cache.redis.port=6379",
                "app.cache.redis.ttl=15m").run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasRootCauseMessage(
                                    "Redis habilitado requiere REDIS_CACHE_HMAC_SECRET de al menos 32 bytes UTF-8.");
                });
    }

    @Test
    void rejectsEnabledCacheWithoutPositiveTtl() {
        contextRunner.withPropertyValues(
                "app.cache.redis.enabled=true",
                "app.cache.redis.host=127.0.0.1",
                "app.cache.redis.port=6379",
                "app.cache.redis.hmac-secret=synthetic-test-hmac-secret-value-00000000000000000000000000000000")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasRootCauseMessage("Redis habilitado requiere REDIS_CACHE_TTL positivo.");
                });
    }

    @Test
    void rejectsEnabledCacheWithoutHost() {
        contextRunner.withPropertyValues(
                "app.cache.redis.enabled=true",
                "app.cache.redis.hmac-secret=synthetic-test-hmac-secret-value-00000000000000000000000000000000",
                "app.cache.redis.ttl=15m").run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasRootCauseMessage("Redis habilitado requiere configurar REDIS_HOST.");
                });
    }

    @Test
    void rejectsHmacSecretShorterThanConfiguredMinimum() {
        contextRunner.withPropertyValues(
                "app.cache.redis.enabled=true",
                "app.cache.redis.host=127.0.0.1",
                "app.cache.redis.port=6379",
                "app.cache.redis.hmac-secret=short-secret",
                "app.cache.redis.ttl=15m").run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasRootCauseMessage(
                                    "Redis habilitado requiere REDIS_CACHE_HMAC_SECRET de al menos 32 bytes UTF-8.");
                });
    }

    @Test
    void createsCacheBeansWithValidSettingsWithoutRequiringAConnectionAtStartup() {
        contextRunner.withPropertyValues(
                "app.cache.redis.enabled=true",
                "app.cache.redis.host=127.0.0.1",
                "app.cache.redis.port=6379",
                "app.cache.redis.hmac-secret=synthetic-test-hmac-secret-value-00000000000000000000000000000000",
                "app.cache.redis.ttl=15m").run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBeansOfType(PuntosLicenciaCachePort.class)).hasSize(1);
                    assertThat(context.getBeansOfType(RedisConnectionFactory.class)).hasSize(1);
                });
    }

    @Test
    void localSimulationProfileRegistersOnlyTheSimulationCachePort() {
        new ApplicationContextRunner()
                .withUserConfiguration(
                        RedisInfrastructureConfiguration.class,
                        RedisSimulationCacheConfiguration.class,
                        ObjectMapperTestConfiguration.class)
                .withInitializer(context -> context.getEnvironment().setActiveProfiles("local-simulation"))
                .withPropertyValues(
                        "app.cache.redis.enabled=true",
                        "app.cache.redis.host=127.0.0.1",
                        "app.cache.redis.port=6379",
                        "app.cache.redis.hmac-secret=synthetic-test-hmac-secret-value-00000000000000000000000000000000",
                        "app.cache.redis.ttl=15m",
                        "app.ant.simulation.enabled=true")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBeansOfType(PuntosLicenciaCachePort.class)).isEmpty();
                    assertThat(context.getBeansOfType(PuntosLicenciaSimulationCachePort.class)).hasSize(1);
                    assertThat(context.getBeansOfType(RedisConnectionFactory.class)).hasSize(1);
                    assertThat(context).hasBean("redisTemplate");
                });
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class ObjectMapperTestConfiguration {

        @Bean
        ObjectMapper redisTestObjectMapper() {
            return new ObjectMapper();
        }
    }
}
