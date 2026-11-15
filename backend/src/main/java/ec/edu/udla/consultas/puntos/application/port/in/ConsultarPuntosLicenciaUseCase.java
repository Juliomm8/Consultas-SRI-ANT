package ec.edu.udla.consultas.puntos.application.port.in;

import ec.edu.udla.consultas.puntos.application.model.PuntosLicenciaConsultaResultado;
import ec.edu.udla.consultas.puntos.domain.Cedula;

/** Application entry point for a license-points consultation. */
public interface ConsultarPuntosLicenciaUseCase {

    PuntosLicenciaConsultaResultado consultar(Cedula cedula);
}
