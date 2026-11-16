package ec.edu.udla.consultas.puntos.application;

import ec.edu.udla.consultas.puntos.application.exception.PuntosLicenciaCacheException;
import ec.edu.udla.consultas.puntos.application.exception.PuntosLicenciaProviderException;
import ec.edu.udla.consultas.puntos.application.exception.PuntosLicenciaSinInformacionException;
import ec.edu.udla.consultas.puntos.application.port.out.PuntosLicenciaCachePort;
import ec.edu.udla.consultas.puntos.application.port.out.PuntosLicenciaConsultaPort;
import ec.edu.udla.consultas.puntos.application.port.out.PuntosLicenciaSimulationCachePort;
import ec.edu.udla.consultas.puntos.application.service.ConsultarPuntosLicenciaService;
import ec.edu.udla.consultas.puntos.domain.Cedula;
import ec.edu.udla.consultas.puntos.domain.FuentePuntosLicencia;
import ec.edu.udla.consultas.puntos.domain.ResultadoPuntosLicencia;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

class ConsultarPuntosLicenciaServiceTests {

    private static final Cedula CEDULA = new Cedula("0000000000");
    private static final Instant FETCHED_AT = Instant.parse("2026-11-15T10:00:00Z");

    @Test
    void storesSimulatedResultOnlyThroughItsSeparateCachePort() {
        RecordingCache cache = new RecordingCache();
        var service = service(cedula -> Optional.of(
                result(20, FuentePuntosLicencia.SIMULATED, FETCHED_AT)), Optional.empty(), Optional.of(cache));

        var result = service.consultar(CEDULA);

        assertThat(result.totalPuntos()).isEqualTo(20);
        assertThat(result.source()).isEqualTo(FuentePuntosLicencia.SIMULATED);
        assertThat(result.stale()).isFalse();
        assertThat(result.warning()).contains("no es información oficial de ANT");
        assertThat(cache.simulationSaved.get()).isNotNull();
        assertThat(cache.simulationSaved.get().fuente()).isEqualTo(FuentePuntosLicencia.SIMULATED);
        assertThat(cache.antSaved.get()).isNull();
    }

    @Test
    void storesOnlyFreshAntResultsAndPreservesConfirmedZero() {
        RecordingCache cache = new RecordingCache();
        var service = service(cedula -> Optional.of(
                result(0, FuentePuntosLicencia.ANT, FETCHED_AT)), Optional.of(cache));

        var result = service.consultar(CEDULA);

        assertThat(result.totalPuntos()).isZero();
        assertThat(result.source()).isEqualTo(FuentePuntosLicencia.ANT);
        assertThat(result.stale()).isFalse();
        assertThat(cache.saved.get()).isNotNull();
        assertThat(cache.saved.get().fetchedAt()).isEqualTo(FETCHED_AT);
        assertThat(cache.saved.get().totalPuntos()).isZero();
        assertThat(cache.savedCedula.get()).isEqualTo(CEDULA);
    }

    @Test
    void doesNotConvertMissingInformationToZeroOrUseCache() {
        RecordingCache cache = new RecordingCache();
        cache.latest.set(Optional.of(cacheResult(12, FuentePuntosLicencia.ANT, FETCHED_AT)));
        var service = service(cedula -> Optional.empty(), Optional.of(cache), Optional.empty());

        Throwable failure = catchThrowable(() -> service.consultar(CEDULA));

        assertThat(failure).isInstanceOf(PuntosLicenciaSinInformacionException.class);
        assertThat(cache.reads).isZero();
        assertThat(cache.saved.get()).isNull();
    }

    @Test
    void usesCachedResultAfterTechnicalFailureAndPreservesOriginalTimestamp() {
        RecordingCache cache = new RecordingCache();
        cache.latest.set(Optional.of(cacheResult(13, FuentePuntosLicencia.SIMULATED, FETCHED_AT)));
        var service = service(cedula -> {
            throw new PuntosLicenciaProviderException(PuntosLicenciaProviderException.Kind.TIMEOUT);
        }, Optional.empty(), Optional.of(cache));

        var result = service.consultar(CEDULA);

        assertThat(result.totalPuntos()).isEqualTo(13);
        assertThat(result.source()).isEqualTo(FuentePuntosLicencia.CACHE);
        assertThat(result.originSource()).isEqualTo(FuentePuntosLicencia.SIMULATED);
        assertThat(result.fetchedAt()).isEqualTo(FETCHED_AT);
        assertThat(result.stale()).isTrue();
        assertThat(result.warning()).contains("SIMULATED", "No es información oficial", "desactualizado");
        assertThat(cache.lastReadCedula.get()).isEqualTo(CEDULA);
    }

    @Test
    void recoversSimulatedCacheAfterUnavailableProviderAndPreservesConfirmedZero() {
        RecordingCache cache = new RecordingCache();
        cache.latest.set(Optional.of(cacheResult(0, FuentePuntosLicencia.SIMULATED, FETCHED_AT)));
        var service = service(cedula -> {
            throw new PuntosLicenciaProviderException(PuntosLicenciaProviderException.Kind.UNAVAILABLE);
        }, Optional.empty(), Optional.of(cache));

        var result = service.consultar(CEDULA);

        assertThat(result.totalPuntos()).isZero();
        assertThat(result.source()).isEqualTo(FuentePuntosLicencia.CACHE);
        assertThat(result.originSource()).isEqualTo(FuentePuntosLicencia.SIMULATED);
        assertThat(result.fetchedAt()).isEqualTo(FETCHED_AT);
        assertThat(result.stale()).isTrue();
        assertThat(result.warning()).contains("SIMULATED", "No es información oficial");
    }

