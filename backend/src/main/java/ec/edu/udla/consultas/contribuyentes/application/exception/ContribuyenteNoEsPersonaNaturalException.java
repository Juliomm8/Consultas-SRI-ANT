package ec.edu.udla.consultas.contribuyentes.application.exception;

public class ContribuyenteNoEsPersonaNaturalException extends RuntimeException {

    public ContribuyenteNoEsPersonaNaturalException() {
        super("El contribuyente consultado no corresponde a una persona natural.");
    }
}
