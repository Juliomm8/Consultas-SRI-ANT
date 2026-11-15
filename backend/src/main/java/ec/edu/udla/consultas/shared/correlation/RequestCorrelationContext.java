package ec.edu.udla.consultas.shared.correlation;

import org.slf4j.MDC;

/** Shared MDC key for correlating a synchronous request across application layers. */
public final class RequestCorrelationContext {

    public static final String REQUEST_ID_MDC_KEY = "requestId";

    private RequestCorrelationContext() {
    }

    public static String currentRequestId() {
        String requestId = MDC.get(REQUEST_ID_MDC_KEY);
        return requestId == null || requestId.isBlank() ? "unavailable" : requestId;
    }
}
