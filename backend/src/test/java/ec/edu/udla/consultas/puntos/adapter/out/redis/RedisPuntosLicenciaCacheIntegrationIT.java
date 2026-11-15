package ec.edu.udla.consultas.puntos.adapter.out.redis;

import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.util.concurrent.TimeUnit;

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
    }

    @AfterEach
    void cleanUp() {
        try {
            if (redis != null && cedula != null) {
                redis.delete(keyFactory.keyFor(cedula));
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
        assertThat(cacheKey).doesNotContain(cedula.value());
        assertThat(storedDocument).doesNotContain(cedula.value());
        assertThat(stored.size()).isEqualTo(3);
        assertThat(stored.path("totalPuntos").asInt()).isEqualTo(totalPuntos);
        assertThat(stored.path("fetchedAt").asText()).isEqualTo(fetchedAt.toString());
        assertThat(stored.path("source").asText()).isEqualTo(FuentePuntosLicencia.ANT.name());
        assertThat(remainingTtlMs).isPositive().isLessThanOrEqualTo(TTL.toMillis());
    }
}
