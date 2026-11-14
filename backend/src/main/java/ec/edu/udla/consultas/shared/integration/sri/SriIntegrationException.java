package ec.edu.udla.consultas.shared.integration.sri;

/** A sanitized technical failure from an SRI HTTP integration. */
public final class SriIntegrationException extends RuntimeException {

    public enum Kind {
        TIMEOUT,
        UNAVAILABLE,
        HTTP_ERROR,
        INVALID_RESPONSE
    }

    private final Kind kind;
    private final Integer upstreamStatus;

    private SriIntegrationException(Kind kind, Integer upstreamStatus) {
        super(messageFor(kind));
        this.kind = kind;
        this.upstreamStatus = upstreamStatus;
    }

    public static SriIntegrationException timeout() {
        return new SriIntegrationException(Kind.TIMEOUT, null);
    }

    public static SriIntegrationException unavailable() {
        return new SriIntegrationException(Kind.UNAVAILABLE, null);
    }

    public static SriIntegrationException httpError(int status) {
        return new SriIntegrationException(Kind.HTTP_ERROR, status);
    }

    public static SriIntegrationException invalidResponse() {
        return new SriIntegrationException(Kind.INVALID_RESPONSE, null);
    }

    public Kind kind() {
        return kind;
    }

    public Integer upstreamStatus() {
        return upstreamStatus;
    }

    private static String messageFor(Kind kind) {
        return switch (kind) {
            case TIMEOUT -> "El servicio SRI excedió el tiempo de respuesta.";
            case UNAVAILABLE -> "No fue posible comunicarse con el servicio SRI.";
            case HTTP_ERROR -> "El servicio SRI devolvió una respuesta HTTP inesperada.";
            case INVALID_RESPONSE -> "El servicio SRI devolvió una respuesta inválida.";
        };
    }
}
