package ec.edu.udla.consultas.puntos.domain;

import java.time.Instant;
import java.util.Objects;

/** Normalized license-points result; it contains no personal identifier. */
public final class ResultadoPuntosLicencia {

    private final Integer totalPuntos;
    private final Instant fetchedAt;
    private final FuentePuntosLicencia fuente;
    private final FuentePuntosLicencia originSource;

    public ResultadoPuntosLicencia(
            Integer totalPuntos,
            Instant fetchedAt,
            FuentePuntosLicencia fuente) {
        this(totalPuntos, fetchedAt, fuente, fuente);
    }

    public ResultadoPuntosLicencia(
            Integer totalPuntos,
            Instant fetchedAt,
            FuentePuntosLicencia fuente,
            FuentePuntosLicencia originSource) {
        if (totalPuntos == null || totalPuntos < 0) {
            throw new IllegalArgumentException("El total de puntos debe ser un entero no negativo.");
        }
        this.totalPuntos = totalPuntos;
        this.fetchedAt = Objects.requireNonNull(fetchedAt, "La fecha de obtención es obligatoria.");
        this.fuente = Objects.requireNonNull(fuente, "La fuente del resultado es obligatoria.");
        this.originSource = Objects.requireNonNull(originSource, "La procedencia original es obligatoria.");
        if (fuente == FuentePuntosLicencia.CACHE) {
            if (originSource != FuentePuntosLicencia.ANT && originSource != FuentePuntosLicencia.SIMULATED) {
                throw new IllegalArgumentException("La caché debe conservar una procedencia válida.");
            }
        } else if (originSource != fuente) {
            throw new IllegalArgumentException("La procedencia debe coincidir con la fuente del resultado.");
        }
    }

    public Integer totalPuntos() {
        return totalPuntos;
    }

    public Instant fetchedAt() {
        return fetchedAt;
    }

    public FuentePuntosLicencia fuente() {
        return fuente;
    }

    public FuentePuntosLicencia originSource() {
        return originSource;
    }

    @Override
    public String toString() {
        return "ResultadoPuntosLicencia[totalPuntos=<redacted>, fuente=" + fuente
                + ", originSource=" + originSource
                + ", fetchedAt=" + fetchedAt + "]";
    }
}
