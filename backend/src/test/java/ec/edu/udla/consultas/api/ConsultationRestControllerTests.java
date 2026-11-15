package ec.edu.udla.consultas.api;

import com.jayway.jsonpath.JsonPath;
import ec.edu.udla.consultas.contribuyentes.adapter.in.rest.ContribuyenteRestController;
import ec.edu.udla.consultas.contribuyentes.application.exception.ContribuyenteNoEncontradoException;
import ec.edu.udla.consultas.contribuyentes.application.exception.ContribuyenteNoEsPersonaNaturalException;
import ec.edu.udla.consultas.contribuyentes.application.exception.TipoPersonaNoDeterminadaException;
import ec.edu.udla.consultas.contribuyentes.application.port.in.ConsultarContribuyenteQuery;
import ec.edu.udla.consultas.contribuyentes.application.port.in.ConsultarContribuyenteUseCase;
import ec.edu.udla.consultas.contribuyentes.domain.Contribuyente;
import ec.edu.udla.consultas.contribuyentes.domain.Ruc;
import ec.edu.udla.consultas.contribuyentes.domain.TipoPersona;
import ec.edu.udla.consultas.contribuyentes.domain.ValorCampoContribuyente;
import ec.edu.udla.consultas.shared.api.GlobalExceptionHandler;
import ec.edu.udla.consultas.shared.api.RequestIdFilter;
import ec.edu.udla.consultas.shared.integration.sri.SriIntegrationException;
import ec.edu.udla.consultas.vehiculos.adapter.in.rest.VehiculoRestController;
import ec.edu.udla.consultas.vehiculos.application.port.in.ConsultarVehiculoQuery;
import ec.edu.udla.consultas.vehiculos.application.port.in.ConsultarVehiculoUseCase;
import ec.edu.udla.consultas.vehiculos.domain.ValorCampoVehiculo;
import ec.edu.udla.consultas.vehiculos.domain.Vehiculo;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {ContribuyenteRestController.class, VehiculoRestController.class})
@Import({GlobalExceptionHandler.class, RequestIdFilter.class})
class ConsultationRestControllerTests {

    private static final String TAXPAYER_PATH = "/api/v1/contribuyentes/consultas";
    private static final String VEHICLE_PATH = "/api/v1/vehiculos/consultas";
    private static final String SYNTHETIC_RUC = "0000000000000";
    private static final String SYNTHETIC_EMAIL = "synthetic@example.test";
    private static final String SYNTHETIC_PLATE = "SYNTHETIC123";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ConsultarContribuyenteUseCase taxpayerUseCase;

    @MockitoBean
    private ConsultarVehiculoUseCase vehicleUseCase;

