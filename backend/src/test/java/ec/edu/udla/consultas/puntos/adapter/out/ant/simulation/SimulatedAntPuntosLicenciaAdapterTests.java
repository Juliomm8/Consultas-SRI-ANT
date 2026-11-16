package ec.edu.udla.consultas.puntos.adapter.out.ant.simulation;

import ec.edu.udla.consultas.puntos.application.exception.PuntosLicenciaProviderException;
import ec.edu.udla.consultas.puntos.domain.Cedula;
import ec.edu.udla.consultas.puntos.domain.FuentePuntosLicencia;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

class SimulatedAntPuntosLicenciaAdapterTests {

    private static final Cedula CEDULA = new Cedula("0000000000");
    private static final Instant NOW = Instant.parse("2026-11-15T10:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    @Test
    void returnsOnlyTheNormalizedSyntheticPointsAndMarksSource() {
        AntSimulationProperties properties = properties(AntSimulationProperties.Scenario.SUCCESS, 22);
        var adapter = new SimulatedAntPuntosLicenciaAdapter(properties, CLOCK);

        var result = adapter.consultar(CEDULA).orElseThrow();

        assertThat(result.totalPuntos()).isEqualTo(22);
        assertThat(result.fuente()).isEqualTo(FuentePuntosLicencia.SIMULATED);
        assertThat(result.fetchedAt()).isEqualTo(NOW);
        assertThat(result.toString()).doesNotContain(CEDULA.value());
    }

    @Test
    void keepsZeroDifferentFromNoData() {
        var zero = new SimulatedAntPuntosLicenciaAdapter(
                properties(AntSimulationProperties.Scenario.ZERO, 20), CLOCK);
        var noData = new SimulatedAntPuntosLicenciaAdapter(
                properties(AntSimulationProperties.Scenario.NO_DATA, 20), CLOCK);

        assertThat(zero.consultar(CEDULA)).hasValueSatisfying(result ->
                assertThat(result.totalPuntos()).isZero());
        assertThat(noData.consultar(CEDULA)).isEmpty();
    }

    @Test
    void modelsTimeoutAndUnavailableWithoutMakingHttpCalls() {
        var timeout = new SimulatedAntPuntosLicenciaAdapter(
                properties(AntSimulationProperties.Scenario.TIMEOUT, 20), CLOCK);
        var unavailable = new SimulatedAntPuntosLicenciaAdapter(
                properties(AntSimulationProperties.Scenario.UNAVAILABLE, 20), CLOCK);
        var invalidResponse = new SimulatedAntPuntosLicenciaAdapter(
                properties(AntSimulationProperties.Scenario.INVALID_RESPONSE, 20), CLOCK);

        assertProviderFailure(timeout, PuntosLicenciaProviderException.Kind.TIMEOUT);
        assertProviderFailure(unavailable, PuntosLicenciaProviderException.Kind.UNAVAILABLE);
        assertProviderFailure(invalidResponse, PuntosLicenciaProviderException.Kind.INVALID_RESPONSE);
    }

    @Test
    void recoversAfterTheFirstSimulatedUnavailableCall() {
        var adapter = new SimulatedAntPuntosLicenciaAdapter(
                properties(AntSimulationProperties.Scenario.RECOVERY, 19), CLOCK);

        assertProviderFailure(adapter, PuntosLicenciaProviderException.Kind.UNAVAILABLE);
        assertThat(adapter.consultar(CEDULA)).hasValueSatisfying(result -> {
            assertThat(result.totalPuntos()).isEqualTo(19);
            assertThat(result.fuente()).isEqualTo(FuentePuntosLicencia.SIMULATED);
        });
    }

    @Test
    void succeedsOnceThenThrowsAnImmediateTimeoutWithoutSleeping() {
        var adapter = new SimulatedAntPuntosLicenciaAdapter(
                properties(AntSimulationProperties.Scenario.SUCCESS_THEN_TIMEOUT, 21), CLOCK);

        var first = adapter.consultar(CEDULA).orElseThrow();
        assertThat(first.totalPuntos()).isEqualTo(21);
        assertThat(first.fuente()).isEqualTo(FuentePuntosLicencia.SIMULATED);
        assertProviderFailure(adapter, PuntosLicenciaProviderException.Kind.TIMEOUT);
    }

    private static void assertProviderFailure(
            SimulatedAntPuntosLicenciaAdapter adapter,
            PuntosLicenciaProviderException.Kind kind) {
        Throwable failure = catchThrowable(() -> adapter.consultar(CEDULA));
        assertThat(failure)
                .isInstanceOf(PuntosLicenciaProviderException.class)
                .extracting(value -> ((PuntosLicenciaProviderException) value).kind())
                .isEqualTo(kind);
    }

    private static AntSimulationProperties properties(AntSimulationProperties.Scenario scenario, int points) {
        AntSimulationProperties properties = new AntSimulationProperties();
        properties.setScenario(scenario);
        properties.setTotalPoints(points);
        return properties;
    }
}
