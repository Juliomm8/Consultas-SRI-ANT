package ec.edu.udla.consultas.vehiculos.adapter.out.sri;

import ec.edu.udla.consultas.shared.integration.sri.SriIntegrationException;
import ec.edu.udla.consultas.vehiculos.domain.ValorCampoVehiculo;
import ec.edu.udla.consultas.vehiculos.domain.Vehiculo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.net.URI;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withNoContent;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class SriVehiculoHttpAdapterTests {

    private static final String TEST_IDENTIFIER = "TEST&123";
    private static final String ENCODED_TEST_IDENTIFIER = "TEST%26123";
    private static final String EXPECTED_URI = "https://sri.test/vehicle?numeroPlacaCampvCpn="
            + ENCODED_TEST_IDENTIFIER;
    private static final URI VEHICLE_ENDPOINT = URI.create("https://sri.test/vehicle");

    private MockRestServiceServer server;
    private SriVehiculoHttpAdapter adapter;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        adapter = new SriVehiculoHttpAdapter(builder.build(), VEHICLE_ENDPOINT);
    }

    @Test
    void mapsObservedSuccessfulRootObjectAndRetainsJsonScalarTypesAndNulls() {
        server.expect(requestTo(EXPECTED_URI))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("""
                        {
                          "codigoVehiculo": 123,
                          "numeroPlaca": "VEHICLE123",
                          "numeroCamvCpn": null,
                          "descripcionMarca": "SYNTHETIC_BRAND",
                          "descripcionModelo": "SYNTHETIC_MODEL",
                          "anioAuto": 2020,
                          "descripcionPais": null,
                          "ultimoAnioPagado": 2024,
                          "estadoExoneracion": null,
                          "aplicaCuota": false,
                          "verMensajeElectricos": true,
                          "unobservedField": "not-mapped"
                        }
                        """, MediaType.APPLICATION_JSON));

        List<Vehiculo> result = adapter.consultarPorIdentificador(TEST_IDENTIFIER);

        assertEquals(1, result.size());
        var attributes = result.get(0).atributos();
        assertEquals(new ValorCampoVehiculo(ValorCampoVehiculo.TipoDato.NUMERO, "123"),
                attributes.get("codigoVehiculo"));
        assertEquals(new ValorCampoVehiculo(ValorCampoVehiculo.TipoDato.NULO, null),
                attributes.get("numeroCamvCpn"));
        assertEquals(new ValorCampoVehiculo(ValorCampoVehiculo.TipoDato.BOOLEANO, "false"),
                attributes.get("aplicaCuota"));
        assertFalse(attributes.containsKey("unobservedField"));
        assertFalse(result.get(0).toString().contains("VEHICLE123"));
        server.verify();
    }

    @Test
    void returnsAnEmptyListForTheObservedNegativeEnvelopeWithEmptyData() {
        server.expect(requestTo(EXPECTED_URI))
                .andRespond(withSuccess("""
                        {
                          "mensajeServidor": {"texto": "synthetic message"},
                          "objeto": null,
                          "data": []
                        }
                        """, MediaType.APPLICATION_JSON));

        assertEquals(List.of(), adapter.consultarPorIdentificador(TEST_IDENTIFIER));
        server.verify();
    }

    @Test
    void retainsNonEmptyDataArrayAsAnUnverifiedCompatibleVariant() {
        server.expect(requestTo(EXPECTED_URI))
                .andRespond(withSuccess("""
                        {
                          "mensajeServidor": {"texto": "synthetic message"},
                          "objeto": null,
                          "data": [{"codigoVehiculo": 456, "numeroPlaca": "VEHICLE456"}]
                        }
                        """, MediaType.APPLICATION_JSON));

        List<Vehiculo> result = adapter.consultarPorIdentificador(TEST_IDENTIFIER);

        assertEquals(1, result.size());
        assertEquals(new ValorCampoVehiculo(ValorCampoVehiculo.TipoDato.NUMERO, "456"),
                result.get(0).atributos().get("codigoVehiculo"));
        server.verify();
    }

    @Test
    void rejectsMalformedJsonWithoutRetainingBodyOrIdentifier() {
        String syntheticBody = "{malformed SYNTHETIC_PRIVATE_BODY " + TEST_IDENTIFIER;
        server.expect(requestTo(EXPECTED_URI))
                .andRespond(withSuccess(syntheticBody, MediaType.APPLICATION_JSON));

        SriIntegrationException exception = assertThrows(
                SriIntegrationException.class,
                () -> adapter.consultarPorIdentificador(TEST_IDENTIFIER));

        assertEquals(SriIntegrationException.Kind.INVALID_RESPONSE, exception.kind());
        assertNull(exception.getCause());
        assertFalse(exception.getMessage().contains(TEST_IDENTIFIER));
        assertFalse(exception.getMessage().contains("SYNTHETIC_PRIVATE_BODY"));
        server.verify();
    }

    @Test
    void treatsHttp204AsAnInvalidEmptyResponse() {
        server.expect(requestTo(EXPECTED_URI)).andRespond(withNoContent());

        SriIntegrationException exception = assertThrows(
                SriIntegrationException.class,
                () -> adapter.consultarPorIdentificador(TEST_IDENTIFIER));

        assertEquals(SriIntegrationException.Kind.INVALID_RESPONSE, exception.kind());
        assertFalse(exception.getMessage().contains(TEST_IDENTIFIER));
        server.verify();
    }

    @Test
    void mapsHttp502WithoutExposingThePlateOrExternalBody() {
        String externalBody = "synthetic gateway body " + TEST_IDENTIFIER;
        server.expect(requestTo(EXPECTED_URI))
                .andRespond(withStatus(HttpStatus.BAD_GATEWAY).body(externalBody));

        assertSanitizedHttpError(502, externalBody);
    }

    @Test
    void mapsHttp503WithoutExposingThePlateOrExternalBody() {
        String externalBody = "synthetic unavailable body " + TEST_IDENTIFIER;
        server.expect(requestTo(EXPECTED_URI))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE).body(externalBody));

        assertSanitizedHttpError(503, externalBody);
    }

    @Test
    void mapsConnectionFailureWithoutRetainingTheExceptionOrIdentifier() {
        server.expect(requestTo(EXPECTED_URI))
                .andRespond(withException(new IOException("synthetic connection failure " + TEST_IDENTIFIER)));

        SriIntegrationException exception = assertThrows(
                SriIntegrationException.class,
                () -> adapter.consultarPorIdentificador(TEST_IDENTIFIER));

        assertEquals(SriIntegrationException.Kind.UNAVAILABLE, exception.kind());
        assertNull(exception.getCause());
        assertFalse(exception.getMessage().contains(TEST_IDENTIFIER));
        server.verify();
    }

    @Test
    void rejectsRootVehicleWithoutRequiredCode() {
        String syntheticBody = "{\"numeroPlaca\":\"" + TEST_IDENTIFIER + "\"}";
        server.expect(requestTo(EXPECTED_URI))
                .andRespond(withSuccess(syntheticBody, MediaType.APPLICATION_JSON));

        SriIntegrationException exception = assertThrows(
                SriIntegrationException.class,
                () -> adapter.consultarPorIdentificador(TEST_IDENTIFIER));

        assertEquals(SriIntegrationException.Kind.INVALID_RESPONSE, exception.kind());
        assertFalse(exception.getMessage().contains(TEST_IDENTIFIER));
        assertFalse(exception.getMessage().contains(syntheticBody));
        server.verify();
    }

    @Test
    void rejectsDataItemWithoutRequiredCode() {
        server.expect(requestTo(EXPECTED_URI))
                .andRespond(withSuccess("{\"data\":[{\"numeroPlaca\":\"VEHICLE123\"}]}",
                        MediaType.APPLICATION_JSON));

        SriIntegrationException exception = assertThrows(
                SriIntegrationException.class,
                () -> adapter.consultarPorIdentificador(TEST_IDENTIFIER));

        assertEquals(SriIntegrationException.Kind.INVALID_RESPONSE, exception.kind());
        server.verify();
    }

    @Test
    void rejectsUnexpectedRootShape() {
        server.expect(requestTo(EXPECTED_URI))
                .andRespond(withSuccess("{\"unrelated\":\"synthetic\"}", MediaType.APPLICATION_JSON));

        SriIntegrationException exception = assertThrows(
                SriIntegrationException.class,
                () -> adapter.consultarPorIdentificador(TEST_IDENTIFIER));
        assertEquals(SriIntegrationException.Kind.INVALID_RESPONSE, exception.kind());
        server.verify();
    }

    @Test
    void rejectsUnexpectedDataShape() {
        server.expect(requestTo(EXPECTED_URI))
                .andRespond(withSuccess("{\"data\":{}}", MediaType.APPLICATION_JSON));

        SriIntegrationException exception = assertThrows(
                SriIntegrationException.class,
                () -> adapter.consultarPorIdentificador(TEST_IDENTIFIER));
        assertEquals(SriIntegrationException.Kind.INVALID_RESPONSE, exception.kind());
        server.verify();
    }

    @Test
    void rejectsNonObjectElementsInsideDataArray() {
        server.expect(requestTo(EXPECTED_URI))
                .andRespond(withSuccess("{\"data\":[123]}", MediaType.APPLICATION_JSON));

        SriIntegrationException exception = assertThrows(
                SriIntegrationException.class,
                () -> adapter.consultarPorIdentificador(TEST_IDENTIFIER));

        assertEquals(SriIntegrationException.Kind.INVALID_RESPONSE, exception.kind());
        server.verify();
    }

    private void assertSanitizedHttpError(int expectedStatus, String externalBody) {
        SriIntegrationException exception = assertThrows(
                SriIntegrationException.class,
                () -> adapter.consultarPorIdentificador(TEST_IDENTIFIER));

        assertEquals(SriIntegrationException.Kind.HTTP_ERROR, exception.kind());
        assertEquals(expectedStatus, exception.upstreamStatus());
        assertNull(exception.getCause());
        assertFalse(exception.getMessage().contains(TEST_IDENTIFIER));
        assertFalse(exception.getMessage().contains(externalBody));
        server.verify();
    }
}
