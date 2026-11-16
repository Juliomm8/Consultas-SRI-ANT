package ec.edu.udla.consultas.puntos.application.model;

import ec.edu.udla.consultas.puntos.domain.FuentePuntosLicencia;

import java.time.Instant;
import java.util.Objects;

/** Normalized application result including cache freshness metadata. */
public record PuntosLicenciaConsultaResultado(
        Integer totalPuntos,
        FuentePuntosLicencia source,
        FuentePuntosLicencia originSource,
        Instant fetchedAt,
        boolean stale,
        String warning) {

    public PuntosLicenciaConsultaResultado {
        if (totalPuntos == null || totalPuntos < 0) {
            throw new IllegalArgumentException("El total de puntos debe ser un entero no negativo.");
        }
        Objects.requireNonNull(source, "La fuente del resultado es obligatoria.");
        Objects.requireNonNull(originSource, "La procedencia original es obligatoria.");
        Objects.requireNonNull(fetchedAt, "La fecha original de obtención es obligatoria.");
        if (source == FuentePuntosLicencia.CACHE) {
            if (originSource != FuentePuntosLicencia.ANT && originSource != FuentePuntosLicencia.SIMULATED) {
                throw new IllegalArgumentException("La caché debe conservar una procedencia válida.");
            }
        } else if (source != originSource) {
            throw new IllegalArgumentException("La procedencia debe coincidir con la fuente del resultado.");
        }
    }
}
