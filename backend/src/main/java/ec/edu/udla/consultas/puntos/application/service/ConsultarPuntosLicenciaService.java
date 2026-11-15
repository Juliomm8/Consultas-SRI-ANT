package ec.edu.udla.consultas.puntos.application.service;

import ec.edu.udla.consultas.puntos.application.exception.PuntosLicenciaCacheException;
import ec.edu.udla.consultas.puntos.application.exception.PuntosLicenciaProviderException;
import ec.edu.udla.consultas.puntos.application.exception.PuntosLicenciaSinInformacionException;
import ec.edu.udla.consultas.puntos.application.model.PuntosLicenciaConsultaResultado;
import ec.edu.udla.consultas.puntos.application.port.in.ConsultarPuntosLicenciaUseCase;
import ec.edu.udla.consultas.puntos.application.port.out.PuntosLicenciaCachePort;
import ec.edu.udla.consultas.puntos.application.port.out.PuntosLicenciaConsultaPort;
import ec.edu.udla.consultas.puntos.domain.Cedula;
import ec.edu.udla.consultas.puntos.domain.FuentePuntosLicencia;
import ec.edu.udla.consultas.puntos.domain.ResultadoPuntosLicencia;

import java.util.Objects;
import java.util.Optional;

/** Coordinates a provider query and the last-known-good cache fallback. */
public final class ConsultarPuntosLicenciaService implements ConsultarPuntosLicenciaUseCase {

    private static final String SIMULATION_WARNING =
            "Resultado SIMULATED para pruebas locales; no es información oficial de ANT.";
    private static final String CACHE_WARNING =
            "ANT no respondió; se muestra el último resultado válido guardado y puede estar desactualizado.";

    private final PuntosLicenciaConsultaPort provider;
    private final Optional<PuntosLicenciaCachePort> cache;

    public ConsultarPuntosLicenciaService(
            PuntosLicenciaConsultaPort provider,
            Optional<PuntosLicenciaCachePort> cache) {
        this.provider = Objects.requireNonNull(provider, "El puerto de consulta es obligatorio.");
        this.cache = Objects.requireNonNull(cache, "La configuración de caché es obligatoria.");
    }

    @Override
    public PuntosLicenciaConsultaResultado consultar(Cedula cedula) {
        Objects.requireNonNull(cedula, "La cédula es obligatoria.");

        Optional<ResultadoPuntosLicencia> providerResult;
        try {
            providerResult = provider.consultar(cedula);
            if (providerResult == null) {
                throw new PuntosLicenciaProviderException(
                        PuntosLicenciaProviderException.Kind.INVALID_RESPONSE);
            }
        } catch (PuntosLicenciaProviderException failure) {
            return fallbackOrRethrow(cedula, failure);
        }

        if (providerResult.isEmpty()) {
            throw new PuntosLicenciaSinInformacionException();
        }

        ResultadoPuntosLicencia result = providerResult.orElseThrow();
        if (result.fuente() == FuentePuntosLicencia.SIMULATED) {
            return toOutcome(result, false, SIMULATION_WARNING);
        }
        if (result.fuente() != FuentePuntosLicencia.ANT) {
            return fallbackOrRethrow(cedula, new PuntosLicenciaProviderException(
                    PuntosLicenciaProviderException.Kind.INVALID_RESPONSE));
        }

        cache.ifPresent(cachePort -> {
            try {
                cachePort.guardarResultado(cedula, result);
            } catch (PuntosLicenciaCacheException ignored) {
                // The live validated provider result remains usable. The Redis adapter logs safe metadata.
            }
        });
        return toOutcome(result, false, null);
    }

    private PuntosLicenciaConsultaResultado fallbackOrRethrow(
            Cedula cedula,
            PuntosLicenciaProviderException providerFailure) {
        if (cache.isPresent()) {
            try {
                Optional<ResultadoPuntosLicencia> cached = cache.orElseThrow().obtenerUltimoResultado(cedula);
                if (cached != null && cached.isPresent()
                        && cached.orElseThrow().fuente() == FuentePuntosLicencia.CACHE) {
                    return toOutcome(cached.orElseThrow(), true, CACHE_WARNING);
                }
            } catch (PuntosLicenciaCacheException ignored) {
                // Preserve the provider failure; the Redis adapter logs safe metadata.
            }
        }
        throw providerFailure;
    }

    private static PuntosLicenciaConsultaResultado toOutcome(
            ResultadoPuntosLicencia result,
            boolean stale,
            String warning) {
        return new PuntosLicenciaConsultaResultado(
                result.totalPuntos(), result.fuente(), result.fetchedAt(), stale, warning);
    }
}
