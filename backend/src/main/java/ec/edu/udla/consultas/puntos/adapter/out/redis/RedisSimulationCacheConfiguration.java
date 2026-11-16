package ec.edu.udla.consultas.puntos.adapter.out.redis;

import com.fasterxml.jackson.databind.ObjectMapper;
import ec.edu.udla.consultas.puntos.application.port.out.PuntosLicenciaSimulationCachePort;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.core.StringRedisTemplate;

/** Registers only the cache namespace reserved for local synthetic results. */
@Configuration(proxyBeanMethods = false)
@Profile("local-simulation")
@ConditionalOnProperty(prefix = "app.cache.redis", name = "enabled", havingValue = "true")
@ConditionalOnProperty(prefix = "app.ant.simulation", name = "enabled", havingValue = "true")
public class RedisSimulationCacheConfiguration {

    @Bean
    public PuntosLicenciaSimulationCachePort puntosLicenciaSimulationCachePort(
            StringRedisTemplate redis,
            ObjectMapper objectMapper,
            RedisCacheProperties properties) {
        properties.validateForEnabledCache();
        return new RedisSimulatedPuntosLicenciaCacheAdapter(
                redis,
                objectMapper,
                new HmacCacheKeyFactory(
                        properties.getHmacSecret(), HmacCacheKeyFactory.SIMULATION_KEY_PREFIX),
                properties.getTtl());
    }
}
