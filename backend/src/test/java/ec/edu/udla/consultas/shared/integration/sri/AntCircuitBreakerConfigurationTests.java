package ec.edu.udla.consultas.shared.integration.sri;

import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import ec.edu.udla.consultas.puntos.application.exception.PuntosLicenciaProviderException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
class AntCircuitBreakerConfigurationTests {

    @Autowired
    private CircuitBreakerRegistry circuitBreakerRegistry;

    @Test
    void registersConfiguredAntCircuitBreakerWithoutOpeningExternalConnections() {
        var circuitBreaker = circuitBreakerRegistry.circuitBreaker("antPoints");

        assertEquals(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED,
                circuitBreaker.getCircuitBreakerConfig().getSlidingWindowType());
        assertEquals(5, circuitBreaker.getCircuitBreakerConfig().getSlidingWindowSize());
        assertEquals(50.0f, circuitBreaker.getCircuitBreakerConfig().getFailureRateThreshold());
        assertEquals(true, circuitBreaker.getCircuitBreakerConfig().getRecordExceptionPredicate()
                .test(new PuntosLicenciaProviderException(PuntosLicenciaProviderException.Kind.UNAVAILABLE)));
        assertEquals(false, circuitBreaker.getCircuitBreakerConfig().getRecordExceptionPredicate()
                .test(new IllegalArgumentException("synthetic validation failure")));
    }
}