    @Test
    void preservesProviderFailureWhenCacheIsEmptyOrUnavailable() {
        RecordingCache emptyCache = new RecordingCache();
        emptyCache.latest.set(Optional.empty());
        var unavailable = service(cedula -> {
            throw new PuntosLicenciaProviderException(PuntosLicenciaProviderException.Kind.UNAVAILABLE);
        }, Optional.of(emptyCache), Optional.empty());
        Throwable withoutEntry = catchThrowable(() -> unavailable.consultar(CEDULA));
        assertThat(withoutEntry)
                .isInstanceOf(PuntosLicenciaProviderException.class)
                .extracting(failure -> ((PuntosLicenciaProviderException) failure).kind())
                .isEqualTo(PuntosLicenciaProviderException.Kind.UNAVAILABLE);

        RecordingCache brokenCache = new RecordingCache();
        brokenCache.readFailure = new PuntosLicenciaCacheException(PuntosLicenciaCacheException.Kind.UNAVAILABLE);
        var timeout = service(cedula -> {
            throw new PuntosLicenciaProviderException(PuntosLicenciaProviderException.Kind.TIMEOUT);
        }, Optional.of(brokenCache), Optional.empty());
        Throwable withCacheFailure = catchThrowable(() -> timeout.consultar(CEDULA));
        assertThat(withCacheFailure)
                .isInstanceOf(PuntosLicenciaProviderException.class)
                .extracting(failure -> ((PuntosLicenciaProviderException) failure).kind())
                .isEqualTo(PuntosLicenciaProviderException.Kind.TIMEOUT);
    }

    @Test
    void cacheWriteFailureDoesNotDiscardUsableProviderResult() {
        RecordingCache cache = new RecordingCache();
        cache.writeFailure = new PuntosLicenciaCacheException(PuntosLicenciaCacheException.Kind.UNAVAILABLE);
        var service = service(cedula -> Optional.of(
                result(16, FuentePuntosLicencia.ANT, FETCHED_AT)), Optional.of(cache));

        var result = service.consultar(CEDULA);

        assertThat(result.totalPuntos()).isEqualTo(16);
        assertThat(result.source()).isEqualTo(FuentePuntosLicencia.ANT);
        assertThat(result.stale()).isFalse();
    }

    @Test
    void doesNotAcceptCacheSourceAsAProviderSuccess() {
        RecordingCache cache = new RecordingCache();
        var service = service(cedula -> Optional.of(
                result(16, FuentePuntosLicencia.CACHE, FETCHED_AT)), Optional.of(cache));

        Throwable failure = catchThrowable(() -> service.consultar(CEDULA));

        assertThat(failure)
                .isInstanceOf(PuntosLicenciaProviderException.class)
                .extracting(value -> ((PuntosLicenciaProviderException) value).kind())
                .isEqualTo(PuntosLicenciaProviderException.Kind.INVALID_RESPONSE);
    }

    private static ConsultarPuntosLicenciaService service(
            PuntosLicenciaConsultaPort provider,
            Optional<PuntosLicenciaCachePort> cache) {
        return new ConsultarPuntosLicenciaService(provider, cache);
    }

    private static ConsultarPuntosLicenciaService service(
            PuntosLicenciaConsultaPort provider,
            Optional<PuntosLicenciaCachePort> antCache,
            Optional<PuntosLicenciaSimulationCachePort> simulationCache) {
        return new ConsultarPuntosLicenciaService(provider, antCache, simulationCache);
    }

    private static ResultadoPuntosLicencia result(
            int total,
            FuentePuntosLicencia source,
            Instant fetchedAt) {
        return source == FuentePuntosLicencia.CACHE
                ? cacheResult(total, FuentePuntosLicencia.ANT, fetchedAt)
                : new ResultadoPuntosLicencia(total, fetchedAt, source);
    }

    private static ResultadoPuntosLicencia cacheResult(
            int total,
            FuentePuntosLicencia origin,
            Instant fetchedAt) {
        return new ResultadoPuntosLicencia(total, fetchedAt, FuentePuntosLicencia.CACHE, origin);
    }

    private static final class RecordingCache implements PuntosLicenciaCachePort, PuntosLicenciaSimulationCachePort {
        private final AtomicReference<ResultadoPuntosLicencia> saved = new AtomicReference<>();
        private final AtomicReference<ResultadoPuntosLicencia> antSaved = new AtomicReference<>();
        private final AtomicReference<ResultadoPuntosLicencia> simulationSaved = new AtomicReference<>();
        private final AtomicReference<Cedula> savedCedula = new AtomicReference<>();
        private final AtomicReference<Cedula> lastReadCedula = new AtomicReference<>();
        private final AtomicReference<Optional<ResultadoPuntosLicencia>> latest =
                new AtomicReference<>(Optional.empty());
        private int reads;
        private PuntosLicenciaCacheException writeFailure;
        private PuntosLicenciaCacheException readFailure;

        @Override
        public void guardarResultado(Cedula cedula, ResultadoPuntosLicencia resultado) {
            if (writeFailure != null) {
                throw writeFailure;
            }
            savedCedula.set(cedula);
            saved.set(resultado);
            if (resultado.fuente() == FuentePuntosLicencia.ANT) {
                antSaved.set(resultado);
            } else if (resultado.fuente() == FuentePuntosLicencia.SIMULATED) {
                simulationSaved.set(resultado);
            }
        }

        @Override
        public Optional<ResultadoPuntosLicencia> obtenerUltimoResultado(Cedula cedula) {
            reads++;
            lastReadCedula.set(cedula);
            if (readFailure != null) {
                throw readFailure;
            }
            return latest.get();
        }
    }
}
