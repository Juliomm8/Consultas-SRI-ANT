package ec.edu.udla.consultas.contribuyentes.adapter.out.sri;

import ec.edu.udla.consultas.contribuyentes.domain.Ruc;
import ec.edu.udla.consultas.contribuyentes.domain.TipoPersona;
import ec.edu.udla.consultas.contribuyentes.domain.ValorCampoContribuyente;
import ec.edu.udla.consultas.shared.integration.sri.SriIntegrationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withNoContent;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class SriContribuyenteHttpAdapterTests {

    private static final String TEST_RUC = "1234567890123";
    private static final URI EXISTENCE_ENDPOINT = URI.create("https://sri.test/existence");
    private static final URI DETAILS_ENDPOINT = URI.create("https://sri.test/details");

    private MockRestServiceServer server;
    private SriContribuyenteHttpAdapter adapter;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        adapter = new SriContribuyenteHttpAdapter(builder.build(), EXISTENCE_ENDPOINT, DETAILS_ENDPOINT);
    }

    @Test
    void requestsDetailsOnlyAfterExistenceIsConfirmedAndPreservesUnknownPersonType() {
        server.expect(requestTo("https://sri.test/existence?numeroRuc=" + TEST_RUC))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("true", MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://sri.test/details?ruc=" + TEST_RUC))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("{\"numeroRuc\":\"" + TEST_RUC
                        + "\",\"tipoContribuyente\":\"UNCONFIRMED_TYPE\"}", MediaType.APPLICATION_JSON));

        var result = adapter.consultarPor(new Ruc(TEST_RUC));

        assertTrue(result.isPresent());
        assertEquals(TEST_RUC, result.orElseThrow().ruc().value());
        assertEquals(TipoPersona.NO_DETERMINADA, result.orElseThrow().tipoPersona());
        server.verify();
    }

    @Test
    void mapsConfirmedNaturalPersonType() {
        server.expect(requestTo("https://sri.test/existence?numeroRuc=" + TEST_RUC))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("true", MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://sri.test/details?ruc=" + TEST_RUC))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("{\"numeroRuc\":\"" + TEST_RUC
                        + "\",\"tipoContribuyente\":\"PERSONA NATURAL\"}", MediaType.APPLICATION_JSON));

        var result = adapter.consultarPor(new Ruc(TEST_RUC));

        assertTrue(result.isPresent());
        assertEquals(TEST_RUC, result.orElseThrow().ruc().value());
        assertEquals(TipoPersona.NATURAL, result.orElseThrow().tipoPersona());
        server.verify();
    }

    @Test
    void preservesObservedProviderFieldsTypesAndNestedStructuresAndExcludesUnknownFields() {
        server.expect(requestTo("https://sri.test/existence?numeroRuc=" + TEST_RUC))
                .andRespond(withSuccess("true", MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://sri.test/details?ruc=" + TEST_RUC))
                .andRespond(withSuccess("""
                        {
                          "numeroRuc": "%s",
                          "razonSocial": "SYNTHETIC_NAME",
                          "estadoContribuyenteRuc": "ACTIVE_SYNTHETIC",
                          "actividadEconomicaPrincipal": "ACTIVITY_SYNTHETIC",
                          "tipoContribuyente": "PERSONA NATURAL",
                          "regimen": "REGIME_SYNTHETIC",
                          "categoria": 2.50,
                          "obligadoLlevarContabilidad": false,
                          "agenteRetencion": true,
                          "contribuyenteEspecial": null,
                          "informacionFechasContribuyente": {
                            "fechaInicioActividades": "2020-01-02",
                            "fechaCese": null,
                            "fechaReinicioActividades": 2024,
                            "fechaActualizacion": null,
                            "fechaNoObservada": "omit"
                          },
                          "representantesLegales": [
                            {"nombreSintetico": "SYNTHETIC_NAME", "activo": true, "detalle": null}
                          ],
                          "motivoCancelacionSuspension": null,
                          "contribuyenteFantasma": false,
                          "transaccionesInexistente": [],
                          "campoNoObservado": "must-be-omitted"
                        }
                        """.formatted(TEST_RUC), MediaType.APPLICATION_JSON));

        var taxpayer = adapter.consultarPor(new Ruc(TEST_RUC)).orElseThrow();
        Map<String, ValorCampoContribuyente> fields = taxpayer.datosSRI();

        assertEquals(TipoPersona.NATURAL, taxpayer.tipoPersona());
        assertEquals(Set.of(
                "numeroRuc", "razonSocial", "estadoContribuyenteRuc", "actividadEconomicaPrincipal",
                "tipoContribuyente", "regimen", "categoria", "obligadoLlevarContabilidad", "agenteRetencion",
                "contribuyenteEspecial", "informacionFechasContribuyente", "representantesLegales",
                "motivoCancelacionSuspension", "contribuyenteFantasma", "transaccionesInexistente"), fields.keySet());
        assertEquals(ValorCampoContribuyente.TipoDato.TEXTO, fields.get("razonSocial").tipoDato());
        assertEquals(ValorCampoContribuyente.TipoDato.NUMERO, fields.get("categoria").tipoDato());
        assertEquals(0, new BigDecimal("2.50").compareTo((BigDecimal) fields.get("categoria").valor()));
        assertEquals(ValorCampoContribuyente.TipoDato.BOOLEANO, fields.get("agenteRetencion").tipoDato());
        assertEquals(Boolean.TRUE, fields.get("agenteRetencion").valor());
        assertEquals(ValorCampoContribuyente.TipoDato.NULO, fields.get("contribuyenteEspecial").tipoDato());
        assertEquals(ValorCampoContribuyente.TipoDato.ARREGLO, fields.get("transaccionesInexistente").tipoDato());

        @SuppressWarnings("unchecked")
        Map<String, ValorCampoContribuyente> dates = (Map<String, ValorCampoContribuyente>) fields
                .get("informacionFechasContribuyente").valor();
        assertEquals(Set.of("fechaInicioActividades", "fechaCese", "fechaReinicioActividades", "fechaActualizacion"),
                dates.keySet());
        assertEquals(ValorCampoContribuyente.TipoDato.TEXTO, dates.get("fechaInicioActividades").tipoDato());
        assertEquals(ValorCampoContribuyente.TipoDato.NULO, dates.get("fechaCese").tipoDato());
        assertEquals(ValorCampoContribuyente.TipoDato.NUMERO, dates.get("fechaReinicioActividades").tipoDato());

        @SuppressWarnings("unchecked")
        List<ValorCampoContribuyente> representatives = (List<ValorCampoContribuyente>) fields
                .get("representantesLegales").valor();
        @SuppressWarnings("unchecked")
        Map<String, ValorCampoContribuyente> representative = (Map<String, ValorCampoContribuyente>) representatives
                .get(0).valor();
        assertEquals(ValorCampoContribuyente.TipoDato.BOOLEANO, representative.get("activo").tipoDato());
        assertEquals(ValorCampoContribuyente.TipoDato.NULO, representative.get("detalle").tipoDato());
        assertFalse(fields.containsKey("campoNoObservado"));
        assertThrows(UnsupportedOperationException.class,
                () -> fields.put("regimen", ValorCampoContribuyente.texto("other")));
        assertFalse(taxpayer.toString().contains(TEST_RUC));
        assertFalse(taxpayer.toString().contains("SYNTHETIC_NAME"));
        server.verify();
    }

    @Test
    void keepsPersonTypeUndeterminedWhenTheProviderPropertyIsMissing() {
        assertUndeterminedPersonType("{\"numeroRuc\":\"" + TEST_RUC + "\"}");
    }

    @Test
    void keepsPersonTypeUndeterminedWhenTheProviderPropertyIsNull() {
        assertUndeterminedPersonType("{\"numeroRuc\":\"" + TEST_RUC
                + "\",\"tipoContribuyente\":null}");
    }

    @Test
    void keepsPersonTypeUndeterminedWhenTheProviderPropertyHasAnUnexpectedJsonType() {
        assertUndeterminedPersonType("{\"numeroRuc\":\"" + TEST_RUC
                + "\",\"tipoContribuyente\":{\"value\":\"PERSONA NATURAL\"}}");
    }

    @Test
    void returnsEmptyAndDoesNotRequestDetailsWhenExistenceIsFalse() {
        server.expect(requestTo("https://sri.test/existence?numeroRuc=" + TEST_RUC))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("false", MediaType.APPLICATION_JSON));

        Optional<?> result = adapter.consultarPor(new Ruc(TEST_RUC));

        assertTrue(result.isEmpty());
        server.verify();
    }

    @Test
    void treatsNoContentAfterPositiveExistenceAsInvalidResponse() {
        server.expect(requestTo("https://sri.test/existence?numeroRuc=" + TEST_RUC))
                .andRespond(withSuccess("true", MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://sri.test/details?ruc=" + TEST_RUC))
                .andRespond(withNoContent());

        SriIntegrationException exception = assertThrows(
                SriIntegrationException.class,
                () -> adapter.consultarPor(new Ruc(TEST_RUC)));

        assertEquals(SriIntegrationException.Kind.INVALID_RESPONSE, exception.kind());
        assertFalse(exception.getMessage().contains(TEST_RUC));
        server.verify();
    }

    @Test
    void rejectsMalformedJsonWithoutRetainingResponseDetails() {
        server.expect(requestTo("https://sri.test/existence?numeroRuc=" + TEST_RUC))
                .andRespond(withSuccess("{malformed", MediaType.APPLICATION_JSON));

        SriIntegrationException exception = assertThrows(
                SriIntegrationException.class,
                () -> adapter.consultarPor(new Ruc(TEST_RUC)));

        assertEquals(SriIntegrationException.Kind.INVALID_RESPONSE, exception.kind());
        assertNull(exception.getCause());
        assertFalse(exception.getMessage().contains(TEST_RUC));
        server.verify();
    }

    @Test
    void rejectsNonBooleanExistenceResponses() {
        server.expect(requestTo("https://sri.test/existence?numeroRuc=" + TEST_RUC))
                .andRespond(withSuccess("{\"exists\":true}", MediaType.APPLICATION_JSON));

        SriIntegrationException exception = assertThrows(
                SriIntegrationException.class,
                () -> adapter.consultarPor(new Ruc(TEST_RUC)));

        assertEquals(SriIntegrationException.Kind.INVALID_RESPONSE, exception.kind());
        server.verify();
    }

    @Test
    void mapsExternalServerErrorsWithoutExposingTheBodyOrIdentifier() {
        server.expect(requestTo("https://sri.test/existence?numeroRuc=" + TEST_RUC))
                .andRespond(withStatus(HttpStatus.BAD_GATEWAY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"detail\":\"synthetic-sensitive-body\"}"));

        SriIntegrationException exception = assertThrows(
                SriIntegrationException.class,
                () -> adapter.consultarPor(new Ruc(TEST_RUC)));

        assertEquals(SriIntegrationException.Kind.HTTP_ERROR, exception.kind());
        assertEquals(502, exception.upstreamStatus());
        assertNull(exception.getCause());
        assertFalse(exception.getMessage().contains(TEST_RUC));
        assertFalse(exception.getMessage().contains("synthetic-sensitive-body"));
        server.verify();
    }

    private void assertUndeterminedPersonType(String detailsResponse) {
        server.expect(requestTo("https://sri.test/existence?numeroRuc=" + TEST_RUC))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("true", MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://sri.test/details?ruc=" + TEST_RUC))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(detailsResponse, MediaType.APPLICATION_JSON));

        var result = adapter.consultarPor(new Ruc(TEST_RUC));

        assertTrue(result.isPresent());
        assertEquals(TipoPersona.NO_DETERMINADA, result.orElseThrow().tipoPersona());
        server.verify();
    }

    @Test
    void rejectsDetailsThatDoNotMatchTheRequestedRuc() {
        server.expect(requestTo("https://sri.test/existence?numeroRuc=" + TEST_RUC))
                .andRespond(withSuccess("true", MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://sri.test/details?ruc=" + TEST_RUC))
                .andRespond(withSuccess("{\"numeroRuc\":\"9999999999999\"}", MediaType.APPLICATION_JSON));

        SriIntegrationException exception = assertThrows(
                SriIntegrationException.class,
                () -> adapter.consultarPor(new Ruc(TEST_RUC)));

        assertEquals(SriIntegrationException.Kind.INVALID_RESPONSE, exception.kind());
        assertFalse(exception.getMessage().contains(TEST_RUC));
        server.verify();
    }
}
