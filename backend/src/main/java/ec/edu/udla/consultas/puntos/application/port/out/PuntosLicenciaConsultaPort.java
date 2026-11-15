package ec.edu.udla.consultas.puntos.application.port.out;

import ec.edu.udla.consultas.puntos.domain.Cedula;
import ec.edu.udla.consultas.puntos.domain.ResultadoPuntosLicencia;

import java.util.Optional;

/**
 * Provider boundary for a normalized license-points result.
 *
 * <p>An empty value means the provider returned no information; it never means
 * zero points. Technical failures must be represented by
 * {@code PuntosLicenciaProviderException}.</p>
 */
public interface PuntosLicenciaConsultaPort {

    Optional<ResultadoPuntosLicencia> consultar(Cedula cedula);
}
