package ec.edu.udla.consultas.puntos.application.port.out;

import ec.edu.udla.consultas.puntos.domain.Cedula;
import ec.edu.udla.consultas.puntos.domain.ResultadoPuntosLicencia;

import java.util.Optional;

/** Local-only cache boundary for synthetic results, isolated from official ANT data. */
public interface PuntosLicenciaSimulationCachePort {

    /** Accepts only a SIMULATED result. */
    void guardarResultado(Cedula cedula, ResultadoPuntosLicencia resultado);

    /** Returns CACHE while preserving SIMULATED as originSource. */
    Optional<ResultadoPuntosLicencia> obtenerUltimoResultado(Cedula cedula);
}
