package ec.edu.udla.consultas.puntos.adapter.out.ant;

import ec.edu.udla.consultas.puntos.application.exception.PuntosLicenciaProviderException;
import ec.edu.udla.consultas.puntos.application.port.out.PuntosLicenciaConsultaPort;
import ec.edu.udla.consultas.puntos.domain.Cedula;
import ec.edu.udla.consultas.puntos.domain.FuentePuntosLicencia;
import ec.edu.udla.consultas.puntos.domain.ResultadoPuntosLicencia;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

class ResilientPuntosLicenciaConsultaAdapterTests {

    private static final Cedula CEDULA = new Cedula("0000000000");

    @Test
    void opensOnProviderFailuresAndClosesAfterSuccessfulHalfOpenProbe() {
        AtomicInteger calls = new AtomicInteger();
        PuntosLicenciaConsultaPort provider = cedula -> {
            if (calls.incrementAndGet() <= 2) {
                throw new PuntosLicenciaProviderException(PuntosLicenciaProviderException.Kind.UNAVAILABLE);
            }
            return Optional.of(new ResultadoPuntosLicencia(
                    18, Instant.parse("2026-11-15T10:00:00Z"), FuentePuntosLicencia.SIMULATED));
        };
        CircuitBreaker breaker = CircuitBreaker.of("antPoints-test", testConfiguration());
        var adapter = new ResilientPuntosLicenciaConsultaAdapter(provider, breaker);

        assertProviderUnavailable(adapter);
        assertProviderUnavailable(adapter);
        assertThat(breaker.getState()).isEqualTo(CircuitBreaker.State.OPEN);

        assertProviderUnavailable(adapter);
        assertThat(calls).hasValue(2);
        breaker.transitionToHalfOpenState();
        assertThat(breaker.getState()).isEqualTo(CircuitBreaker.State.HALF_OPEN);

        assertThat(adapter.consultar(CEDULA)).hasValueSatisfying(result ->
                assertThat(result.fuente()).isEqualTo(FuentePuntosLicencia.SIMULATED));
        assertThat(breaker.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
        assertThat(calls).hasValue(3);
    }

    @Test
    void noDataAndNonProviderValidationExceptionsDoNotCountAsProviderFailures() {
        CircuitBreaker breaker = CircuitBreaker.of("antPoints-non-failure-test", testConfiguration());
        var noDataAdapter = new ResilientPuntosLicenciaConsultaAdapter(cedula -> Optional.empty(), breaker);

        assertThat(noDataAdapter.consultar(CEDULA)).isEmpty();
        assertThat(breaker.getMetrics().getNumberOfFailedCalls()).isZero();

        PuntosLicenciaConsultaPort invalidInput = cedula -> {
            throw new IllegalArgumentException("validation failure");
        };
        var invalidInputAdapter = new ResilientPuntosLicenciaConsultaAdapter(invalidInput, breaker);
        Throwable failure = catchThrowable(() -> invalidInputAdapter.consultar(CEDULA));

        assertThat(failure).isInstanceOf(IllegalArgumentException.class);
        assertThat(breaker.getMetrics().getNumberOfFailedCalls()).isZero();
        assertThat(breaker.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
    }

    private static CircuitBreakerConfig testConfiguration() {
        return CircuitBreakerConfig.custom()
                .slidingWindowSize(2)
                .minimumNumberOfCalls(2)
                .failureRateThreshold(50)
                .waitDurationInOpenState(Duration.ofSeconds(30))
                .permittedNumberOfCallsInHalfOpenState(1)
                .recordException(exception -> exception instanceof PuntosLicenciaProviderException)
                .build();
    }

    private static void assertProviderUnavailable(ResilientPuntosLicenciaConsultaAdapter adapter) {
        Throwable failure = catchThrowable(() -> adapter.consultar(CEDULA));
        assertThat(failure)
                .isInstanceOf(PuntosLicenciaProviderException.class)
                .extracting(value -> ((PuntosLicenciaProviderException) value).kind())
                .isEqualTo(PuntosLicenciaProviderException.Kind.UNAVAILABLE);
    }
}
