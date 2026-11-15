package ec.edu.udla.consultas.shared.integration.sri;

import com.fasterxml.jackson.databind.JsonNode;
import ec.edu.udla.consultas.shared.api.RequestIdFilter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.net.URI;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withNoContent;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

@ExtendWith(OutputCaptureExtension.class)
class SriResponseSupportTests {

    private static final URI TEST_URI = URI.create("https://sri.test/taxpayer?numeroRuc=1234567890123"
            + "&email=log-privacy@example.invalid&placa=ZZZ-9876&token=synthetic-token-do-not-log");
    private static final String TEST_IDENTIFIER = "1234567890123";
    private static final String TEST_EMAIL = "log-privacy@example.invalid";
    private static final String TEST_PLATE = "ZZZ-9876";
    private static final String TEST_TOKEN = "synthetic-token-do-not-log";
    private static final String TEST_SENSITIVE_URL = TEST_URI.toString();
    private static final String PRIVATE_BODY_MARKER = "synthetic-private-provider-body";

    private MockRestServiceServer server;
    private RestClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = builder.build();
    }

    @Test
    void logsSafeResponseMetadataAndPropagatesRequestIdFromFilter(CapturedOutput output) throws Exception {
        server.expect(requestTo(TEST_URI))
                .andRespond(withSuccess("true", MediaType.APPLICATION_JSON));
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/contribuyentes/consultas");
        MockHttpServletResponse response = new MockHttpServletResponse();

        new RequestIdFilter().doFilter(request, response, (servletRequest, servletResponse) -> {
            JsonNode result = SriResponseSupport.getJson(
                    client, TEST_URI, SriResponseSupport.Operation.TAXPAYER_EXISTENCE);
            assertTrue(result.isBoolean());
        });

        String requestId = response.getHeader(RequestIdFilter.HEADER_NAME);
        assertTrue(requestId != null && !requestId.isBlank());
        assertTrue(output.toString().contains("operation=taxpayer-existence"));
        assertTrue(output.toString().contains("requestId=" + requestId));
        assertTrue(output.toString().contains("httpStatus=200"));
        assertTrue(output.toString().contains("contentType=application/json"));
        assertTrue(output.toString().contains("jsonRootType=BOOLEAN"));
        assertNoSensitiveMarkers(output.toString());
        server.verify();
    }

    @Test
    void rejectsAnEmptySuccessfulResponseAndLogsItsMetadata(CapturedOutput output) {
        server.expect(requestTo(TEST_URI))
                .andRespond(withSuccess("", MediaType.APPLICATION_JSON));

        SriIntegrationException exception = assertThrows(
                SriIntegrationException.class,
                () -> SriResponseSupport.getJson(client, TEST_URI, SriResponseSupport.Operation.TAXPAYER_EXISTENCE));

        assertEquals(SriIntegrationException.Kind.INVALID_RESPONSE, exception.kind());
        assertTrue(output.toString().contains("outcome=empty_body"));
        assertTrue(output.toString().contains("httpStatus=200"));
        assertNoSensitiveMarkers(output.toString());
        server.verify();
    }

    @Test
    void rejectsHttp204AsAnEmptyResponse(CapturedOutput output) {
        server.expect(requestTo(TEST_URI)).andRespond(withNoContent());

        SriIntegrationException exception = assertThrows(
                SriIntegrationException.class,
                () -> SriResponseSupport.getJson(client, TEST_URI, SriResponseSupport.Operation.TAXPAYER_DETAILS));

        assertEquals(SriIntegrationException.Kind.INVALID_RESPONSE, exception.kind());
        assertTrue(output.toString().contains("outcome=empty_body"));
        assertTrue(output.toString().contains("httpStatus=204"));
        server.verify();
    }

    @Test
    void rejectsMalformedJsonWithoutLoggingTheBody(CapturedOutput output) {
        server.expect(requestTo(TEST_URI))
                .andRespond(withSuccess("{malformed " + PRIVATE_BODY_MARKER, MediaType.APPLICATION_JSON));

        SriIntegrationException exception = assertThrows(
                SriIntegrationException.class,
                () -> SriResponseSupport.getJson(client, TEST_URI, SriResponseSupport.Operation.TAXPAYER_DETAILS));

        assertEquals(SriIntegrationException.Kind.INVALID_RESPONSE, exception.kind());
        assertNull(exception.getCause());
        assertTrue(output.toString().contains("outcome=malformed_json"));
        assertTrue(output.toString().contains("exceptionType="));
        assertNoSensitiveMarkers(output.toString());
        server.verify();
    }

    @Test
    void rejectsUnexpectedContentTypeWithoutLoggingTheBody(CapturedOutput output) {
        server.expect(requestTo(TEST_URI))
                .andRespond(withSuccess("<html>" + PRIVATE_BODY_MARKER + "</html>", MediaType.TEXT_HTML));

        SriIntegrationException exception = assertThrows(
                SriIntegrationException.class,
                () -> SriResponseSupport.getJson(client, TEST_URI, SriResponseSupport.Operation.TAXPAYER_DETAILS));

        assertEquals(SriIntegrationException.Kind.INVALID_RESPONSE, exception.kind());
        assertTrue(output.toString().contains("outcome=unexpected_content_type"));
        assertTrue(output.toString().contains("contentType=text/html"));
        assertNoSensitiveMarkers(output.toString());
        server.verify();
    }

    @Test
    void mapsHttpProviderErrorsAndLogsOnlySafeMetadata(CapturedOutput output) {
        for (int status : new int[] {502, 503, 504}) {
            RestClient.Builder builder = RestClient.builder();
            server = MockRestServiceServer.bindTo(builder).build();
            client = builder.build();
            server.expect(requestTo(TEST_URI))
                    .andRespond(withStatus(HttpStatusCode.valueOf(status))
                            .contentType(MediaType.TEXT_HTML)
                    .body(PRIVATE_BODY_MARKER + TEST_IDENTIFIER + TEST_EMAIL + TEST_PLATE + TEST_TOKEN
                            + TEST_SENSITIVE_URL));

            SriIntegrationException exception = assertThrows(
                    SriIntegrationException.class,
                    () -> SriResponseSupport.getJson(client, TEST_URI,
                            SriResponseSupport.Operation.TAXPAYER_DETAILS));

            assertEquals(SriIntegrationException.Kind.HTTP_ERROR, exception.kind());
            assertEquals(status, exception.upstreamStatus());
            assertTrue(output.toString().contains("outcome=http_error"));
            assertTrue(output.toString().contains("httpStatus=" + status));
            assertTrue(output.toString().contains("contentType=text/html"));
            server.verify();
        }
        assertNoSensitiveMarkers(output.toString());
    }

    @Test
    void classifiesTimeoutAndDoesNotLogItsExceptionMessage(CapturedOutput output) {
        server.expect(requestTo(TEST_URI))
                .andRespond(withException(new SocketTimeoutException("synthetic timeout " + TEST_IDENTIFIER)));

        SriIntegrationException exception = assertThrows(
                SriIntegrationException.class,
                () -> SriResponseSupport.getJson(client, TEST_URI, SriResponseSupport.Operation.TAXPAYER_DETAILS));

        assertEquals(SriIntegrationException.Kind.TIMEOUT, exception.kind());
        assertTrue(output.toString().contains("outcome=timeout"));
        assertTrue(output.toString().contains("exceptionType=ResourceAccessException"));
        assertNoSensitiveMarkers(output.toString());
        server.verify();
    }

    @Test
    void classifiesConnectionFailureAndDoesNotRetainTheClientException(CapturedOutput output) {
        server.expect(requestTo(TEST_URI))
                .andRespond(withException(new IOException("synthetic connection failure " + TEST_IDENTIFIER)));

        SriIntegrationException exception = assertThrows(
                SriIntegrationException.class,
                () -> SriResponseSupport.getJson(client, TEST_URI, SriResponseSupport.Operation.TAXPAYER_DETAILS));

        assertEquals(SriIntegrationException.Kind.UNAVAILABLE, exception.kind());
        assertNull(exception.getCause());
        assertTrue(output.toString().contains("outcome=unavailable"));
        assertTrue(output.toString().contains("exceptionType=ResourceAccessException"));
        assertNoSensitiveMarkers(output.toString());
        server.verify();
    }

    @Test
    void sanitizesCallerSuppliedDiagnosticLabels(CapturedOutput output) {
        String sensitiveLabel = TEST_SENSITIVE_URL + " " + TEST_EMAIL + " " + TEST_PLATE + " " + TEST_TOKEN;

        SriResponseSupport.logUnexpectedStructure(
                SriResponseSupport.Operation.TAXPAYER_DETAILS, sensitiveLabel, null);
        SriResponseSupport.logTaxpayerClassification(sensitiveLabel, false);

        assertTrue(output.toString().contains("reason=unspecified"));
        assertTrue(output.toString().contains("fieldState=unknown"));
        assertNoSensitiveMarkers(output.toString());
    }

    @Test
    void classifiesTimeoutWithoutKeepingTheSensitiveClientException() {
        ResourceAccessException clientFailure = new ResourceAccessException(
                "synthetic request with private query",
                new SocketTimeoutException("synthetic timeout"));

        SriIntegrationException sanitized = SriResponseSupport.accessFailure(clientFailure);

        assertEquals(SriIntegrationException.Kind.TIMEOUT, sanitized.kind());
        assertNull(sanitized.getCause());
        assertEquals(
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

    private static void assertNoSensitiveMarkers(String output) {
        assertFalse(output.contains(TEST_IDENTIFIER));
        assertFalse(output.contains(TEST_EMAIL));
        assertFalse(output.contains(TEST_PLATE));
        assertFalse(output.contains(TEST_TOKEN));
        assertFalse(output.contains(TEST_SENSITIVE_URL));
        assertFalse(output.contains(PRIVATE_BODY_MARKER));
    }
}
