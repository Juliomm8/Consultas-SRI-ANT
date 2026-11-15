package ec.edu.udla.consultas.puntos.application.port.out;

import ec.edu.udla.consultas.puntos.domain.Cedula;
import ec.edu.udla.consultas.puntos.domain.ResultadoPuntosLicencia;

import java.util.Optional;

/**
 * Output port for retaining the latest validated ANT result, keyed by the
 * cédula supplied to the official consultation flow. This contract does not
 * derive cédulas from RUCs.
 */
public interface PuntosLicenciaCachePort {

    /** Only a validated result whose source is ANT may be saved. */
    void guardarResultado(Cedula cedula, ResultadoPuntosLicencia resultado);

    /**
     * Returns empty only when no unexpired entry exists. A returned result has
     * source CACHE and retains the original fetchedAt; technical cache failures
     * are reported with PuntosLicenciaCacheException.
     */
    Optional<ResultadoPuntosLicencia> obtenerUltimoResultado(Cedula cedula);
}
