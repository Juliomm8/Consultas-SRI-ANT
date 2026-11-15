package ec.edu.udla.consultas.puntos.adapter.out.ant;

import ec.edu.udla.consultas.puntos.application.exception.PuntosLicenciaProviderException;
import ec.edu.udla.consultas.puntos.application.port.out.PuntosLicenciaConsultaPort;
import ec.edu.udla.consultas.puntos.domain.Cedula;
import ec.edu.udla.consultas.puntos.domain.ResultadoPuntosLicencia;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;

import java.util.Optional;
import java.util.Objects;

/** Infrastructure decorator that protects the provider port with Circuit Breaker. */
public final class ResilientPuntosLicenciaConsultaAdapter implements PuntosLicenciaConsultaPort {

    private final PuntosLicenciaConsultaPort delegate;
    private final CircuitBreaker circuitBreaker;

    public ResilientPuntosLicenciaConsultaAdapter(
            PuntosLicenciaConsultaPort delegate,
            CircuitBreaker circuitBreaker) {
        this.delegate = Objects.requireNonNull(delegate, "El adaptador proveedor es obligatorio.");
        this.circuitBreaker = Objects.requireNonNull(circuitBreaker, "El circuito es obligatorio.");
    }

    @Override
    public Optional<ResultadoPuntosLicencia> consultar(Cedula cedula) {
        try {
            return circuitBreaker.executeSupplier(() -> delegate.consultar(cedula));
        } catch (CallNotPermittedException exception) {
            throw new PuntosLicenciaProviderException(PuntosLicenciaProviderException.Kind.UNAVAILABLE);
        }
    }
}
