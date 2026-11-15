package ec.edu.udla.consultas.contribuyentes.domain;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public record Contribuyente(
        Ruc ruc,
        TipoPersona tipoPersona,
        Map<String, ValorCampoContribuyente> datosSRI) {

    public static final Set<String> CAMPOS_SRI_OBSERVADOS = Set.of(
            "numeroRuc",
            "razonSocial",
            "estadoContribuyenteRuc",
            "actividadEconomicaPrincipal",
            "tipoContribuyente",
            "regimen",
            "categoria",
            "obligadoLlevarContabilidad",
            "agenteRetencion",
            "contribuyenteEspecial",
            "informacionFechasContribuyente",
            "representantesLegales",
            "motivoCancelacionSuspension",
            "contribuyenteFantasma",
            "transaccionesInexistente");

    private static final Set<String> CAMPOS_FECHAS_OBSERVADOS = Set.of(
            "fechaInicioActividades",
            "fechaCese",
            "fechaReinicioActividades",
            "fechaActualizacion");

    public Contribuyente(Ruc ruc, TipoPersona tipoPersona) {
        this(ruc, tipoPersona, Map.of());
    }

    public Contribuyente {
        if (ruc == null) {
            throw new IllegalArgumentException("El RUC del contribuyente es obligatorio.");
        }
        if (tipoPersona == null) {
            throw new IllegalArgumentException("El tipo de persona es obligatorio.");
        }
        if (datosSRI == null) {
            throw new IllegalArgumentException("Los datos SRI del contribuyente son obligatorios.");
        }
        if (!CAMPOS_SRI_OBSERVADOS.containsAll(datosSRI.keySet())) {
            throw new IllegalArgumentException("El contribuyente contiene propiedades SRI no permitidas.");
        }
        if (datosSRI.values().stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("Los campos SRI deben tener un tipo explícito.");
        }
        validateObservedDateFields(datosSRI.get("informacionFechasContribuyente"));
        datosSRI = Collections.unmodifiableMap(new LinkedHashMap<>(datosSRI));
    }

    @Override
    public String toString() {
        return "Contribuyente[ruc=redactado, tipoPersona=" + tipoPersona + ", datosSRI=redactados]";
    }

    private static void validateObservedDateFields(ValorCampoContribuyente dates) {
        if (dates == null || dates.tipoDato() != ValorCampoContribuyente.TipoDato.OBJETO) {
            return;
        }
        @SuppressWarnings("unchecked")
        Map<String, ValorCampoContribuyente> properties = (Map<String, ValorCampoContribuyente>) dates.valor();
        if (!CAMPOS_FECHAS_OBSERVADOS.containsAll(properties.keySet())) {
            throw new IllegalArgumentException("La información de fechas contiene propiedades no observadas.");
        }
    }
}
