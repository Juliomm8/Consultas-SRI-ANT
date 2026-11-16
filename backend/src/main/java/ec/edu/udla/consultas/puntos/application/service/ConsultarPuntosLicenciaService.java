package ec.edu.udla.consultas.puntos.application.service;

import ec.edu.udla.consultas.puntos.application.exception.PuntosLicenciaCacheException;
import ec.edu.udla.consultas.puntos.application.exception.PuntosLicenciaProviderException;
import ec.edu.udla.consultas.puntos.application.exception.PuntosLicenciaSinInformacionException;
import ec.edu.udla.consultas.puntos.application.model.PuntosLicenciaConsultaResultado;
import ec.edu.udla.consultas.puntos.application.port.in.ConsultarPuntosLicenciaUseCase;
import ec.edu.udla.consultas.puntos.application.port.out.PuntosLicenciaCachePort;
import ec.edu.udla.consultas.puntos.application.port.out.PuntosLicenciaConsultaPort;
import ec.edu.udla.consultas.puntos.application.port.out.PuntosLicenciaSimulationCachePort;
import ec.edu.udla.consultas.puntos.domain.Cedula;
import ec.edu.udla.consultas.puntos.domain.FuentePuntosLicencia;
import ec.edu.udla.consultas.puntos.domain.ResultadoPuntosLicencia;

import java.util.Objects;
import java.util.Optional;

/** Coordinates a provider query and the last-known-good cache fallback. */
public final class ConsultarPuntosLicenciaService implements ConsultarPuntosLicenciaUseCase {

    private static final String SIMULATION_WARNING =
            "Resultado SIMULATED para pruebas locales; no es información oficial de ANT.";
    private static final String ANT_CACHE_WARNING =
            "ANT no respondió; se muestra el último resultado válido guardado y puede estar desactualizado.";
    private static final String SIMULATION_CACHE_WARNING =
            "El proveedor simulado falló; se recuperó un resultado SIMULATED de la caché local. "
                    + "No es información oficial de ANT y puede estar desactualizado.";

    private final PuntosLicenciaConsultaPort provider;
    private final Optional<PuntosLicenciaCachePort> antCache;
    private final Optional<PuntosLicenciaSimulationCachePort> simulationCache;

    public ConsultarPuntosLicenciaService(
            PuntosLicenciaConsultaPort provider,
            Optional<PuntosLicenciaCachePort> cache) {
        this(provider, cache, Optional.empty());
    }

    public ConsultarPuntosLicenciaService(
            PuntosLicenciaConsultaPort provider,
            Optional<PuntosLicenciaCachePort> antCache,
            Optional<PuntosLicenciaSimulationCachePort> simulationCache) {
        this.provider = Objects.requireNonNull(provider, "El puerto de consulta es obligatorio.");
        this.antCache = Objects.requireNonNull(antCache, "La configuración de caché ANT es obligatoria.");
        this.simulationCache = Objects.requireNonNull(
                simulationCache, "La configuración de caché simulada es obligatoria.");
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
            simulationCache.ifPresent(cachePort -> saveWithoutDiscardingResult(cachePort, cedula, result));
            return toOutcome(result, false, SIMULATION_WARNING);
        }
        if (result.fuente() != FuentePuntosLicencia.ANT) {
            return fallbackOrRethrow(cedula, new PuntosLicenciaProviderException(
                    PuntosLicenciaProviderException.Kind.INVALID_RESPONSE));
        }

        antCache.ifPresent(cachePort -> saveWithoutDiscardingResult(cachePort, cedula, result));
        return toOutcome(result, false, null);
    }

    private PuntosLicenciaConsultaResultado fallbackOrRethrow(
            Cedula cedula,
            PuntosLicenciaProviderException providerFailure) {
        Optional<PuntosLicenciaConsultaResultado> cachedSimulation = readSimulationCache(cedula);
        if (cachedSimulation.isPresent()) {
            return cachedSimulation.orElseThrow();
        }
        Optional<PuntosLicenciaConsultaResultado> cachedAnt = readAntCache(cedula);
        if (cachedAnt.isPresent()) {
            return cachedAnt.orElseThrow();
        }
        throw providerFailure;
    }

    private Optional<PuntosLicenciaConsultaResultado> readSimulationCache(Cedula cedula) {
        if (simulationCache.isEmpty()) {
            return Optional.empty();
        }
        try {
            Optional<ResultadoPuntosLicencia> cached = simulationCache.orElseThrow()
                    .obtenerUltimoResultado(cedula);
            if (isCacheFrom(cached, FuentePuntosLicencia.SIMULATED)) {
                return Optional.of(toOutcome(cached.orElseThrow(), true, SIMULATION_CACHE_WARNING));
            }
        } catch (PuntosLicenciaCacheException ignored) {
            // Preserve the provider failure; the Redis adapter logs safe metadata.
        }
        return Optional.empty();
    }

    private Optional<PuntosLicenciaConsultaResultado> readAntCache(Cedula cedula) {
        if (antCache.isEmpty()) {
            return Optional.empty();
        }
        try {
            Optional<ResultadoPuntosLicencia> cached = antCache.orElseThrow().obtenerUltimoResultado(cedula);
            if (isCacheFrom(cached, FuentePuntosLicencia.ANT)) {
                return Optional.of(toOutcome(cached.orElseThrow(), true, ANT_CACHE_WARNING));
            }
        } catch (PuntosLicenciaCacheException ignored) {
            // Preserve the provider failure; the Redis adapter logs safe metadata.
        }
        return Optional.empty();
    }

    private static boolean isCacheFrom(
            Optional<ResultadoPuntosLicencia> cached,
            FuentePuntosLicencia expectedOrigin) {
        return cached != null && cached.isPresent()
                && cached.orElseThrow().fuente() == FuentePuntosLicencia.CACHE
                && cached.orElseThrow().originSource() == expectedOrigin;
    }

    private static void saveWithoutDiscardingResult(
            PuntosLicenciaCachePort cachePort,
            Cedula cedula,
            ResultadoPuntosLicencia result) {
        try {
            cachePort.guardarResultado(cedula, result);
        } catch (PuntosLicenciaCacheException ignored) {
            // The validated provider result remains usable; the adapter logs safe metadata.
        }
    }

    private static void saveWithoutDiscardingResult(
            PuntosLicenciaSimulationCachePort cachePort,
            Cedula cedula,
            ResultadoPuntosLicencia result) {
        try {
            cachePort.guardarResultado(cedula, result);
        } catch (PuntosLicenciaCacheException ignored) {
            // The local fixture remains usable; the adapter logs safe metadata.
        }
    }

    private static PuntosLicenciaConsultaResultado toOutcome(
            ResultadoPuntosLicencia result,
            boolean stale,
            String warning) {
        return new PuntosLicenciaConsultaResultado(
                result.totalPuntos(), result.fuente(), result.originSource(), result.fetchedAt(), stale, warning);
    }
}
