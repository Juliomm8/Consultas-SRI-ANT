package ec.edu.udla.consultas.vehiculos.adapter.in.rest.dto;

import ec.edu.udla.consultas.vehiculos.domain.ValorCampoVehiculo;
import ec.edu.udla.consultas.vehiculos.domain.Vehiculo;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** HTTP response model kept separate from the vehicle domain. */
public record VehiculoResponseDto(Map<String, Object> camposSRI) {

    public VehiculoResponseDto {
        Objects.requireNonNull(camposSRI, "Los campos SRI del vehículo son obligatorios.");
        camposSRI = Collections.unmodifiableMap(new LinkedHashMap<>(camposSRI));
    }

    public static VehiculoResponseDto from(Vehiculo vehicle) {
        Objects.requireNonNull(vehicle, "El vehículo es obligatorio.");
        Map<String, Object> fields = new LinkedHashMap<>();
        vehicle.atributos().forEach((name, value) -> fields.put(name, asJsonValue(value)));
        return new VehiculoResponseDto(fields);
    }

    @Override
    public String toString() {
        return "VehiculoResponseDto[camposSRI=redactados]";
    }

    private static Object asJsonValue(ValorCampoVehiculo value) {
        return switch (value.tipoDato()) {
            case TEXTO -> value.valor();
            case NUMERO -> new BigDecimal(value.valor());
            case BOOLEANO -> Boolean.valueOf(value.valor());
            case NULO -> null;
        };
    }
}
