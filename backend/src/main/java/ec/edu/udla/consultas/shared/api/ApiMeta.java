package ec.edu.udla.consultas.shared.api;

import java.time.Instant;

public record ApiMeta(String requestId, String source, Instant retrievedAt) {
}