    @Test
    void taxpayerEndpointReturnsEnvelopeTypedProviderFieldsAndMatchingRequestId() throws Exception {
        Contribuyente taxpayer = new Contribuyente(
                new Ruc(SYNTHETIC_RUC),
                TipoPersona.NATURAL,
                Map.of(
                        "numeroRuc", ValorCampoContribuyente.texto(SYNTHETIC_RUC),
                        "razonSocial", ValorCampoContribuyente.texto("SYNTHETIC_NAME"),
                        "categoria", ValorCampoContribuyente.numero(new java.math.BigDecimal("3.25")),
                        "obligadoLlevarContabilidad", ValorCampoContribuyente.booleano(false),
                        "contribuyenteEspecial", ValorCampoContribuyente.nulo(),
                        "informacionFechasContribuyente", ValorCampoContribuyente.objeto(Map.of(
                                "fechaInicioActividades", ValorCampoContribuyente.texto("2020-01-02")))));
        when(taxpayerUseCase.consultar(any())).thenReturn(taxpayer);

        MvcResult result = mockMvc.perform(post(TAXPAYER_PATH)
                        .header(RequestIdFilter.HEADER_NAME, "client-supplied-id")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","ruc":"%s"}
                                """.formatted(SYNTHETIC_EMAIL, SYNTHETIC_RUC)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.ruc").value(SYNTHETIC_RUC))
                .andExpect(jsonPath("$.data.tipoPersona").value("NATURAL"))
                .andExpect(jsonPath("$.data.datosSRI.numeroRuc").value(SYNTHETIC_RUC))
                .andExpect(jsonPath("$.data.datosSRI.categoria").value(3.25))
                .andExpect(jsonPath("$.data.datosSRI.obligadoLlevarContabilidad").value(false))
                .andExpect(jsonPath("$.data.datosSRI.contribuyenteEspecial").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.meta.source").value("SRI"))
                .andExpect(jsonPath("$.meta.retrievedAt").isNotEmpty())
                .andReturn();

        String body = result.getResponse().getContentAsString();
        assertFalse(body.contains(SYNTHETIC_EMAIL));
        assertRequestIdMatchesHeader(result, "$.meta.requestId", "client-supplied-id");
        org.mockito.ArgumentCaptor<ConsultarContribuyenteQuery> queryCaptor =
                forClass(ConsultarContribuyenteQuery.class);
        verify(taxpayerUseCase).consultar(queryCaptor.capture());
        assertEquals(SYNTHETIC_RUC, queryCaptor.getValue().ruc().value());
    }

    @Test
    void vehicleEndpointReturnsDtoListWithOriginalJsonTypes() throws Exception {
        Vehiculo vehicle = new Vehiculo(Map.of(
                "codigoVehiculo", new ValorCampoVehiculo(ValorCampoVehiculo.TipoDato.NUMERO, "123"),
                "numeroPlaca", new ValorCampoVehiculo(ValorCampoVehiculo.TipoDato.TEXTO, SYNTHETIC_PLATE),
                "aplicaCuota", new ValorCampoVehiculo(ValorCampoVehiculo.TipoDato.BOOLEANO, "true"),
                "fechaRevision", new ValorCampoVehiculo(ValorCampoVehiculo.TipoDato.NULO, null)));
        when(vehicleUseCase.consultar(any())).thenReturn(List.of(vehicle));

        Instant requestStartedAt = Instant.now().minusSeconds(1);
        MvcResult result = mockMvc.perform(post(VEHICLE_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format("{\"placa\":\"%s\"}", SYNTHETIC_PLATE)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].camposSRI.codigoVehiculo").value(123))
                .andExpect(jsonPath("$.data[0].camposSRI.numeroPlaca").value(SYNTHETIC_PLATE))
                .andExpect(jsonPath("$.data[0].camposSRI.aplicaCuota").value(true))
                .andExpect(jsonPath("$.data[0].camposSRI.fechaRevision").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.meta.source").value("SRI"))
                .andReturn();

        assertRequestIdMatchesHeader(result, "$.meta.requestId", null);
        String retrievedAt = JsonPath.read(result.getResponse().getContentAsString(), "$.meta.retrievedAt");
        Instant parsedRetrievedAt = Instant.parse(retrievedAt);
        assertFalse(parsedRetrievedAt.isBefore(requestStartedAt));
        assertTrue(parsedRetrievedAt.isBefore(Instant.now().plusSeconds(1)));
        verify(vehicleUseCase).consultar(new ConsultarVehiculoQuery(SYNTHETIC_PLATE));
    }

    @Test
    void vehicleEndpointKeepsEmptyProviderResultAsSuccessfulEmptyList() throws Exception {
        when(vehicleUseCase.consultar(any())).thenReturn(List.of());

        mockMvc.perform(post(VEHICLE_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format("{\"placa\":\"%s\"}", SYNTHETIC_PLATE)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(0));
    }

    @Test
    void invalidEmailReturnsValidationErrorAndDoesNotCallUseCase() throws Exception {
        String body = """
                {"email":"not-an-email","ruc":"%s"}
                """.formatted(SYNTHETIC_RUC);
        MvcResult result = mockMvc.perform(post(TAXPAYER_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                .andReturn();

        assertErrorIsSanitized(result, "$.error.requestId", "not-an-email", SYNTHETIC_RUC);
        verify(taxpayerUseCase, never()).consultar(any());
    }

    @Test
    void invalidRucReturnsValidationErrorAndDoesNotCallUseCase() throws Exception {
        MvcResult result = mockMvc.perform(post(TAXPAYER_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format("{\"email\":\"%s\",\"ruc\":\"123\"}", SYNTHETIC_EMAIL)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                .andReturn();

        assertErrorIsSanitized(result, "$.error.requestId", SYNTHETIC_EMAIL, "123");
        verify(taxpayerUseCase, never()).consultar(any());
    }

    @Test
    void invalidPlateReturnsValidationErrorAndDoesNotCallUseCase() throws Exception {
        MvcResult result = mockMvc.perform(post(VEHICLE_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"placa\":\"ABC-123\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                .andReturn();

        assertErrorIsSanitized(result, "$.error.requestId", "ABC-123");
        verify(vehicleUseCase, never()).consultar(any());
    }

    @Test
    void malformedJsonReturnsStableBadRequest() throws Exception {
        MvcResult result = mockMvc.perform(post(TAXPAYER_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("MALFORMED_JSON"))
                .andReturn();

        assertRequestIdMatchesHeader(result, "$.error.requestId", null);
        verify(taxpayerUseCase, never()).consultar(any());
    }

    @Test
    void mapsConfirmedTaxpayerAbsenceTo404() throws Exception {
        when(taxpayerUseCase.consultar(any())).thenThrow(new ContribuyenteNoEncontradoException());

        MvcResult result = postTaxpayerExpectingError(404, "NOT_FOUND");

        assertErrorIsSanitized(result, "$.error.requestId", SYNTHETIC_RUC, SYNTHETIC_EMAIL);
    }

    @Test
    void distinguishesNonNaturalAndUndeterminedTaxpayerErrorsAs422() throws Exception {
        when(taxpayerUseCase.consultar(any())).thenThrow(new ContribuyenteNoEsPersonaNaturalException());
        MvcResult nonNatural = postTaxpayerExpectingError(422, "IDENTIFICATION_NOT_NATURAL_PERSON");
        assertErrorIsSanitized(nonNatural, "$.error.requestId", SYNTHETIC_RUC, SYNTHETIC_EMAIL);

        reset(taxpayerUseCase);
        when(taxpayerUseCase.consultar(any())).thenThrow(new TipoPersonaNoDeterminadaException());
        MvcResult undetermined = postTaxpayerExpectingError(422, "TAXPAYER_TYPE_UNDETERMINED");
        assertErrorIsSanitized(undetermined, "$.error.requestId", SYNTHETIC_RUC, SYNTHETIC_EMAIL);
    }

    @Test
    void mapsSriBadResponseUnavailableAndTimeoutTo502503And504() throws Exception {
        when(taxpayerUseCase.consultar(any())).thenThrow(SriIntegrationException.invalidResponse());
        assertErrorIsSanitized(postTaxpayerExpectingError(502, "PROVIDER_BAD_RESPONSE"),
                "$.error.requestId", SYNTHETIC_RUC);

        reset(taxpayerUseCase);
        when(taxpayerUseCase.consultar(any())).thenThrow(SriIntegrationException.unavailable());
        assertErrorIsSanitized(postTaxpayerExpectingError(503, "PROVIDER_UNAVAILABLE"),
                "$.error.requestId", SYNTHETIC_RUC);

        reset(taxpayerUseCase);
        when(taxpayerUseCase.consultar(any())).thenThrow(SriIntegrationException.timeout());
        assertErrorIsSanitized(postTaxpayerExpectingError(504, "PROVIDER_TIMEOUT"),
                "$.error.requestId", SYNTHETIC_RUC);
    }

    @Test
    void mapsProviderHttpErrorToBadGatewayWithoutLeakingExternalDetails() throws Exception {
        when(taxpayerUseCase.consultar(any())).thenThrow(SriIntegrationException.httpError(502));

        MvcResult result = postTaxpayerExpectingError(502, "PROVIDER_BAD_RESPONSE");

        assertErrorIsSanitized(result, "$.error.requestId", SYNTHETIC_RUC, "502");
    }

    @Test
    void unexpectedErrorDoesNotExposeInternalExceptionDetails() throws Exception {
        when(vehicleUseCase.consultar(any())).thenThrow(
                new IllegalStateException("PRIVATE_SYNTHETIC_PLATE_1234 and private provider detail"));

        MvcResult result = mockMvc.perform(post(VEHICLE_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format("{\"placa\":\"%s\"}", SYNTHETIC_PLATE)))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error.code").value("INTERNAL_ERROR"))
                .andReturn();

        assertErrorIsSanitized(result, "$.error.requestId", "PRIVATE_SYNTHETIC_PLATE_1234",
                "private provider detail", "IllegalStateException", SYNTHETIC_PLATE);
    }

    private MvcResult postTaxpayerExpectingError(int statusCode, String code) throws Exception {
        return mockMvc.perform(post(TAXPAYER_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format("{\"email\":\"%s\",\"ruc\":\"%s\"}",
                                SYNTHETIC_EMAIL, SYNTHETIC_RUC)))
                .andExpect(status().is(statusCode))
                .andExpect(jsonPath("$.error.code").value(code))
                .andReturn();
    }

    private void assertRequestIdMatchesHeader(MvcResult result, String bodyPath, String clientRequestId)
            throws Exception {
        String headerId = result.getResponse().getHeader(RequestIdFilter.HEADER_NAME);
        String bodyId = JsonPath.read(result.getResponse().getContentAsString(), bodyPath);
        assertEquals(1, result.getResponse().getHeaders(RequestIdFilter.HEADER_NAME).size());
        assertTrue(headerId != null && !headerId.isBlank());
        assertEquals(headerId, bodyId);
        assertDoesNotThrow(() -> UUID.fromString(bodyId));
        if (clientRequestId != null) {
            assertNotEquals(clientRequestId, headerId);
        }
    }

    private void assertErrorIsSanitized(MvcResult result, String bodyPath, String... forbiddenValues)
            throws Exception {
        String body = result.getResponse().getContentAsString();
        for (String forbidden : forbiddenValues) {
            assertFalse(body.contains(forbidden), "La respuesta filtró un valor sensible o interno.");
        }
        assertRequestIdMatchesHeader(result, bodyPath, null);
    }
}
