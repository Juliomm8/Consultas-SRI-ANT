package ec.edu.udla.consultas.puntos.application.exception;

/** Safe technical failure raised by a license-points cache port. */
public final class PuntosLicenciaCacheException extends RuntimeException {

    public enum Kind {
        UNAVAILABLE,
        INVALID_RESULT,
        INVALID_ENTRY,
        SERIALIZATION_FAILURE
    }

    private final Kind kind;

    public PuntosLicenciaCacheException(Kind kind) {
        super(safeMessage(kind));
        this.kind = kind;
    }

    public Kind kind() {
        return kind;
    }

    private static String safeMessage(Kind kind) {
        if (kind == null) {
            return "La caché de puntos de licencia no pudo procesar la solicitud.";
        }
        return switch (kind) {
            case UNAVAILABLE -> "La caché de puntos de licencia no está disponible.";
            case INVALID_RESULT -> "El resultado no cumple el formato interno normalizado.";
            case INVALID_ENTRY -> "La entrada de caché de puntos de licencia no es válida.";
            case SERIALIZATION_FAILURE -> "No se pudo procesar el resultado de la caché.";
        };
    }
}
