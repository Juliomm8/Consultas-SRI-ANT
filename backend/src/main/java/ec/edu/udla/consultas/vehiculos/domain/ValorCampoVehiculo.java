package ec.edu.udla.consultas.vehiculos.domain;

import java.util.Objects;

/** A scalar SRI vehicle field whose JSON type is preserved without assuming a business type. */
public record ValorCampoVehiculo(TipoDato tipoDato, String valor) {

    public enum TipoDato {
        TEXTO,
        NUMERO,
        BOOLEANO,
        NULO
    }

    public ValorCampoVehiculo {
        Objects.requireNonNull(tipoDato, "El tipo de dato del campo es obligatorio.");
        if (tipoDato == TipoDato.NULO && valor != null) {
            throw new IllegalArgumentException("Un campo nulo no puede contener un valor.");
        }
        if (tipoDato != TipoDato.NULO && valor == null) {
            throw new IllegalArgumentException("El valor del campo es obligatorio.");
        }
    }

    @Override
    public String toString() {
        return "ValorCampoVehiculo[tipoDato=" + tipoDato + ", valor=redactado]";
    }
}
