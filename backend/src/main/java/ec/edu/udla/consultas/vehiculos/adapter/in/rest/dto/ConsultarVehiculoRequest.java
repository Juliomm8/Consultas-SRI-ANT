package ec.edu.udla.consultas.vehiculos.adapter.in.rest.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record ConsultarVehiculoRequest(
        @NotBlank(message = "La placa es obligatoria.")
        @Pattern(regexp = "^[A-Za-z0-9]+$", message = "La placa solo puede contener letras y números.")
        String placa) {
}
