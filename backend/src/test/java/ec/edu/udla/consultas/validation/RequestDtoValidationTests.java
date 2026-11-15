package ec.edu.udla.consultas.validation;

import ec.edu.udla.consultas.contribuyentes.adapter.in.rest.dto.ConsultarContribuyenteRequest;
import ec.edu.udla.consultas.puntos.adapter.in.rest.dto.ConsultarPuntosRequest;
import ec.edu.udla.consultas.vehiculos.adapter.in.rest.dto.ConsultarVehiculoRequest;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RequestDtoValidationTests {

    private static final Validator VALIDATOR = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void acceptsValidEmailAndSyntheticThirteenDigitRuc() {
        ConsultarContribuyenteRequest request = new ConsultarContribuyenteRequest(
                "usuario@ejemplo.com",
                "0000000000000");

        assertTrue(VALIDATOR.validate(request).isEmpty());
    }

    @Test
    void rejectsInvalidEmail() {
        ConsultarContribuyenteRequest request = new ConsultarContribuyenteRequest(
                "no-es-correo",
                "0000000000000");

        assertTrue(invalidProperties(request).contains("email"));
    }

    @Test
    void rejectsMissingEmail() {
        ConsultarContribuyenteRequest request = new ConsultarContribuyenteRequest(
                null,
                "0000000000000");

        assertTrue(invalidProperties(request).contains("email"));
    }

    @Test
    void acceptsTenDigitCedulaAsStructuralSyntaxOnly() {
        ConsultarPuntosRequest request = new ConsultarPuntosRequest("0000000000");

        assertTrue(VALIDATOR.validate(request).isEmpty());
    }

    @Test
    void rejectsMissingCedula() {
        ConsultarPuntosRequest request = new ConsultarPuntosRequest(null);

        assertTrue(invalidProperties(request).contains("cedula"));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "",
            "123456789",
            "12345678901",
            "12345A7890",
            "12345 7890",
            "１２３４５６７８９０",
            "१२३४५६७८९०"
    })
    void rejectsCedulaWithWrongLengthWhitespaceLettersOrUnicodeDigits(String cedula) {
        ConsultarPuntosRequest request = new ConsultarPuntosRequest(cedula);

        assertTrue(invalidProperties(request).contains("cedula"));
    }

    @Test
    void acceptsAlphanumericPlateWithoutImposingLength() {
        ConsultarVehiculoRequest request = new ConsultarVehiculoRequest("AB12345678901234567890");

        assertTrue(VALIDATOR.validate(request).isEmpty());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "ABC-1234", "ABC 1234", "ABC_1234"})
    void rejectsBlankPlateAndClearlyInvalidCharacters(String placa) {
        ConsultarVehiculoRequest request = new ConsultarVehiculoRequest(placa);

        assertTrue(invalidProperties(request).contains("placa"));
    }

    @Test
    void reportsSeveralInvalidFieldsInOneValidationPass() {
        ConsultarContribuyenteRequest request = new ConsultarContribuyenteRequest("", "12X");
        Set<String> invalidProperties = invalidProperties(request);

        assertFalse(invalidProperties.isEmpty());
        assertEquals(Set.of("email", "ruc"), invalidProperties);
    }

    private Set<String> invalidProperties(Object request) {
        return VALIDATOR.validate(request).stream()
                .map(violation -> violation.getPropertyPath().toString())
                .collect(Collectors.toSet());
    }
}
