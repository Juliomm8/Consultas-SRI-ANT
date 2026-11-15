package ec.edu.udla.consultas.vehiculos.domain;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Minimal vehicle model containing only fields observed in an authorized SRI response. */
public record Vehiculo(Map<String, ValorCampoVehiculo> atributos) {

    public static final Set<String> CAMPOS_SRI_OBSERVADOS = Set.of(
            "codigoVehiculo",
            "numeroPlaca",
            "numeroCamvCpn",
            "colorVehiculo1",
            "colorVehiculo2",
            "cilindraje",
            "nombreClase",
            "descripcionMarca",
            "descripcionModelo",
            "anioAuto",
            "descripcionPais",
            "mensajeMotivoAuto",
            "aplicaCuota",
            "fechaUltimaMatricula",
            "fechaCaducidadMatricula",
            "fechaCompraRegistro",
            "fechaRevision",
            "descripcionCanton",
            "descripcionServicio",
            "ultimoAnioPagado",
            "estadoExoneracion",
            "prohibidoEnajenar",
            "observacion",
            "verMensajeElectricos");

    public Vehiculo {
        Objects.requireNonNull(atributos, "Los atributos del vehículo son obligatorios.");
        if (atributos.isEmpty() || !CAMPOS_SRI_OBSERVADOS.containsAll(atributos.keySet())) {
            throw new IllegalArgumentException("El vehículo debe contener campos SRI observados.");
        }
        if (atributos.values().stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("Los campos del vehículo deben tener tipo explícito.");
        }
        atributos = Collections.unmodifiableMap(new LinkedHashMap<>(atributos));
    }

    @Override
    public String toString() {
        return "Vehiculo[atributos=redactados]";
    }
}
