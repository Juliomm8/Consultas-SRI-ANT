package ec.edu.udla.consultas.contribuyentes.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

    @Test
    void preservesAllowedProviderFieldsAsTypedImmutableJsonValues() {
        List<ValorCampoContribuyente> representatives = new ArrayList<>();
        representatives.add(ValorCampoContribuyente.objeto(Map.of(
                "nombreRepresentante", ValorCampoContribuyente.texto("SYNTHETIC_NAME"),
                "activo", ValorCampoContribuyente.booleano(true),
                "detalleOpcional", ValorCampoContribuyente.nulo())));
        Map<String, ValorCampoContribuyente> dates = new LinkedHashMap<>();
        dates.put("fechaInicioActividades", ValorCampoContribuyente.texto("2020-01-02"));
        dates.put("fechaCese", ValorCampoContribuyente.nulo());
        Map<String, ValorCampoContribuyente> fields = new LinkedHashMap<>();
        fields.put("numeroRuc", ValorCampoContribuyente.texto("0000000000000"));
        fields.put("razonSocial", ValorCampoContribuyente.texto("SYNTHETIC_NAME"));
        fields.put("obligadoLlevarContabilidad", ValorCampoContribuyente.booleano(false));
        fields.put("categoria", ValorCampoContribuyente.numero(new BigDecimal("2.50")));
        fields.put("motivoCancelacionSuspension", ValorCampoContribuyente.nulo());
        fields.put("informacionFechasContribuyente", ValorCampoContribuyente.objeto(dates));
        fields.put("representantesLegales", ValorCampoContribuyente.arreglo(representatives));

        Contribuyente taxpayer = new Contribuyente(new Ruc("0000000000000"), TipoPersona.NATURAL, fields);
        Map<?, ?> serializedDates = (Map<?, ?>) taxpayer.datosSRI()
                .get("informacionFechasContribuyente").asJsonValue();
        List<?> serializedRepresentatives = (List<?>) taxpayer.datosSRI()
                .get("representantesLegales").asJsonValue();

        assertEquals("0000000000000", taxpayer.datosSRI().get("numeroRuc").asJsonValue());
        assertEquals(new BigDecimal("2.50"), taxpayer.datosSRI().get("categoria").asJsonValue());
        assertEquals(false, taxpayer.datosSRI().get("obligadoLlevarContabilidad").asJsonValue());
        assertTrue(taxpayer.datosSRI().containsKey("motivoCancelacionSuspension"));
        assertEquals(null, taxpayer.datosSRI().get("motivoCancelacionSuspension").asJsonValue());
        assertEquals("2020-01-02", serializedDates.get("fechaInicioActividades"));
        assertTrue(serializedDates.containsKey("fechaCese"));
        assertEquals(null, serializedDates.get("fechaCese"));
        assertEquals("SYNTHETIC_NAME", ((Map<?, ?>) serializedRepresentatives.get(0)).get("nombreRepresentante"));
        assertThrows(UnsupportedOperationException.class,
                () -> taxpayer.datosSRI().put("regimen", ValorCampoContribuyente.texto("SYNTHETIC")));
        dates.put("fechaActualizacion", ValorCampoContribuyente.texto("2024-01-01"));
        representatives.add(ValorCampoContribuyente.nulo());
        assertFalse(serializedDates.containsKey("fechaActualizacion"));
        assertEquals(1, serializedRepresentatives.size());
        assertThrows(UnsupportedOperationException.class,
                () -> ((Map<?, ?>) taxpayer.datosSRI()
                        .get("informacionFechasContribuyente").valor()).clear());
        assertFalse(taxpayer.toString().contains("0000000000000"));
        assertFalse(taxpayer.toString().contains("SYNTHETIC_NAME"));
    }

    @Test
    void excludesUnobservedProviderProperties() {
        assertThrows(IllegalArgumentException.class, () -> new Contribuyente(
                new Ruc("0000000000000"),
                TipoPersona.NATURAL,
                Map.of("unobservedField", ValorCampoContribuyente.texto("synthetic"))));
    }

    @Test
    void excludesUnobservedPropertiesInsideKnownDateObject() {
        assertThrows(IllegalArgumentException.class, () -> new Contribuyente(
                new Ruc("0000000000000"),
                TipoPersona.NATURAL,
                Map.of("informacionFechasContribuyente", ValorCampoContribuyente.objeto(Map.of(
                        "unobservedDate", ValorCampoContribuyente.texto("synthetic"))))));
    }
}
