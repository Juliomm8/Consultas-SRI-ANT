package ec.edu.udla.consultas.puntos.adapter.out.redis;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import ec.edu.udla.consultas.puntos.application.exception.PuntosLicenciaCacheException;
import ec.edu.udla.consultas.puntos.application.port.out.PuntosLicenciaCachePort;
import ec.edu.udla.consultas.puntos.domain.Cedula;
import ec.edu.udla.consultas.puntos.domain.FuentePuntosLicencia;
import ec.edu.udla.consultas.puntos.domain.ResultadoPuntosLicencia;
import ec.edu.udla.consultas.shared.correlation.RequestCorrelationContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/** Redis adapter for the normalized license-points cache contract. */
public final class RedisPuntosLicenciaCacheAdapter implements PuntosLicenciaCachePort {
    private static final Logger LOGGER = LoggerFactory.getLogger(RedisPuntosLicenciaCacheAdapter.class);

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final HmacCacheKeyFactory keyFactory;
    private final Duration ttl;

    RedisPuntosLicenciaCacheAdapter(
            StringRedisTemplate redis,
            ObjectMapper objectMapper,
            HmacCacheKeyFactory keyFactory,
            Duration ttl) {
        this.redis = Objects.requireNonNull(redis, "La plantilla Redis es obligatoria.");
        this.objectMapper = Objects.requireNonNull(objectMapper, "El serializador JSON es obligatorio.");
        this.keyFactory = Objects.requireNonNull(keyFactory, "El generador de claves es obligatorio.");
        if (ttl == null || ttl.isZero() || ttl.isNegative()) {
            throw new IllegalArgumentException("El TTL de caché debe ser positivo.");
        }
        this.ttl = ttl;
    }

    @Override
    public void guardarResultado(Cedula cedula, ResultadoPuntosLicencia resultado) {
        Objects.requireNonNull(cedula, "La cédula es obligatoria.");
        Objects.requireNonNull(resultado, "El resultado es obligatorio.");
        if (resultado.fuente() != FuentePuntosLicencia.ANT) {
            throw new PuntosLicenciaCacheException(PuntosLicenciaCacheException.Kind.INVALID_RESULT);
        }
        String key = keyFactory.keyFor(cedula);
        String document;
        try {
            document = objectMapper.writeValueAsString(new RedisDocument(
                    resultado.totalPuntos(),
                    resultado.fetchedAt().toString(),
                    resultado.fuente().name()));
        } catch (JsonProcessingException exception) {
            logFailure("write", "serialization_failure", exception);
            throw new PuntosLicenciaCacheException(
                    PuntosLicenciaCacheException.Kind.SERIALIZATION_FAILURE);
        }

        try {
            redis.opsForValue().set(key, document, ttl);
        } catch (DataAccessException exception) {
            logFailure("write", "unavailable", exception);
            throw new PuntosLicenciaCacheException(PuntosLicenciaCacheException.Kind.UNAVAILABLE);
        }
    }

    @Override
    public Optional<ResultadoPuntosLicencia> obtenerUltimoResultado(Cedula cedula) {
        String key = keyFactory.keyFor(cedula);
        String document;
        try {
            document = redis.opsForValue().get(key);
        } catch (DataAccessException exception) {
            logFailure("read", "unavailable", exception);
            throw new PuntosLicenciaCacheException(PuntosLicenciaCacheException.Kind.UNAVAILABLE);
        }
        if (document == null) {
            return Optional.empty();
        }

        try {
            JsonNode root = objectMapper.readTree(document);
            if (root == null || !root.isObject() || root.size() != 3
                    || !root.has("totalPuntos") || !root.has("fetchedAt") || !root.has("source")) {
                throw new IllegalArgumentException("Entrada de caché incompatible.");
            }
            JsonNode totalPuntos = root.get("totalPuntos");
            JsonNode fetchedAtNode = root.get("fetchedAt");
            JsonNode sourceNode = root.get("source");
            if (totalPuntos == null || !totalPuntos.isIntegralNumber() || !totalPuntos.canConvertToInt()
                    || totalPuntos.intValue() < 0 || fetchedAtNode == null || !fetchedAtNode.isTextual()
                    || sourceNode == null || !sourceNode.isTextual()
                    || !FuentePuntosLicencia.ANT.name().equals(sourceNode.textValue())) {
                throw new IllegalArgumentException("Entrada de caché incompatible.");
            }
            Instant fetchedAt = Instant.parse(fetchedAtNode.textValue());
            return Optional.of(new ResultadoPuntosLicencia(
                    totalPuntos.intValue(), fetchedAt, FuentePuntosLicencia.CACHE));
        } catch (JsonProcessingException | IllegalArgumentException exception) {
            logFailure("read", "invalid_entry", exception);
            throw new PuntosLicenciaCacheException(PuntosLicenciaCacheException.Kind.INVALID_ENTRY);
        }
    }

    private static void logFailure(String operation, String outcome, Exception exception) {
        LOGGER.warn(
                "Redis points cache operation failed; requestId={}, operation={}, outcome={}, exceptionType={}",
                RequestCorrelationContext.currentRequestId(),
                operation,
                outcome,
                exception.getClass().getSimpleName());
    }

    public record RedisDocument(Integer totalPuntos, String fetchedAt, String source) {

        @Override
        public String toString() {
            return "RedisDocument[totalPuntos=<redacted>, fetchedAt=" + fetchedAt
                    + ", source=" + source + "]";
        }
    }
}
