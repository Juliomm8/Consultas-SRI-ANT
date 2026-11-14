package ec.edu.udla.consultas.contribuyentes.domain;

import ec.edu.udla.consultas.contribuyentes.domain.exception.RucFormatoInvalidoException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RucTest {

    @Test
    void acceptsThirteenAsciiDigits() {
        Ruc ruc = new Ruc("0000000000000");

        assertEquals("0000000000000", ruc.value());
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "123456789012", "12345678901234", "12345678901A3", "١٢٣٤٥٦٧٨٩٠١٢٣"})
    void rejectsNullEmptyWrongLengthLettersAndUnicodeDigits(String value) {
        RucFormatoInvalidoException exception = assertThrows(
                RucFormatoInvalidoException.class,
                () -> new Ruc(value));

        if (value != null && !value.isEmpty()) {
            assertFalse(exception.getMessage().contains(value));
        }
    }

    @Test
    void doesNotRevealTheRucWhenConvertedToString() {
        Ruc ruc = new Ruc("0000000000000");

        assertEquals("Ruc[redacted]", ruc.toString());
    }
}
