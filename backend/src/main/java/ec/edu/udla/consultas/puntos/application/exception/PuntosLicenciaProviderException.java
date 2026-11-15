package ec.edu.udla.consultas.puntos.application.exception;

import java.util.Objects;

/** Sanitized technical failure raised by a points provider boundary. */
public final class PuntosLicenciaProviderException extends RuntimeException {

    public enum Kind {
        TIMEOUT,
        UNAVAILABLE,
        INVALID_RESPONSE
    }

    private final Kind kind;

    public PuntosLicenciaProviderException(Kind kind) {
        super(safeMessage(kind));
        this.kind = Objects.requireNonNull(kind, "La clasificación técnica es obligatoria.");
    }

    public Kind kind() {
        return kind;
    }

    private static String safeMessage(Kind kind) {
        if (kind == null) {
            return "El servicio de puntos de licencia no pudo procesar la solicitud.";
        }
        return switch (kind) {
            case TIMEOUT -> "El servicio de puntos de licencia excedió el tiempo de espera.";
            case UNAVAILABLE -> "El servicio de puntos de licencia no está disponible.";
            case INVALID_RESPONSE -> "El servicio de puntos de licencia devolvió una respuesta no utilizable.";
        };
    }
}
