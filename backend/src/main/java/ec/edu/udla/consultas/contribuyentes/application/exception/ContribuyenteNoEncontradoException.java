package ec.edu.udla.consultas.contribuyentes.application.exception;

public class ContribuyenteNoEncontradoException extends RuntimeException {

    public ContribuyenteNoEncontradoException() {
        super("No se encontró el contribuyente consultado.");
    }
}
