package ec.edu.udla.consultas.puntos.adapter.in.rest.dto;

import java.time.Instant;

/** Points API envelope; it does not expose the requested identification. */
public record PuntosLicenciaResponseDto(Data data, Meta meta) {

    public record Data(Integer totalPuntos) {
    }

    public record Meta(
            String requestId,
            String source,
            Instant retrievedAt,
            Instant fetchedAt,
            boolean stale,
            String warning) {
    }
}
