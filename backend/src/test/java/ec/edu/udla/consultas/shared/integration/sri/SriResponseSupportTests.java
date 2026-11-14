package ec.edu.udla.consultas.shared.integration.sri;

import org.junit.jupiter.api.Test;
import org.springframework.web.client.ResourceAccessException;

import java.io.IOException;
import java.net.SocketTimeoutException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class SriResponseSupportTests {

    @Test
    void classifiesTimeoutWithoutKeepingTheSensitiveClientException() {
        ResourceAccessException clientFailure = new ResourceAccessException(
                "synthetic request with private query",
                new SocketTimeoutException("synthetic timeout"));

        SriIntegrationException sanitized = SriResponseSupport.accessFailure(clientFailure);

        assertEquals(SriIntegrationException.Kind.TIMEOUT, sanitized.kind());
        assertNull(sanitized.getCause());
        org.junit.jupiter.api.Assertions.assertEquals(
                "El servicio SRI excedió el tiempo de respuesta.",
                sanitized.getMessage());
    }

    @Test
    void classifiesConnectionFailureWithoutKeepingTheSensitiveClientException() {
        ResourceAccessException clientFailure = new ResourceAccessException(
                "synthetic request with private query",
                new IOException("synthetic connection failure"));

        SriIntegrationException sanitized = SriResponseSupport.accessFailure(clientFailure);

        assertEquals(SriIntegrationException.Kind.UNAVAILABLE, sanitized.kind());
        assertNull(sanitized.getCause());
    }
}
