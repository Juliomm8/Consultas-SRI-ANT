package ec.edu.udla.consultas.puntos.adapter.in.rest.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record ConsultarPuntosRequest(
        @NotBlank(message = "El RUC es obligatorio.")
        @Pattern(regexp = "^[0-9]{13}$", message = "El RUC debe contener exactamente 13 dígitos.")
        String ruc) {
}
