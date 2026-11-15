package ec.edu.udla.consultas.puntos.application.exception;

/** Indicates that the provider returned no points information. */
public final class PuntosLicenciaSinInformacionException extends RuntimeException {

    public PuntosLicenciaSinInformacionException() {
        super("No se encontró información de puntos de licencia.");
    }
}
