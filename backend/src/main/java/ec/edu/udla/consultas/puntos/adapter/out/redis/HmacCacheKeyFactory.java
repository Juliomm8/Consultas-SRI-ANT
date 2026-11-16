package ec.edu.udla.consultas.puntos.adapter.out.redis;

import ec.edu.udla.consultas.puntos.domain.Cedula;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.HexFormat;

final class HmacCacheKeyFactory {

    private static final String ALGORITHM = "HmacSHA256";
    static final String ANT_KEY_PREFIX = "consultas-sri-ant:ant-points:v2:";
    static final String SIMULATION_KEY_PREFIX = "consultas-sri-ant:ant-points:simulation:v1:";

    private final byte[] secret;
    private final String keyPrefix;

    HmacCacheKeyFactory(String secret) {
        this(secret, ANT_KEY_PREFIX);
    }

    HmacCacheKeyFactory(String secret, String keyPrefix) {
        if (secret == null || secret.isBlank()) {
            throw new IllegalArgumentException("La clave HMAC es obligatoria.");
        }
        if (keyPrefix == null || keyPrefix.isBlank()) {
            throw new IllegalArgumentException("El namespace de caché es obligatorio.");
        }
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
        this.keyPrefix = keyPrefix;
    }

    String keyFor(Cedula cedula) {
        if (cedula == null) {
            throw new IllegalArgumentException("La cédula es obligatoria.");
        }
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(new SecretKeySpec(secret, ALGORITHM));
            byte[] digest = mac.doFinal(cedula.value().getBytes(StandardCharsets.UTF_8));
            return keyPrefix + HexFormat.of().formatHex(digest);
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("HMAC-SHA-256 no está disponible en este entorno.");
        }
    }
}
