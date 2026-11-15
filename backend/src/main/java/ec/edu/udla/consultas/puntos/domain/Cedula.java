package ec.edu.udla.consultas.puntos.domain;

import ec.edu.udla.consultas.puntos.domain.exception.CedulaFormatoInvalidoException;

/** Ecuadorian cédula value object with structural validation only. */
public record Cedula(String value) {

    public Cedula {
        if (!hasValidFormat(value)) {
            throw new CedulaFormatoInvalidoException();
        }
    }

    private static boolean hasValidFormat(String value) {
        if (value == null || value.length() != 10) {
            return false;
        }

        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            if (character < '0' || character > '9') {
                return false;
            }
        }
        return true;
    }

    @Override
    public String toString() {
        return "Cedula[redacted]";
    }
}
