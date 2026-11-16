package ec.edu.udla.consultas.puntos.adapter.in.rest;

import ec.edu.udla.consultas.puntos.adapter.out.ant.simulation.AntSimulationProperties;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "spring.profiles.active=local-simulation")
@AutoConfigureMockMvc
class PuntosLicenciaRestControllerTests {

    private static final String SYNTHETIC_CEDULA = "0000000000";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AntSimulationProperties simulationProperties;

    @Autowired
    private CircuitBreakerRegistry circuitBreakerRegistry;

    @Autowired
    private Environment environment;

    @BeforeEach
    void resetSimulation() {
        simulationProperties.setScenario(AntSimulationProperties.Scenario.SUCCESS);
        simulationProperties.setTotalPoints(20);
        circuitBreakerRegistry.circuitBreaker("antPoints").reset();
    }

    @Test
    void exposesTheSimulatedRouteOnlyOnLoopbackAndReturnsItsProvenance() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/licencias/puntos/consultas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cedula\":\"" + SYNTHETIC_CEDULA + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalPuntos").value(20))
                .andExpect(jsonPath("$.meta.source").value("SIMULATED"))
                .andExpect(jsonPath("$.meta.originSource").value("SIMULATED"))
                .andExpect(jsonPath("$.meta.stale").value(false))
                .andExpect(jsonPath("$.meta.fetchedAt").isNotEmpty())
                .andExpect(jsonPath("$.meta.warning").value(
                        org.hamcrest.Matchers.containsString("no es información oficial de ANT")))
                .andReturn();

        String requestId = result.getResponse().getHeader("X-Request-Id");
        assertThat(result.getResponse().getContentAsString()).contains("\"requestId\":\"" + requestId + "\"")
                .doesNotContain(SYNTHETIC_CEDULA);
        assertThat(environment.getProperty("server.address")).isEqualTo("127.0.0.1");
    }

    @Test
    void representsSimulatedZeroSeparatelyFromNoInformation() throws Exception {
        simulationProperties.setScenario(AntSimulationProperties.Scenario.ZERO);
        mockMvc.perform(post("/api/v1/licencias/puntos/consultas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cedula\":\"" + SYNTHETIC_CEDULA + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalPuntos").value(0))
                .andExpect(jsonPath("$.meta.source").value("SIMULATED"));

        simulationProperties.setScenario(AntSimulationProperties.Scenario.NO_DATA);
        mockMvc.perform(post("/api/v1/licencias/puntos/consultas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cedula\":\"" + SYNTHETIC_CEDULA + "\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("NOT_FOUND"))
                .andExpect(jsonPath("$.error.requestId").isNotEmpty());
    }

    @Test
    void mapsSimulatedTechnicalFailuresWithoutReturningPoints() throws Exception {
        simulationProperties.setScenario(AntSimulationProperties.Scenario.TIMEOUT);
        mockMvc.perform(post("/api/v1/licencias/puntos/consultas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cedula\":\"" + SYNTHETIC_CEDULA + "\"}"))
                .andExpect(status().isGatewayTimeout())
                .andExpect(jsonPath("$.error.code").value("PROVIDER_TIMEOUT"));

        circuitBreakerRegistry.circuitBreaker("antPoints").reset();
        simulationProperties.setScenario(AntSimulationProperties.Scenario.UNAVAILABLE);
        mockMvc.perform(post("/api/v1/licencias/puntos/consultas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cedula\":\"" + SYNTHETIC_CEDULA + "\"}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error.code").value("PROVIDER_UNAVAILABLE"));

        circuitBreakerRegistry.circuitBreaker("antPoints").reset();
        simulationProperties.setScenario(AntSimulationProperties.Scenario.INVALID_RESPONSE);
        mockMvc.perform(post("/api/v1/licencias/puntos/consultas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cedula\":\"" + SYNTHETIC_CEDULA + "\"}"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.error.code").value("PROVIDER_BAD_RESPONSE"));
    }

    @Test
    void validationErrorDoesNotEchoIdentification() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/licencias/puntos/consultas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cedula\":\"123\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                .andReturn();

        assertThat(result.getResponse().getContentAsString()).doesNotContain("123");
    }
}
