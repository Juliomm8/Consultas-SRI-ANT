package ec.edu.udla.consultas.contribuyentes.domain;

public record Contribuyente(Ruc ruc, TipoPersona tipoPersona) {

    public Contribuyente {
        if (ruc == null) {
            throw new IllegalArgumentException("El RUC del contribuyente es obligatorio.");
        }
        if (tipoPersona == null) {
            throw new IllegalArgumentException("El tipo de persona es obligatorio.");
        }
    }
}
