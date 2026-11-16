package ec.edu.udla.consultas.puntos.adapter.out.redis;

import com.fasterxml.jackson.databind.ObjectMapper;
import ec.edu.udla.consultas.puntos.adapter.out.ant.ResilientPuntosLicenciaConsultaAdapter;
import ec.edu.udla.consultas.puntos.adapter.out.ant.simulation.AntSimulationProperties;
import ec.edu.udla.consultas.puntos.adapter.out.ant.simulation.SimulatedAntPuntosLicenciaAdapter;
import ec.edu.udla.consultas.puntos.application.exception.PuntosLicenciaProviderException;
import ec.edu.udla.consultas.puntos.application.port.out.PuntosLicenciaCachePort;
import ec.edu.udla.consultas.puntos.application.port.out.PuntosLicenciaSimulationCachePort;
import ec.edu.udla.consultas.puntos.application.service.ConsultarPuntosLicenciaService;
import ec.edu.udla.consultas.puntos.domain.Cedula;
import ec.edu.udla.consultas.puntos.domain.FuentePuntosLicencia;
import ec.edu.udla.consultas.puntos.domain.ResultadoPuntosLicencia;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Opt-in smoke test against a local Redis instance. It is excluded from the
 * normal Surefire naming patterns and can be run explicitly with the documented
 * redis.integration system properties.
 */
@EnabledIfSystemProperty(named = "redis.integration.enabled", matches = "true")
class RedisPuntosLicenciaCacheIntegrationIT {

    private static final String HMAC_SECRET = "synthetic-local-redis-test-secret-at-least-32-bytes";
    private static final Duration TTL = Duration.ofMinutes(2);

    private LettuceConnectionFactory connectionFactory;
    private StringRedisTemplate redis;
    private HmacCacheKeyFactory keyFactory;
    private RedisPuntosLicenciaCacheAdapter adapter;
    private HmacCacheKeyFactory simulationKeyFactory;
    private RedisSimulatedPuntosLicenciaCacheAdapter simulationAdapter;
    private Cedula cedula;

    @BeforeEach
    void setUp() {
        String host = System.getProperty("redis.integration.host", "127.0.0.1");
        int port = Integer.parseInt(System.getProperty("redis.integration.port", "6379"));
        connectionFactory = new LettuceConnectionFactory(new RedisStandaloneConfiguration(host, port));
        connectionFactory.afterPropertiesSet();
        redis = new StringRedisTemplate(connectionFactory);
        redis.afterPropertiesSet();
        keyFactory = new HmacCacheKeyFactory(HMAC_SECRET);
        adapter = new RedisPuntosLicenciaCacheAdapter(redis, new ObjectMapper(), keyFactory, TTL);
        simulationKeyFactory = new HmacCacheKeyFactory(
                HMAC_SECRET, HmacCacheKeyFactory.SIMULATION_KEY_PREFIX);
        simulationAdapter = new RedisSimulatedPuntosLicenciaCacheAdapter(
                redis, new ObjectMapper(), simulationKeyFactory, TTL);
    }

    @AfterEach
    void cleanUp() {
        try {
            if (redis != null && cedula != null) {
                redis.delete(keyFactory.keyFor(cedula));
                redis.delete(simulationKeyFactory.keyFor(cedula));
            }
        } finally {
            if (connectionFactory != null) {
                connectionFactory.destroy();
            }
        }
    }

