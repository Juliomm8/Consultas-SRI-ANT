package ec.edu.udla.consultas.contribuyentes.domain.exception;

public class RucFormatoInvalidoException extends IllegalArgumentException {

    public RucFormatoInvalidoException() {
        super("El RUC debe contener exactamente 13 dígitos ASCII.");
    }
}
