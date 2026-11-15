package ec.edu.udla.consultas.puntos.adapter.out.redis;

import ec.edu.udla.consultas.puntos.domain.Cedula;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.HexFormat;

final class HmacCacheKeyFactory {

    private static final String ALGORITHM = "HmacSHA256";
    private static final String KEY_PREFIX = "consultas-sri-ant:ant-points:v2:";

    private final byte[] secret;

    HmacCacheKeyFactory(String secret) {
        if (secret == null || secret.isBlank()) {
            throw new IllegalArgumentException("La clave HMAC es obligatoria.");
        }
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
    }

    String keyFor(Cedula cedula) {
        if (cedula == null) {
            throw new IllegalArgumentException("La cédula es obligatoria.");
        }
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(new SecretKeySpec(secret, ALGORITHM));
            byte[] digest = mac.doFinal(cedula.value().getBytes(StandardCharsets.UTF_8));
            return KEY_PREFIX + HexFormat.of().formatHex(digest);
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("HMAC-SHA-256 no está disponible en este entorno.");
        }
    }
}
