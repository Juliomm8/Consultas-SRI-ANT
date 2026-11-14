package ec.edu.udla.consultas.shared.api;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = ApiErrorTestController.class)
@Import({GlobalExceptionHandler.class, RequestIdFilter.class})
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
    void unexpectedErrorReturnsSanitizedInternalError() throws Exception {
        MvcResult result = mockMvc.perform(get("/__test/api/failure"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.error.message").value("Ocurrió un error interno. Inténtalo más tarde."))
                .andExpect(jsonPath("$.error.requestId").isNotEmpty())
                .andExpect(jsonPath("$.trace").doesNotExist())
                .andReturn();

        String body = result.getResponse().getContentAsString();
        assertFalse(body.contains("PRIVATE-DETAIL-MUST-NOT-LEAK"));
        assertFalse(body.contains("IllegalStateException"));
        assertRequestIdMatchesHeader(result, null);
    }

    private void assertRequestIdMatchesHeader(MvcResult result, String clientRequestId) throws Exception {
        String headerRequestId = result.getResponse().getHeader(REQUEST_ID_HEADER);
        String bodyRequestId = JsonPath.read(result.getResponse().getContentAsString(), "$.error.requestId");

        assertNotNull(headerRequestId);
        assertEquals(headerRequestId, bodyRequestId);
        assertDoesNotThrow(() -> UUID.fromString(bodyRequestId));
        if (clientRequestId != null) {
            assertNotEquals(clientRequestId, headerRequestId);
        }
    }
}
