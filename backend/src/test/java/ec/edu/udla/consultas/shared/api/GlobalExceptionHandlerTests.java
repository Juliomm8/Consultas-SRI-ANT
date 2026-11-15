package ec.edu.udla.consultas.shared.api;

import com.jayway.jsonpath.JsonPath;
import ec.edu.udla.consultas.shared.integration.sri.SriIntegrationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = ApiErrorTestController.class)
@Import({GlobalExceptionHandler.class, RequestIdFilter.class})
@ExtendWith(OutputCaptureExtension.class)
class GlobalExceptionHandlerTests {

    private static final String REQUEST_ID_HEADER = RequestIdFilter.HEADER_NAME;

    @Autowired
    private MockMvc mockMvc;

    @Test
    void validationErrorHasUniformBodyAndServerGeneratedRequestId() throws Exception {
        String clientRequestId = "client-request-id";
        MvcResult result = mockMvc.perform(post("/__test/api/contribuyentes")
                        .header(REQUEST_ID_HEADER, clientRequestId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "no-es-correo",
                                  "ruc": "0000000000000"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.error.message").value("Los datos enviados no son válidos."))
                .andExpect(jsonPath("$.error.requestId").isNotEmpty())
                .andExpect(jsonPath("$.trace").doesNotExist())
                .andReturn();

        String body = result.getResponse().getContentAsString();
        assertFalse(body.contains("no-es-correo"));
        assertFalse(body.contains("0000000000000"));
        assertRequestIdMatchesHeader(result, clientRequestId);
    }

    @Test
    void malformedJsonReturnsBadRequestWithRequestId() throws Exception {
        MvcResult result = mockMvc.perform(post("/__test/api/contribuyentes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("MALFORMED_JSON"))
                .andExpect(jsonPath("$.error.message").value("El cuerpo debe contener JSON válido."))
                .andExpect(jsonPath("$.error.requestId").isNotEmpty())
                .andReturn();

        assertRequestIdMatchesHeader(result, null);
    }

    @Test
    void unexpectedErrorReturnsSanitizedInternalErrorAndLogsOnlySafeCauseMetadata(CapturedOutput output)
            throws Exception {
        MvcResult result = mockMvc.perform(get("/__test/api/failure"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.error.message").value("Ocurrió un error interno. Inténtalo más tarde."))
                .andExpect(jsonPath("$.error.requestId").isNotEmpty())
                .andExpect(jsonPath("$.trace").doesNotExist())
                .andReturn();

        String body = result.getResponse().getContentAsString();
        assertFalse(body.contains("synthetic servlet wrapper"));
        assertFalse(body.contains("synthetic application failure"));
        assertFalse(body.contains("synthetic root cause"));
        assertFalse(body.contains("ServletException"));
        assertFalse(body.contains("IllegalStateException"));
        assertNoSensitiveMarkers(body);
        assertRequestIdMatchesHeader(result, null);

        String requestId = result.getResponse().getHeader(REQUEST_ID_HEADER);
        assertNotNull(requestId);
        String logs = output.toString();
        assertTrue(logs.contains("Unexpected API error; requestId=" + requestId));
        assertTrue(logs.contains("operation=unmapped"));
        assertTrue(logs.contains("errorCode=INTERNAL_ERROR"));
        assertTrue(logs.contains("httpStatus=500"));
        assertTrue(logs.contains("exceptionTypes=[ServletException, IllegalStateException, IllegalArgumentException]"));
        assertTrue(logs.contains("locationClass=ec.edu.udla.consultas.shared.api.ApiErrorTestController"));
        assertTrue(logs.contains("locationMethod=failUnexpectedly"));
        assertFalse(logs.contains("Caused by:"));
        assertFalse(logs.contains("synthetic servlet wrapper"));
        assertFalse(logs.contains("synthetic application failure"));
        assertFalse(logs.contains("synthetic root cause"));
        assertNoSensitiveMarkers(logs);
    }

    @Test
    void knownOperationUsesAllowlistedLabelWithoutLoggingExceptionMessage(CapturedOutput output) {
        String requestId = UUID.randomUUID().toString();
        MockHttpServletRequest request = new MockHttpServletRequest(
                "POST", "/api/v1/contribuyentes/consultas");
        request.setAttribute(RequestIdFilter.REQUEST_ID_ATTRIBUTE, requestId);

        var response = new GlobalExceptionHandler().handleUnexpectedError(
                new IllegalStateException("failure details " + ApiErrorTestController.SENSITIVE_URL), request);

        assertEquals(500, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals("INTERNAL_ERROR", response.getBody().error().code());
        assertEquals(requestId, response.getBody().error().requestId());
        String logs = output.toString();
        assertTrue(logs.contains("requestId=" + requestId));
        assertTrue(logs.contains("operation=taxpayer-consultation"));
        assertTrue(logs.contains("exceptionTypes=[IllegalStateException]"));
        assertFalse(logs.contains("failure details"));
        assertNoSensitiveMarkers(logs);
        assertNoSensitiveMarkers(response.getBody().toString());
    }

    @Test
    void providerFailureLogsStableCodeAndUpstreamStatus(CapturedOutput output) {
        String requestId = UUID.randomUUID().toString();
        MockHttpServletRequest request = new MockHttpServletRequest(
                "POST", "/api/v1/contribuyentes/consultas");
        request.setAttribute(RequestIdFilter.REQUEST_ID_ATTRIBUTE, requestId);

        var response = new GlobalExceptionHandler().handleSriFailure(
                SriIntegrationException.httpError(502), request);

        assertEquals(502, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals("PROVIDER_BAD_RESPONSE", response.getBody().error().code());
        assertEquals(requestId, response.getBody().error().requestId());
        assertTrue(output.toString().contains("requestId=" + requestId));
        assertTrue(output.toString().contains("errorCode=PROVIDER_BAD_RESPONSE"));
        assertTrue(output.toString().contains("kind=HTTP_ERROR"));
        assertTrue(output.toString().contains("upstreamStatus=502"));
        assertNoSensitiveMarkers(output.toString());
        assertNoSensitiveMarkers(response.getBody().toString());
    }

    private static void assertNoSensitiveMarkers(String value) {
        assertFalse(value.contains(ApiErrorTestController.SYNTHETIC_RUC));
        assertFalse(value.contains(ApiErrorTestController.SYNTHETIC_EMAIL));
        assertFalse(value.contains(ApiErrorTestController.SYNTHETIC_PLATE));
        assertFalse(value.contains(ApiErrorTestController.SYNTHETIC_TOKEN));
        assertFalse(value.contains(ApiErrorTestController.SENSITIVE_URL));
    }

    private void assertRequestIdMatchesHeader(MvcResult result, String clientRequestId) throws Exception {
        String headerRequestId = result.getResponse().getHeader(REQUEST_ID_HEADER);
        String bodyRequestId = JsonPath.read(result.getResponse().getContentAsString(), "$.error.requestId");

        assertEquals(1, result.getResponse().getHeaders(REQUEST_ID_HEADER).size());
        assertNotNull(headerRequestId);
        assertEquals(headerRequestId, bodyRequestId);
        assertDoesNotThrow(() -> UUID.fromString(bodyRequestId));
        if (clientRequestId != null) {
            assertNotEquals(clientRequestId, headerRequestId);
        }
    }
}
