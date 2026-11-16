package ec.edu.udla.consultas.puntos.adapter.out.redis;

import com.fasterxml.jackson.databind.ObjectMapper;
import ec.edu.udla.consultas.puntos.application.port.out.PuntosLicenciaCachePort;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.connection.RedisPassword;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "app.cache.redis", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(RedisCacheProperties.class)
public class RedisInfrastructureConfiguration {

    @Bean
    public LettuceConnectionFactory pointsRedisConnectionFactory(RedisCacheProperties properties) {
        properties.validateForEnabledCache();

        RedisStandaloneConfiguration redisServer = new RedisStandaloneConfiguration(
                properties.getHost(), properties.getPort());
        if (properties.getUsername() != null && !properties.getUsername().isBlank()) {
            redisServer.setUsername(properties.getUsername());
        }
        if (properties.getPassword() != null && !properties.getPassword().isBlank()) {
            redisServer.setPassword(RedisPassword.of(properties.getPassword()));
        }

        var clientBuilder = org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration
                .builder();
        if (properties.isSslEnabled()) {
            clientBuilder.useSsl();
        }
        return new LettuceConnectionFactory(redisServer, clientBuilder.build());
    }

    @Bean(name = {"redisTemplate", "pointsStringRedisTemplate"})
    public StringRedisTemplate pointsStringRedisTemplate(LettuceConnectionFactory connectionFactory) {
        return new StringRedisTemplate(connectionFactory);
    }

    @Bean
    @Profile("!local-simulation")
    public PuntosLicenciaCachePort puntosLicenciaCachePort(
            StringRedisTemplate redis,
            ObjectMapper objectMapper,
            RedisCacheProperties properties) {
        return new RedisPuntosLicenciaCacheAdapter(
                redis,
                objectMapper,
                new HmacCacheKeyFactory(properties.getHmacSecret()),
                properties.getTtl());
    }
}
