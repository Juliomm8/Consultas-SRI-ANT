package ec.edu.udla.consultas.puntos.domain;

import ec.edu.udla.consultas.puntos.domain.exception.CedulaFormatoInvalidoException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CedulaTest {

    @Test
    void acceptsTenAsciiDigitsAsStructuralFormatOnly() {
        Cedula cedula = new Cedula("0000000000");

        assertThat(cedula.value()).isEqualTo("0000000000");
        assertThat(cedula.toString()).doesNotContain(cedula.value()).contains("redacted");
    }

    @ParameterizedTest
    @ValueSource(strings = {"123456789", "12345678901", "12345A7890", "12345 7890", "１２３４５６７８９０"})
    void rejectsWrongLengthWhitespaceLettersAndUnicodeDigits(String value) {
        assertThatThrownBy(() -> new Cedula(value))
                .isInstanceOf(CedulaFormatoInvalidoException.class)
                .hasMessage("La cédula debe contener exactamente 10 dígitos ASCII.")
                .satisfies(exception -> assertThat(exception.getMessage()).doesNotContain(value));
    }

    @Test
    void rejectsNull() {
        assertThatThrownBy(() -> new Cedula(null))
                .isInstanceOf(CedulaFormatoInvalidoException.class)
                .hasMessage("La cédula debe contener exactamente 10 dígitos ASCII.");
    }

    @Test
    void rejectsEmptyValue() {
        assertThatThrownBy(() -> new Cedula(""))
                .isInstanceOf(CedulaFormatoInvalidoException.class)
                .hasMessage("La cédula debe contener exactamente 10 dígitos ASCII.");
    }
}
