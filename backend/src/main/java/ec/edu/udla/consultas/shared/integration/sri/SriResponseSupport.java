package ec.edu.udla.consultas.shared.integration.sri;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import ec.edu.udla.consultas.shared.correlation.RequestCorrelationContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.InvalidMediaTypeException;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.io.IOException;
import java.net.URI;
import java.net.SocketTimeoutException;
import java.net.http.HttpTimeoutException;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/** Shared, privacy-safe HTTP response handling and diagnostics for SRI adapters. */
public final class SriResponseSupport {

    private static final Logger LOGGER = LoggerFactory.getLogger(SriResponseSupport.class);
    private static final ObjectMapper JSON_MAPPER = new ObjectMapper();
    private static final Set<String> STRUCTURE_REASONS = Set.of(
            "expected_boolean",
            "object_record_identifier_missing_or_mismatched",
            "expected_object_or_array",
            "empty_details_array_after_positive_existence",
            "array_element_not_object",
            "array_record_identifier_missing_or_invalid",
            "multiple_records_match_requested_identifier",
            "no_record_matches_requested_identifier",
            "unsupported_json_value_type",
            "expected_object",
            "expected_data_array",
            "expected_vehicle_object",
            "required_vehicle_code_missing");
    private static final Set<String> CLASSIFICATION_STATES = Set.of(
            "missing", "null", "non_textual", "confirmed_natural", "unrecognized_text");

    public enum Operation {
        TAXPAYER_EXISTENCE("taxpayer-existence"),
        TAXPAYER_DETAILS("taxpayer-details"),
        VEHICLE_LOOKUP("vehicle-lookup");

        private final String label;

        Operation(String label) {
            this.label = label;
        }
    }

    private SriResponseSupport() {
    }

    public static JsonNode getJson(RestClient client, URI uri, Operation operation) {
        Objects.requireNonNull(client, "El cliente HTTP es obligatorio.");
        Objects.requireNonNull(uri, "El endpoint es obligatorio.");
        Objects.requireNonNull(operation, "La operación SRI es obligatoria.");

        long startedAt = System.nanoTime();
        try {
            ResponseEntity<byte[]> response = client.get()
                    .uri(uri)
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .toEntity(byte[].class);

            int status = response.getStatusCode().value();
            String contentType = contentTypeLabel(response.getHeaders());
            long elapsedMs = elapsedMillis(startedAt);
            if (!response.getStatusCode().is2xxSuccessful()) {
                logFailure(operation, "http_error", status, contentType, elapsedMs,
                        "none");
                throw SriIntegrationException.httpError(status);
            }

            byte[] responseBody = response.getBody();
            if (responseBody == null || responseBody.length == 0) {
                logFailure(operation, "empty_body", status, contentType, elapsedMs,
                        "none");
                throw SriIntegrationException.invalidResponse();
            }
            if (!isJsonContentType(response.getHeaders())) {
                logFailure(operation, "unexpected_content_type", status, contentType, elapsedMs,
                        "none");
                throw SriIntegrationException.invalidResponse();
            }

            JsonNode body;
            try {
                body = JSON_MAPPER.readTree(responseBody);
            } catch (IOException exception) {
                logFailure(operation, "malformed_json", status, contentType, elapsedMs,
                        exception.getClass().getSimpleName());
                throw SriIntegrationException.invalidResponse();
            }
            if (body == null) {
                logFailure(operation, "empty_body", status, contentType, elapsedMs,
                        "none");
                throw SriIntegrationException.invalidResponse();
            }

            LOGGER.info(
                    "SRI response received; operation={}, requestId={}, httpStatus={}, contentType={}, "
                            + "elapsedMs={}, jsonRootType={}",
                    operation.label,
                    RequestCorrelationContext.currentRequestId(),
                    status,
                    contentType,
                    elapsedMs,
                    body.getNodeType().name());
            return body;
        } catch (SriIntegrationException exception) {
            throw exception;
        } catch (RestClientResponseException exception) {
            int status = exception.getStatusCode().value();
            String contentType = contentTypeLabel(exception.getResponseHeaders());
            logFailure(operation, "http_error", status, contentType, elapsedMillis(startedAt),
                    exception.getClass().getSimpleName());
            throw SriIntegrationException.httpError(status);
        } catch (ResourceAccessException exception) {
            SriIntegrationException failure = accessFailure(exception);
            logFailure(operation, failure.kind().name().toLowerCase(Locale.ROOT), null, "unavailable",
                    elapsedMillis(startedAt), exception.getClass().getSimpleName());
            throw failure;
        } catch (RestClientException exception) {
            logFailure(operation, "client_error", null, "unavailable", elapsedMillis(startedAt),
                    exception.getClass().getSimpleName());
            throw SriIntegrationException.invalidResponse();
        }
    }

