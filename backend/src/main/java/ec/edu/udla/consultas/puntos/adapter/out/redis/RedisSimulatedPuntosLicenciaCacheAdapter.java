package ec.edu.udla.consultas.puntos.adapter.out.redis;

import com.fasterxml.jackson.databind.ObjectMapper;
import ec.edu.udla.consultas.puntos.application.exception.PuntosLicenciaCacheException;
import ec.edu.udla.consultas.puntos.application.port.out.PuntosLicenciaSimulationCachePort;
import ec.edu.udla.consultas.puntos.domain.Cedula;
import ec.edu.udla.consultas.puntos.domain.FuentePuntosLicencia;
import ec.edu.udla.consultas.puntos.domain.ResultadoPuntosLicencia;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;

/** Redis adapter dedicated to local simulated results and its independent namespace. */
public final class RedisSimulatedPuntosLicenciaCacheAdapter implements PuntosLicenciaSimulationCachePort {

    private final RedisPuntosLicenciaCacheAdapter delegate;

    RedisSimulatedPuntosLicenciaCacheAdapter(
            StringRedisTemplate redis,
            ObjectMapper objectMapper,
            HmacCacheKeyFactory keyFactory,
            Duration ttl) {
        this.delegate = new RedisPuntosLicenciaCacheAdapter(
                Objects.requireNonNull(redis, "La plantilla Redis es obligatoria."),
                Objects.requireNonNull(objectMapper, "El serializador JSON es obligatorio."),
                Objects.requireNonNull(keyFactory, "El generador de claves es obligatorio."),
                ttl,
                FuentePuntosLicencia.SIMULATED);
    }

    @Override
    public void guardarResultado(Cedula cedula, ResultadoPuntosLicencia resultado) {
        if (resultado == null || resultado.fuente() != FuentePuntosLicencia.SIMULATED
                || resultado.originSource() != FuentePuntosLicencia.SIMULATED) {
            throw new PuntosLicenciaCacheException(PuntosLicenciaCacheException.Kind.INVALID_RESULT);
        }
        delegate.guardarResultado(cedula, resultado);
    }

    @Override
    public Optional<ResultadoPuntosLicencia> obtenerUltimoResultado(Cedula cedula) {
        return delegate.obtenerUltimoResultado(cedula);
    }
}
