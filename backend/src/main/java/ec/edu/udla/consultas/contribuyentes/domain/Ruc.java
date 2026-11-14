package ec.edu.udla.consultas.contribuyentes.domain;

import ec.edu.udla.consultas.contribuyentes.domain.exception.RucFormatoInvalidoException;

public record Ruc(String value) {

    public Ruc {
        if (!hasValidFormat(value)) {
            throw new RucFormatoInvalidoException();
        }
    }

    private static boolean hasValidFormat(String value) {
        if (value == null || value.length() != 13) {
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
        return "Ruc[redacted]";
    }
}
