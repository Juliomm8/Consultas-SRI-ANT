package ec.edu.udla.consultas.contribuyentes.application.port.out;

import ec.edu.udla.consultas.contribuyentes.domain.Contribuyente;
import ec.edu.udla.consultas.contribuyentes.domain.Ruc;

import java.util.Optional;

/**
 * Outbound port for looking up a taxpayer by RUC.
 *
 * <p>{@link Optional#empty()} means the provider confirmed that the taxpayer does not exist.
 * Provider unavailability, timeouts, and unreadable responses must be represented by technical
 * exceptions and must never be converted to an empty result. The real provider adapter will define
 * and propagate the appropriate technical exceptions.</p>
 */
public interface ContribuyenteConsultaPort {

    Optional<Contribuyente> consultarPor(Ruc ruc);
}
