package ec.edu.udla.consultas.puntos.adapter.out.redis;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import ec.edu.udla.consultas.puntos.application.exception.PuntosLicenciaCacheException;
import ec.edu.udla.consultas.puntos.domain.Cedula;
import ec.edu.udla.consultas.puntos.domain.FuentePuntosLicencia;
import ec.edu.udla.consultas.puntos.domain.ResultadoPuntosLicencia;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.time.Instant;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RedisPuntosLicenciaCacheAdapterTests {

    private static final Cedula CEDULA = new Cedula("0000000000");
    private static final Integer TOTAL_PUNTOS = 17;
    private static final Instant FETCHED_AT = Instant.parse("2026-11-15T12:30:00Z");
    private static final Duration TTL = Duration.ofMinutes(17);
    private static final String HMAC_SECRET = "synthetic-test-hmac-secret-value-00000000000000000000000000000000";

    private final StringRedisTemplate redis = mock(StringRedisTemplate.class);
    @SuppressWarnings("unchecked")
    private final ValueOperations<String, String> values = mock(ValueOperations.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RedisPuntosLicenciaCacheAdapter adapter = new RedisPuntosLicenciaCacheAdapter(
            redis, objectMapper, new HmacCacheKeyFactory(HMAC_SECRET), TTL);
    private final RedisSimulatedPuntosLicenciaCacheAdapter simulationAdapter =
            new RedisSimulatedPuntosLicenciaCacheAdapter(
                    redis,
                    objectMapper,
                    new HmacCacheKeyFactory(HMAC_SECRET, HmacCacheKeyFactory.SIMULATION_KEY_PREFIX),
                    TTL);
    private final Logger logger = (Logger) LoggerFactory.getLogger(RedisPuntosLicenciaCacheAdapter.class);
    private ListAppender<ILoggingEvent> logAppender;
    private Level previousLevel;

    @BeforeEach
    void setUp() {
        when(redis.opsForValue()).thenReturn(values);
        previousLevel = logger.getLevel();
        logger.setLevel(Level.WARN);
        logAppender = new ListAppender<>();
        logAppender.start();
        logger.addAppender(logAppender);
    }

    @AfterEach
    void tearDown() {
        logger.detachAppender(logAppender);
        logAppender.stop();
        logger.setLevel(previousLevel);
        MDC.remove("requestId");
    }

    @Test
    void savesJsonWithConfiguredTtlAndAnOpaqueHmacKey() throws Exception {
        adapter.guardarResultado(
                CEDULA,
                new ResultadoPuntosLicencia(TOTAL_PUNTOS, FETCHED_AT, FuentePuntosLicencia.ANT));

        org.mockito.ArgumentCaptor<String> keyCaptor = org.mockito.ArgumentCaptor.forClass(String.class);
        org.mockito.ArgumentCaptor<String> valueCaptor = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(values).set(keyCaptor.capture(), valueCaptor.capture(), eq(TTL));

        String key = keyCaptor.getValue();
        assertThat(key).matches("consultas-sri-ant:ant-points:v2:[a-f0-9]{64}")
                .doesNotContain(CEDULA.value());
        assertThat(valueCaptor.getValue()).doesNotContain(CEDULA.value());
        JsonNode stored = objectMapper.readTree(valueCaptor.getValue());
        assertThat(stored.path("totalPuntos").asInt()).isEqualTo(TOTAL_PUNTOS);
        assertThat(stored.path("fetchedAt").asText()).isEqualTo(FETCHED_AT.toString());
        assertThat(stored.path("source").asText()).isEqualTo(FuentePuntosLicencia.ANT.name());
        assertThat(stored.size()).isEqualTo(3);
    }

    @Test
    void readsCacheEntryWithOriginalTimestampAndCacheSource() throws Exception {
        String key = new HmacCacheKeyFactory(HMAC_SECRET).keyFor(CEDULA);
        when(values.get(key)).thenReturn(objectMapper.writeValueAsString(
                new RedisPuntosLicenciaCacheAdapter.RedisDocument(
                        TOTAL_PUNTOS, FETCHED_AT.toString(), FuentePuntosLicencia.ANT.name())));

        var result = adapter.obtenerUltimoResultado(CEDULA);

        assertThat(result).isPresent();
        assertThat(result.orElseThrow().totalPuntos()).isEqualTo(TOTAL_PUNTOS);
        assertThat(result.orElseThrow().fetchedAt()).isEqualTo(FETCHED_AT);
        assertThat(result.orElseThrow().fuente()).isEqualTo(FuentePuntosLicencia.CACHE);
        assertThat(result.orElseThrow().originSource()).isEqualTo(FuentePuntosLicencia.ANT);
    }

    @Test
    void returnsEmptyWhenNoEntryExists() {
        when(values.get(anyString())).thenReturn(null);

        assertThat(adapter.obtenerUltimoResultado(CEDULA)).isEmpty();
    }

    @Test
    void rejectsSimulatedResultWithoutWritingToRedis() {
        Throwable failure = catchThrowable(() -> adapter.guardarResultado(
                CEDULA,
                new ResultadoPuntosLicencia(TOTAL_PUNTOS, FETCHED_AT, FuentePuntosLicencia.SIMULATED)));

        assertThat(failure)
                .isInstanceOf(PuntosLicenciaCacheException.class)
                .extracting(exception -> ((PuntosLicenciaCacheException) exception).kind())
                .isEqualTo(PuntosLicenciaCacheException.Kind.INVALID_RESULT);
        verify(values, never()).set(anyString(), anyString(), eq(TTL));
    }

    @Test
    void storesAndRecoversSimulatedResultsOnlyInTheIndependentNamespace() throws Exception {
        simulationAdapter.guardarResultado(
                CEDULA,
                new ResultadoPuntosLicencia(0, FETCHED_AT, FuentePuntosLicencia.SIMULATED));

        org.mockito.ArgumentCaptor<String> keyCaptor = org.mockito.ArgumentCaptor.forClass(String.class);
        org.mockito.ArgumentCaptor<String> valueCaptor = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(values).set(keyCaptor.capture(), valueCaptor.capture(), eq(TTL));
        String simulationKey = keyCaptor.getValue();
        String simulationDocument = valueCaptor.getValue();
        assertThat(simulationKey).matches("consultas-sri-ant:ant-points:simulation:v1:[a-f0-9]{64}")
                .doesNotContain(CEDULA.value());
        assertThat(simulationDocument).doesNotContain(CEDULA.value());
        JsonNode stored = objectMapper.readTree(simulationDocument);
        assertThat(stored.size()).isEqualTo(3);
        assertThat(stored.path("totalPuntos").asInt()).isZero();
        assertThat(stored.path("fetchedAt").asText()).isEqualTo(FETCHED_AT.toString());
        assertThat(stored.path("source").asText()).isEqualTo(FuentePuntosLicencia.SIMULATED.name());

        when(values.get(simulationKey)).thenReturn(simulationDocument);
        var recovered = simulationAdapter.obtenerUltimoResultado(CEDULA).orElseThrow();
        assertThat(recovered.totalPuntos()).isZero();
        assertThat(recovered.fetchedAt()).isEqualTo(FETCHED_AT);
        assertThat(recovered.fuente()).isEqualTo(FuentePuntosLicencia.CACHE);
        assertThat(recovered.originSource()).isEqualTo(FuentePuntosLicencia.SIMULATED);

        assertThat(new HmacCacheKeyFactory(HMAC_SECRET).keyFor(CEDULA)).isNotEqualTo(simulationKey);
        assertThat(adapter.obtenerUltimoResultado(CEDULA)).isEmpty();
    }

    @Test
    void simulatedCacheRejectsOfficialAntResults() {
        Throwable failure = catchThrowable(() -> simulationAdapter.guardarResultado(
                CEDULA,
                new ResultadoPuntosLicencia(TOTAL_PUNTOS, FETCHED_AT, FuentePuntosLicencia.ANT)));

        assertThat(failure)
                .isInstanceOf(PuntosLicenciaCacheException.class)
                .extracting(exception -> ((PuntosLicenciaCacheException) exception).kind())
                .isEqualTo(PuntosLicenciaCacheException.Kind.INVALID_RESULT);
        verify(values, never()).set(anyString(), anyString(), eq(TTL));
    }

    @Test
    void rejectsCachedDocumentsWithUnsupportedSource() throws Exception {
        String key = new HmacCacheKeyFactory(HMAC_SECRET).keyFor(CEDULA);
        when(values.get(key)).thenReturn(objectMapper.writeValueAsString(
                new RedisPuntosLicenciaCacheAdapter.RedisDocument(
                        TOTAL_PUNTOS, FETCHED_AT.toString(), FuentePuntosLicencia.CACHE.name())));

        Throwable failure = catchThrowable(() -> adapter.obtenerUltimoResultado(CEDULA));

        assertThat(failure)
                .isInstanceOf(PuntosLicenciaCacheException.class)
                .extracting(exception -> ((PuntosLicenciaCacheException) exception).kind())
                .isEqualTo(PuntosLicenciaCacheException.Kind.INVALID_ENTRY);
    }

    @Test
    void rejectsMalformedCachedJsonWithoutLoggingItsContents() {
        String privateMarker = "synthetic-private-cache-body-marker";
        String malformedDocument = "{\"personal\":\"" + privateMarker;
        when(values.get(anyString())).thenReturn(malformedDocument);
        MDC.put("requestId", "malformed-cache-request-id");

        Throwable failure = catchThrowable(() -> adapter.obtenerUltimoResultado(CEDULA));

        assertThat(failure)
                .isInstanceOf(PuntosLicenciaCacheException.class)
                .hasMessage("La entrada de caché de puntos de licencia no es válida.");
        String loggedMessages = logAppender.list.stream()
                .map(ILoggingEvent::getFormattedMessage)
                .collect(Collectors.joining("\n"));
        assertThat(loggedMessages)
                .contains("malformed-cache-request-id", "outcome=invalid_entry", "JsonEOFException")
                .doesNotContain(privateMarker, malformedDocument, CEDULA.value());
        assertThat(logAppender.list).allSatisfy(event -> assertThat(event.getThrowableProxy()).isNull());
    }

    @Test
    void rejectsNegativeTotalPointsInCachedEntry() throws Exception {
        String key = new HmacCacheKeyFactory(HMAC_SECRET).keyFor(CEDULA);
        when(values.get(key)).thenReturn(objectMapper.writeValueAsString(
                new RedisPuntosLicenciaCacheAdapter.RedisDocument(
                        -1, FETCHED_AT.toString(), FuentePuntosLicencia.ANT.name())));

        Throwable failure = catchThrowable(() -> adapter.obtenerUltimoResultado(CEDULA));

        assertThat(failure)
                .isInstanceOf(PuntosLicenciaCacheException.class)
                .extracting(exception -> ((PuntosLicenciaCacheException) exception).kind())
                .isEqualTo(PuntosLicenciaCacheException.Kind.INVALID_ENTRY);
        assertThat(logAppender.list).anySatisfy(event ->
                assertThat(event.getFormattedMessage()).contains("outcome=invalid_entry"));
    }

    @Test
    void rejectsStringTypedTotalPointsInCachedEntry() throws Exception {
        String key = new HmacCacheKeyFactory(HMAC_SECRET).keyFor(CEDULA);
        when(values.get(key)).thenReturn("{\"totalPuntos\":\"17\",\"fetchedAt\":\""
                + FETCHED_AT + "\",\"source\":\"ANT\"}");

        Throwable failure = catchThrowable(() -> adapter.obtenerUltimoResultado(CEDULA));

        assertThat(failure)
                .isInstanceOf(PuntosLicenciaCacheException.class)
                .extracting(exception -> ((PuntosLicenciaCacheException) exception).kind())
                .isEqualTo(PuntosLicenciaCacheException.Kind.INVALID_ENTRY);
    }

    @Test
    void rejectsCachedDocumentWithUnexpectedSensitiveFieldWithoutLoggingIt() throws Exception {
        String key = new HmacCacheKeyFactory(HMAC_SECRET).keyFor(CEDULA);
        String identifierMarker = "1234567890";
        String payload = "{\"totalPuntos\":17,\"fetchedAt\":\"" + FETCHED_AT
                + "\",\"source\":\"ANT\",\"cedula\":\"" + identifierMarker + "\"}";
        when(values.get(key)).thenReturn(payload);

        Throwable failure = catchThrowable(() -> adapter.obtenerUltimoResultado(CEDULA));

        assertThat(failure)
                .isInstanceOf(PuntosLicenciaCacheException.class)
                .extracting(exception -> ((PuntosLicenciaCacheException) exception).kind())
                .isEqualTo(PuntosLicenciaCacheException.Kind.INVALID_ENTRY);
        assertThat(logAppender.list).anySatisfy(event ->
                assertThat(event.getFormattedMessage()).contains("outcome=invalid_entry")
                        .doesNotContain(CEDULA.value(), identifierMarker, payload));
    }

    @Test
    void logsOnlySafeMetadataWhenRedisThrowsAnExceptionWithSensitiveMarkers() {
        String cedulaMarker = CEDULA.value();
        String emailMarker = "person@example.invalid";
        String plateMarker = "TEST-PLATE-42";
        String tokenMarker = "synthetic-token-91";
        String urlMarker = "https://cache.invalid/?subject=synthetic";
        String message = cedulaMarker + " " + emailMarker + " " + plateMarker + " " + tokenMarker + " "
                + urlMarker + " " + HMAC_SECRET;
        when(values.get(anyString())).thenThrow(new DataAccessResourceFailureException(message));
        MDC.put("requestId", "safe-cache-request-id");

        Throwable failure = catchThrowable(() -> adapter.obtenerUltimoResultado(CEDULA));

        assertThat(failure)
                .isInstanceOf(PuntosLicenciaCacheException.class)
                .hasMessage("La caché de puntos de licencia no está disponible.")
                .hasNoCause();
        String loggedMessages = logAppender.list.stream()
                .map(ILoggingEvent::getFormattedMessage)
                .collect(Collectors.joining("\n"));
        assertThat(loggedMessages)
                .contains("safe-cache-request-id", "operation=read", "outcome=unavailable",
                        "DataAccessResourceFailureException")
                .doesNotContain(cedulaMarker, emailMarker, plateMarker, tokenMarker, urlMarker, HMAC_SECRET,
                        "consultas-sri-ant:ant-points:v2:");
        assertThat(logAppender.list).allSatisfy(event -> assertThat(event.getThrowableProxy()).isNull());
    }

    @Test
    void doesNotExposeNormalizedContentInToString() {
        var result = new ResultadoPuntosLicencia(
                TOTAL_PUNTOS, FETCHED_AT, FuentePuntosLicencia.ANT);

        assertThat(result.toString()).contains("<redacted>").doesNotContain(TOTAL_PUNTOS.toString());
    }

    @Test
    void preservesAConfirmedZeroAsAValue() throws Exception {
        adapter.guardarResultado(
                CEDULA,
                new ResultadoPuntosLicencia(0, FETCHED_AT, FuentePuntosLicencia.ANT));

        org.mockito.ArgumentCaptor<String> keyCaptor = org.mockito.ArgumentCaptor.forClass(String.class);
        org.mockito.ArgumentCaptor<String> valueCaptor = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(values).set(keyCaptor.capture(), valueCaptor.capture(), eq(TTL));
        when(values.get(keyCaptor.getValue())).thenReturn(valueCaptor.getValue());
        JsonNode stored = objectMapper.readTree(valueCaptor.getValue());
        var recovered = adapter.obtenerUltimoResultado(CEDULA);

        assertThat(stored.path("totalPuntos").asInt()).isZero();
        assertThat(stored.path("totalPuntos").isNumber()).isTrue();
        assertThat(recovered).hasValueSatisfying(result -> {
            assertThat(result.totalPuntos()).isZero();
            assertThat(result.fuente()).isEqualTo(FuentePuntosLicencia.CACHE);
            assertThat(result.originSource()).isEqualTo(FuentePuntosLicencia.ANT);
        });
    }
}