    public static void logUnexpectedStructure(Operation operation, String reason, JsonNode actual) {
        Objects.requireNonNull(operation, "La operación SRI es obligatoria.");
        String safeReason = reason != null && STRUCTURE_REASONS.contains(reason) ? reason : "unspecified";
        String actualType = actual == null ? "MISSING" : actual.getNodeType().name();
        LOGGER.warn(
                "SRI response rejected; operation={}, requestId={}, outcome=unexpected_structure, "
                        + "reason={}, jsonRootType={}, failureType={}",
                operation.label,
                RequestCorrelationContext.currentRequestId(),
                safeReason,
                actualType,
                SriIntegrationException.class.getSimpleName());
    }

    public static void logTaxpayerClassification(String state, boolean confirmedNaturalPerson) {
        String safeState = state != null && CLASSIFICATION_STATES.contains(state) ? state : "unknown";
        LOGGER.info(
                "SRI taxpayer classification evaluated; operation={}, requestId={}, fieldState={}, "
                        + "confirmedNaturalPerson={}",
                Operation.TAXPAYER_DETAILS.label,
                RequestCorrelationContext.currentRequestId(),
                safeState,
                confirmedNaturalPerson);
    }

    public static SriIntegrationException accessFailure(ResourceAccessException exception) {
        Objects.requireNonNull(exception, "La excepción de acceso es obligatoria.");
        return isTimeout(exception)
                ? SriIntegrationException.timeout()
                : SriIntegrationException.unavailable();
    }

    private static void logFailure(
            Operation operation,
            String outcome,
            Integer status,
            String contentType,
            long elapsedMs,
            String exceptionType) {
        LOGGER.warn(
                "SRI response failure; operation={}, requestId={}, outcome={}, httpStatus={}, "
                        + "contentType={}, elapsedMs={}, exceptionType={}, mappedException={}",
                operation.label,
                RequestCorrelationContext.currentRequestId(),
                outcome,
                status == null ? "unavailable" : status,
                contentType,
                elapsedMs,
                exceptionType,
                SriIntegrationException.class.getSimpleName());
    }

    private static String contentTypeLabel(HttpHeaders headers) {
        if (headers == null) {
            return "unavailable";
        }
        try {
            MediaType contentType = headers.getContentType();
            if (contentType == null) {
                return "missing";
            }
            String label = contentType.getType() + "/" + contentType.getSubtype();
            return label.length() <= 80 && label.matches("[A-Za-z0-9!#$&^_.+*-]+/[A-Za-z0-9!#$&^_.+*-]+")
                    ? label.toLowerCase(Locale.ROOT)
                    : "other";
        } catch (InvalidMediaTypeException exception) {
            return "invalid";
        }
    }

    private static boolean isJsonContentType(HttpHeaders headers) {
        try {
            MediaType contentType = headers.getContentType();
            if (contentType == null || !"application".equalsIgnoreCase(contentType.getType())) {
                return false;
            }
            String subtype = contentType.getSubtype().toLowerCase(Locale.ROOT);
            return "json".equals(subtype) || subtype.endsWith("+json");
        } catch (InvalidMediaTypeException exception) {
            return false;
        }
    }

    private static long elapsedMillis(long startedAt) {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);
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
