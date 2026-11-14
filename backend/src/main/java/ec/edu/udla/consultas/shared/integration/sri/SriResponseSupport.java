package ec.edu.udla.consultas.shared.integration.sri;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.net.SocketTimeoutException;
import java.net.http.HttpTimeoutException;
import java.util.Objects;

/** Shared, privacy-safe HTTP response handling for SRI adapters. */
public final class SriResponseSupport {

    private SriResponseSupport() {
    }

    public static JsonNode getJson(RestClient client, java.net.URI uri) {
        Objects.requireNonNull(client, "El cliente HTTP es obligatorio.");
        Objects.requireNonNull(uri, "El endpoint es obligatorio.");

        try {
            JsonNode body = client.get()
                    .uri(uri)
                    .accept(org.springframework.http.MediaType.APPLICATION_JSON)
                    .retrieve()
                    .body(JsonNode.class);
            if (body == null) {
                throw SriIntegrationException.invalidResponse();
            }
            return body;
        } catch (SriIntegrationException exception) {
            throw exception;
        } catch (RestClientResponseException exception) {
            throw SriIntegrationException.httpError(exception.getStatusCode().value());
        } catch (ResourceAccessException exception) {
            throw accessFailure(exception);
        } catch (RestClientException exception) {
            // Do not retain a cause: HTTP client/parser exceptions may contain the full URI or body.
            throw SriIntegrationException.invalidResponse();
        }
    }

    public static SriIntegrationException accessFailure(ResourceAccessException exception) {
        Objects.requireNonNull(exception, "La excepción de acceso es obligatoria.");
        return isTimeout(exception)
                ? SriIntegrationException.timeout()
                : SriIntegrationException.unavailable();
    }

    private static boolean isTimeout(Throwable throwable) {
        for (Throwable current = throwable; current != null; current = current.getCause()) {
            if (current instanceof SocketTimeoutException || current instanceof HttpTimeoutException) {
                return true;
            }
        }
        return false;
    }
}
