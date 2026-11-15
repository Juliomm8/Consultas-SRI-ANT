package ec.edu.udla.consultas.puntos.domain;

import java.time.Instant;
import java.util.Objects;

/** Normalized license-points result; it contains no personal identifier. */
public final class ResultadoPuntosLicencia {

    private final Integer totalPuntos;
    private final Instant fetchedAt;
    private final FuentePuntosLicencia fuente;

    public ResultadoPuntosLicencia(
            Integer totalPuntos,
            Instant fetchedAt,
            FuentePuntosLicencia fuente) {
        if (totalPuntos == null || totalPuntos < 0) {
            throw new IllegalArgumentException("El total de puntos debe ser un entero no negativo.");
        }
        this.totalPuntos = totalPuntos;
        this.fetchedAt = Objects.requireNonNull(fetchedAt, "La fecha de obtención es obligatoria.");
        this.fuente = Objects.requireNonNull(fuente, "La fuente del resultado es obligatoria.");
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

    @Override
    public String toString() {
        return "ResultadoPuntosLicencia[totalPuntos=<redacted>, fuente=" + fuente
                + ", fetchedAt=" + fetchedAt + "]";
    }
}