    @Test
    void writesAndReadsJsonWithExpiryAndOriginalTimestamp() throws Exception {
        cedula = new Cedula("0000000000");
        Instant fetchedAt = Instant.parse("2026-11-15T08:00:00Z");
        int totalPuntos = 7;

        adapter.guardarResultado(cedula,
                new ResultadoPuntosLicencia(totalPuntos, fetchedAt, FuentePuntosLicencia.ANT));

        var cached = adapter.obtenerUltimoResultado(cedula).orElseThrow();
        String cacheKey = keyFactory.keyFor(cedula);
        String storedDocument = redis.opsForValue().get(cacheKey);
        var stored = new ObjectMapper().readTree(storedDocument);
        Long remainingTtlMs = redis.getExpire(cacheKey, TimeUnit.MILLISECONDS);

        assertThat(cached.totalPuntos()).isEqualTo(totalPuntos);
        assertThat(cached.fetchedAt()).isEqualTo(fetchedAt);
        assertThat(cached.fuente()).isEqualTo(FuentePuntosLicencia.CACHE);
        assertThat(cached.originSource()).isEqualTo(FuentePuntosLicencia.ANT);
        assertThat(cacheKey).doesNotContain(cedula.value());
        assertThat(storedDocument).doesNotContain(cedula.value());
        assertThat(stored.size()).isEqualTo(3);
        assertThat(stored.path("totalPuntos").asInt()).isEqualTo(totalPuntos);
        assertThat(stored.path("fetchedAt").asText()).isEqualTo(fetchedAt.toString());
        assertThat(stored.path("source").asText()).isEqualTo(FuentePuntosLicencia.ANT.name());
        assertThat(remainingTtlMs).isPositive().isLessThanOrEqualTo(TTL.toMillis());
    }

    @Test
    void realRedisSupportsUseCaseSuccessThenTimeoutAndSimulationCacheFallback() throws Exception {
        cedula = new Cedula("0000000000");
        AntSimulationProperties properties = new AntSimulationProperties();
        properties.setScenario(AntSimulationProperties.Scenario.SUCCESS_THEN_TIMEOUT);
        properties.setTotalPoints(23);
        var simulator = new SimulatedAntPuntosLicenciaAdapter(properties);
        CircuitBreaker breaker = CircuitBreaker.of("redis-integration-ant-points",
                CircuitBreakerConfig.custom()
                        .slidingWindowSize(5)
                        .minimumNumberOfCalls(3)
                        .failureRateThreshold(50)
                        .recordException(exception -> exception instanceof PuntosLicenciaProviderException)
                        .build());
        var provider = new ResilientPuntosLicenciaConsultaAdapter(simulator, breaker);
        PuntosLicenciaCachePort antCache = adapter;
        PuntosLicenciaSimulationCachePort simulatedCache = simulationAdapter;
        var useCase = new ConsultarPuntosLicenciaService(
                provider, Optional.of(antCache), Optional.of(simulatedCache));

        var first = useCase.consultar(cedula);
        assertThat(first.source()).isEqualTo(FuentePuntosLicencia.SIMULATED);
        assertThat(first.originSource()).isEqualTo(FuentePuntosLicencia.SIMULATED);
        assertThat(first.totalPuntos()).isEqualTo(23);
        assertThat(first.stale()).isFalse();

        String simulationKey = simulationKeyFactory.keyFor(cedula);
        String storedDocument = redis.opsForValue().get(simulationKey);
        assertThat(storedDocument).isNotNull().doesNotContain(cedula.value());
        var stored = new ObjectMapper().readTree(storedDocument);
        assertThat(simulationKey).startsWith(HmacCacheKeyFactory.SIMULATION_KEY_PREFIX)
                .doesNotContain(cedula.value());
        assertThat(stored.size()).isEqualTo(3);
        assertThat(stored.path("source").asText()).isEqualTo(FuentePuntosLicencia.SIMULATED.name());
        assertThat(redis.getExpire(simulationKey, TimeUnit.MILLISECONDS)).isPositive();
        assertThat(adapter.obtenerUltimoResultado(cedula)).isEmpty();

        var second = useCase.consultar(cedula);

        assertThat(second.source()).isEqualTo(FuentePuntosLicencia.CACHE);
        assertThat(second.originSource()).isEqualTo(FuentePuntosLicencia.SIMULATED);
        assertThat(second.totalPuntos()).isEqualTo(first.totalPuntos());
        assertThat(second.fetchedAt()).isEqualTo(first.fetchedAt());
        assertThat(second.stale()).isTrue();
        assertThat(second.warning()).contains("SIMULATED", "No es información oficial");
    }
}
