package ec.edu.udla.consultas.contribuyentes.application.exception;

public class TipoPersonaNoDeterminadaException extends RuntimeException {

    public TipoPersonaNoDeterminadaException() {
        super("No se pudo determinar el tipo de persona del contribuyente.");
    }
}
