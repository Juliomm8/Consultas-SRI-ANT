package ec.edu.udla.consultas.contribuyentes.adapter.out.sri;

import ec.edu.udla.consultas.contribuyentes.domain.Ruc;
import ec.edu.udla.consultas.contribuyentes.domain.TipoPersona;
import ec.edu.udla.consultas.shared.integration.sri.SriIntegrationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.util.Optional;

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
                        + "\",\"tipoContribuyente\":\"valor-no-clasificado\"}", MediaType.APPLICATION_JSON));

        var result = adapter.consultarPor(new Ruc(TEST_RUC));

        assertTrue(result.isPresent());
        assertEquals(TEST_RUC, result.orElseThrow().ruc().value());
        assertEquals(TipoPersona.NO_DETERMINADA, result.orElseThrow().tipoPersona());
        server.verify();
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
