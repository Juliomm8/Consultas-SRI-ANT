package ec.edu.udla.consultas.puntos.adapter.in.rest.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record ConsultarPuntosRequest(
        @NotBlank(message = "La cédula es obligatoria.")
        @Pattern(regexp = "^[0-9]{10}$", message = "La cédula debe contener exactamente 10 dígitos ASCII.")
        String cedula) {
}
