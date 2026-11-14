package ec.edu.udla.consultas.vehiculos.adapter.out.sri;

import ec.edu.udla.consultas.shared.integration.sri.SriIntegrationException;
import ec.edu.udla.consultas.vehiculos.domain.ValorCampoVehiculo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class SriVehiculoHttpAdapterTests {

    private static final String TEST_IDENTIFIER = "TEST&123";
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
    void mapsObservedFieldsAndRetainsJsonScalarTypesAndNulls() {
        server.expect(requestTo("https://sri.test/vehicle?numeroPlacaCampvCpn=TEST%26123"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("""
                        {"data":[{
                          "codigoVehiculo":123,
                          "numeroPlaca":"TEST123",
                          "numeroCamvCpn":null,
                          "descripcionMarca":"marca omitida",
                          "anioAuto":2020,
                          "aplicaCuota":false,
                          "propietario":{"nombre":"no-mapear"}
                        }]}
                        """, MediaType.APPLICATION_JSON));

        List<ec.edu.udla.consultas.vehiculos.domain.Vehiculo> result =
                adapter.consultarPorIdentificador(TEST_IDENTIFIER);

        assertEquals(1, result.size());
        var attributes = result.get(0).atributos();
        assertEquals(new ValorCampoVehiculo(ValorCampoVehiculo.TipoDato.NUMERO, "123"),
                attributes.get("codigoVehiculo"));
        assertEquals(new ValorCampoVehiculo(ValorCampoVehiculo.TipoDato.NULO, null),
                attributes.get("numeroCamvCpn"));
        assertEquals(new ValorCampoVehiculo(ValorCampoVehiculo.TipoDato.BOOLEANO, "false"),
                attributes.get("aplicaCuota"));
        assertFalse(attributes.containsKey("propietario"));
        assertFalse(result.get(0).toString().contains("TEST123"));
        server.verify();
    }

    @Test
    void returnsAnEmptyListForAnObservedEmptyDataArray() {
        server.expect(requestTo("https://sri.test/vehicle?numeroPlacaCampvCpn=TEST%26123"))
                .andRespond(withSuccess("{\"data\":[]}", MediaType.APPLICATION_JSON));

        assertEquals(List.of(), adapter.consultarPorIdentificador(TEST_IDENTIFIER));
        server.verify();
    }

    @Test
    void rejectsMalformedJson() {
        server.expect(requestTo("https://sri.test/vehicle?numeroPlacaCampvCpn=TEST%26123"))
                .andRespond(withSuccess("{malformed", MediaType.APPLICATION_JSON));

        SriIntegrationException exception = assertThrows(
                SriIntegrationException.class,
                () -> adapter.consultarPorIdentificador(TEST_IDENTIFIER));

        assertEquals(SriIntegrationException.Kind.INVALID_RESPONSE, exception.kind());
        assertFalse(exception.getMessage().contains(TEST_IDENTIFIER));
        server.verify();
    }

    @Test
    void rejectsUnexpectedDataShape() {
        server.expect(requestTo("https://sri.test/vehicle?numeroPlacaCampvCpn=TEST%26123"))
                .andRespond(withSuccess("{\"data\":{}}", MediaType.APPLICATION_JSON));

        SriIntegrationException exception = assertThrows(
                SriIntegrationException.class,
                () -> adapter.consultarPorIdentificador(TEST_IDENTIFIER));

        assertEquals(SriIntegrationException.Kind.INVALID_RESPONSE, exception.kind());
        server.verify();
    }

    @Test
    void mapsExternalHttpErrorsWithoutIncludingRequestValues() {
        server.expect(requestTo("https://sri.test/vehicle?numeroPlacaCampvCpn=TEST%26123"))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE).body("synthetic body"));

        SriIntegrationException exception = assertThrows(
                SriIntegrationException.class,
                () -> adapter.consultarPorIdentificador(TEST_IDENTIFIER));

        assertEquals(SriIntegrationException.Kind.HTTP_ERROR, exception.kind());
        assertEquals(503, exception.upstreamStatus());
        assertFalse(exception.getMessage().contains(TEST_IDENTIFIER));
        assertFalse(exception.getMessage().contains("synthetic body"));
        server.verify();
    }
}
