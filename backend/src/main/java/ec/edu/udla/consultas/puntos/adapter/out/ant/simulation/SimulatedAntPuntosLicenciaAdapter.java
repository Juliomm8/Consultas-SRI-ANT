package ec.edu.udla.consultas.puntos.adapter.out.ant.simulation;

import ec.edu.udla.consultas.puntos.application.exception.PuntosLicenciaProviderException;
import ec.edu.udla.consultas.puntos.application.port.out.PuntosLicenciaConsultaPort;
import ec.edu.udla.consultas.puntos.domain.Cedula;
import ec.edu.udla.consultas.puntos.domain.FuentePuntosLicencia;
import ec.edu.udla.consultas.puntos.domain.ResultadoPuntosLicencia;

import java.time.Clock;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Local deterministic adapter. It does not make HTTP requests and its results
 * are never represented as official ANT data.
 */
public final class SimulatedAntPuntosLicenciaAdapter implements PuntosLicenciaConsultaPort {

    private final AntSimulationProperties properties;
    private final Clock clock;
    private final AtomicInteger calls = new AtomicInteger();

    public SimulatedAntPuntosLicenciaAdapter(AntSimulationProperties properties) {
        this(properties, Clock.systemUTC());
    }

    SimulatedAntPuntosLicenciaAdapter(AntSimulationProperties properties, Clock clock) {
        if (properties == null || properties.getScenario() == null) {
            throw new IllegalArgumentException("La configuración del simulador es obligatoria.");
        }
        if (properties.getTotalPoints() < 0) {
            throw new IllegalArgumentException("El total simulado debe ser no negativo.");
        }
        this.properties = properties;
        this.clock = clock;
    }

    @Override
    public Optional<ResultadoPuntosLicencia> consultar(Cedula cedula) {
        if (cedula == null) {
            throw new IllegalArgumentException("La cédula es obligatoria.");
        }

        int invocation = calls.incrementAndGet();
        return switch (properties.getScenario()) {
            case SUCCESS -> Optional.of(success(properties.getTotalPoints()));
            case ZERO -> Optional.of(success(0));
            case NO_DATA -> Optional.empty();
            case TIMEOUT -> throw new PuntosLicenciaProviderException(
                    PuntosLicenciaProviderException.Kind.TIMEOUT);
            case UNAVAILABLE -> throw new PuntosLicenciaProviderException(
                    PuntosLicenciaProviderException.Kind.UNAVAILABLE);
            case INVALID_RESPONSE -> throw new PuntosLicenciaProviderException(
                    PuntosLicenciaProviderException.Kind.INVALID_RESPONSE);
            case RECOVERY -> invocation == 1
                    ? throwUnavailable()
                    : Optional.of(success(properties.getTotalPoints()));
            case SUCCESS_THEN_TIMEOUT -> invocation == 1
                    ? Optional.of(success(properties.getTotalPoints()))
                    : throwTimeout();
        };
    }

    private Optional<ResultadoPuntosLicencia> throwTimeout() {
        throw new PuntosLicenciaProviderException(PuntosLicenciaProviderException.Kind.TIMEOUT);
    }

    private Optional<ResultadoPuntosLicencia> throwUnavailable() {
        throw new PuntosLicenciaProviderException(PuntosLicenciaProviderException.Kind.UNAVAILABLE);
    }

    private ResultadoPuntosLicencia success(int totalPoints) {
        return new ResultadoPuntosLicencia(
                totalPoints, clock.instant(), FuentePuntosLicencia.SIMULATED);
    }
}
