package ec.edu.udla.consultas.contribuyentes.adapter.in.rest.dto;

import ec.edu.udla.consultas.contribuyentes.domain.Contribuyente;
import ec.edu.udla.consultas.contribuyentes.domain.TipoPersona;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** HTTP response model kept separate from the taxpayer domain. */
public record ContribuyenteResponseDto(
        String ruc,
        TipoPersona tipoPersona,
        Map<String, Object> datosSRI) {

    public ContribuyenteResponseDto {
        Objects.requireNonNull(ruc, "El RUC de respuesta es obligatorio.");
        Objects.requireNonNull(tipoPersona, "La clasificación de respuesta es obligatoria.");
        Objects.requireNonNull(datosSRI, "Los datos SRI de respuesta son obligatorios.");
        datosSRI = Collections.unmodifiableMap(new LinkedHashMap<>(datosSRI));
    }

    public static ContribuyenteResponseDto from(Contribuyente taxpayer) {
        Objects.requireNonNull(taxpayer, "El contribuyente es obligatorio.");
        Map<String, Object> fields = new LinkedHashMap<>();
        taxpayer.datosSRI().forEach((name, value) -> fields.put(name, value.asJsonValue()));
        return new ContribuyenteResponseDto(taxpayer.ruc().value(), taxpayer.tipoPersona(), fields);
    }

    @Override
    public String toString() {
        return "ContribuyenteResponseDto[ruc=redactado, tipoPersona=" + tipoPersona + ", datosSRI=redactados]";
    }
}
