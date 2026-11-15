package ec.edu.udla.consultas.puntos.domain.exception;

public class CedulaFormatoInvalidoException extends IllegalArgumentException {

    public CedulaFormatoInvalidoException() {
        super("La cédula debe contener exactamente 10 dígitos ASCII.");
    }
}
