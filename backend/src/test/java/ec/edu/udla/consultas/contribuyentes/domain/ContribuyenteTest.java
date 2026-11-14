package ec.edu.udla.consultas.contribuyentes.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ContribuyenteTest {

    @Test
    void acceptsRucAndPersonaNaturalType() {
        Ruc ruc = new Ruc("0000000000000");

        Contribuyente contribuyente = new Contribuyente(ruc, TipoPersona.NATURAL);

        assertEquals(ruc, contribuyente.ruc());
        assertEquals(TipoPersona.NATURAL, contribuyente.tipoPersona());
    }

    @Test
    void rejectsMissingRuc() {
        assertThrows(IllegalArgumentException.class, () -> new Contribuyente(null, TipoPersona.NATURAL));
    }

    @Test
    void rejectsMissingPersonType() {
        assertThrows(IllegalArgumentException.class, () -> new Contribuyente(new Ruc("0000000000000"), null));
    }

    @Test
    void allowsUndeterminedPersonTypeWithoutInferringClassification() {
        Contribuyente contribuyente = new Contribuyente(new Ruc("0000000000000"), TipoPersona.NO_DETERMINADA);

        assertEquals(TipoPersona.NO_DETERMINADA, contribuyente.tipoPersona());
    }
}
