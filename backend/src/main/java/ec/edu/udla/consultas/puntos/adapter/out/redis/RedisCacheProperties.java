package ec.edu.udla.consultas.puntos.adapter.out.redis;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

/** Infrastructure settings are only bound when the Redis cache is enabled. */
@ConfigurationProperties(prefix = "app.cache.redis")
public class RedisCacheProperties {

    private boolean enabled;
    private String host = "";
    private int port = 6379;
    private String username = "";
    private String password = "";
    private boolean sslEnabled;
    private String hmacSecret = "";
    private Duration ttl;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public boolean isSslEnabled() {
        return sslEnabled;
    }

    public void setSslEnabled(boolean sslEnabled) {
        this.sslEnabled = sslEnabled;
    }

    public String getHmacSecret() {
        return hmacSecret;
    }

    public void setHmacSecret(String hmacSecret) {
        this.hmacSecret = hmacSecret;
    }

    public Duration getTtl() {
        return ttl;
    }

    public void setTtl(Duration ttl) {
        this.ttl = ttl;
    }

    public void validateForEnabledCache() {
        if (host == null || host.isBlank()) {
            throw new IllegalStateException("Redis habilitado requiere configurar REDIS_HOST.");
        }
        if (port < 1 || port > 65535) {
            throw new IllegalStateException("REDIS_PORT debe estar entre 1 y 65535.");
        }
        if (hmacSecret == null
                || hmacSecret.isBlank()
                || hmacSecret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException(
                    "Redis habilitado requiere REDIS_CACHE_HMAC_SECRET de al menos 32 bytes UTF-8.");
        }
        if (ttl == null || ttl.isZero() || ttl.isNegative()) {
            throw new IllegalStateException("Redis habilitado requiere REDIS_CACHE_TTL positivo.");
        }
    }
}
