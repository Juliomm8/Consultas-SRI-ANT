package ec.edu.udla.consultas.shared.api;

import ec.edu.udla.consultas.contribuyentes.application.exception.ContribuyenteNoEncontradoException;
import ec.edu.udla.consultas.contribuyentes.application.exception.ContribuyenteNoEsPersonaNaturalException;
import ec.edu.udla.consultas.contribuyentes.application.exception.TipoPersonaNoDeterminadaException;
import ec.edu.udla.consultas.contribuyentes.domain.exception.RucFormatoInvalidoException;
import ec.edu.udla.consultas.puntos.application.exception.PuntosLicenciaProviderException;
import ec.edu.udla.consultas.puntos.application.exception.PuntosLicenciaSinInformacionException;
import ec.edu.udla.consultas.puntos.domain.exception.CedulaFormatoInvalidoException;
import ec.edu.udla.consultas.shared.integration.sri.SriIntegrationException;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final String INTERNAL_ERROR_CODE = "INTERNAL_ERROR";
    private static final String APPLICATION_PACKAGE_PREFIX = "ec.edu.udla.consultas.";
    private static final int MAX_CAUSE_DEPTH = 8;
    private static final Logger LOGGER = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidationError(
            MethodArgumentNotValidException exception,
            HttpServletRequest request) {
        return errorResponse(
                HttpStatus.BAD_REQUEST,
                "VALIDATION_ERROR",
                "Los datos enviados no son válidos.",
                request);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> handleMalformedJson(
            HttpMessageNotReadableException exception,
            HttpServletRequest request) {
        return errorResponse(
                HttpStatus.BAD_REQUEST,
                "MALFORMED_JSON",
                "El cuerpo debe contener JSON válido.",
                request);
    }

    @ExceptionHandler(RucFormatoInvalidoException.class)
    public ResponseEntity<ApiError> handleInvalidRuc(
            RucFormatoInvalidoException exception,
            HttpServletRequest request) {
        return errorResponse(
                HttpStatus.BAD_REQUEST,
                "VALIDATION_ERROR",
                "Los datos enviados no son válidos.",
                request);
    }

    @ExceptionHandler(CedulaFormatoInvalidoException.class)
    public ResponseEntity<ApiError> handleInvalidCedula(
            CedulaFormatoInvalidoException exception,
            HttpServletRequest request) {
        return errorResponse(
                HttpStatus.BAD_REQUEST,
                "VALIDATION_ERROR",
                "Los datos enviados no son válidos.",
                request);
    }

    @ExceptionHandler(PuntosLicenciaSinInformacionException.class)
    public ResponseEntity<ApiError> handlePointsNotFound(
            PuntosLicenciaSinInformacionException exception,
            HttpServletRequest request) {
        return errorResponse(
                HttpStatus.NOT_FOUND,
                "NOT_FOUND",
                "No se encontró información de puntos de licencia.",
                request);
    }

    @ExceptionHandler(PuntosLicenciaProviderException.class)
    public ResponseEntity<ApiError> handlePointsProviderFailure(
            PuntosLicenciaProviderException exception,
            HttpServletRequest request) {
        String requestId = requestId(request);
        String code = switch (exception.kind()) {
            case TIMEOUT -> "PROVIDER_TIMEOUT";
            case UNAVAILABLE -> "PROVIDER_UNAVAILABLE";
            case INVALID_RESPONSE -> "PROVIDER_BAD_RESPONSE";
        };
        HttpStatus status = switch (exception.kind()) {
            case TIMEOUT -> HttpStatus.GATEWAY_TIMEOUT;
            case UNAVAILABLE -> HttpStatus.SERVICE_UNAVAILABLE;
            case INVALID_RESPONSE -> HttpStatus.BAD_GATEWAY;
        };
        LOGGER.warn("ANT points provider failed; requestId={}, errorCode={}, kind={}",
                requestId, code, exception.kind());
        String message = switch (exception.kind()) {
            case TIMEOUT -> "El servicio de puntos de licencia excedió el tiempo de espera.";
            case UNAVAILABLE -> "El servicio de puntos de licencia no está disponible.";
            case INVALID_RESPONSE -> "El servicio de puntos de licencia devolvió una respuesta no utilizable.";
        };
        return errorResponse(status, code, message, request, requestId);
    }

    @ExceptionHandler(ContribuyenteNoEncontradoException.class)
    public ResponseEntity<ApiError> handleTaxpayerNotFound(
            ContribuyenteNoEncontradoException exception,
            HttpServletRequest request) {
        return errorResponse(
                HttpStatus.NOT_FOUND,
                "NOT_FOUND",
                "No se encontró información para la identificación consultada.",
                request);
    }

    @ExceptionHandler(ContribuyenteNoEsPersonaNaturalException.class)
    public ResponseEntity<ApiError> handleNonNaturalTaxpayer(
            ContribuyenteNoEsPersonaNaturalException exception,
            HttpServletRequest request) {
        return errorResponse(
                HttpStatus.UNPROCESSABLE_ENTITY,
                "IDENTIFICATION_NOT_NATURAL_PERSON",
                "La identificación no corresponde a una persona natural.",
                request);
    }

    @ExceptionHandler(TipoPersonaNoDeterminadaException.class)
    public ResponseEntity<ApiError> handleUndeterminedTaxpayerType(
            TipoPersonaNoDeterminadaException exception,
            HttpServletRequest request) {
        return errorResponse(
                HttpStatus.UNPROCESSABLE_ENTITY,
                "TAXPAYER_TYPE_UNDETERMINED",
                "No se pudo determinar el tipo de persona.",
                request);
    }

    @ExceptionHandler(SriIntegrationException.class)
    public ResponseEntity<ApiError> handleSriFailure(
            SriIntegrationException exception,
            HttpServletRequest request) {
        String requestId = requestId(request);
        LOGGER.warn(
                "SRI integration failed; requestId={}, errorCode={}, kind={}, upstreamStatus={}",
                requestId,
                sriErrorCode(exception.kind()),
                exception.kind(),
                exception.upstreamStatus());
        return switch (exception.kind()) {
            case TIMEOUT -> errorResponse(
                    HttpStatus.GATEWAY_TIMEOUT,
                    "PROVIDER_TIMEOUT",
                    "El servicio SRI excedió el tiempo de espera.",
                    request,
                    requestId);
            case UNAVAILABLE -> errorResponse(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "PROVIDER_UNAVAILABLE",
                    "El servicio SRI no está disponible.",
                    request,
                    requestId);
            case HTTP_ERROR, INVALID_RESPONSE -> errorResponse(
                    HttpStatus.BAD_GATEWAY,
                    "PROVIDER_BAD_RESPONSE",
                    "El servicio SRI devolvió una respuesta no utilizable.",
                    request,
                    requestId);
        };
    }

    private static String sriErrorCode(SriIntegrationException.Kind kind) {
        return switch (kind) {
            case TIMEOUT -> "PROVIDER_TIMEOUT";
            case UNAVAILABLE -> "PROVIDER_UNAVAILABLE";
            case HTTP_ERROR, INVALID_RESPONSE -> "PROVIDER_BAD_RESPONSE";
        };
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleUnexpectedError(
            Exception exception,
            HttpServletRequest request) {
        String requestId = requestId(request);
        SafeExceptionDetails details = safeExceptionDetails(exception);
        LOGGER.error(
                "Unexpected API error; requestId={}, operation={}, errorCode={}, httpStatus={}, "
                        + "exceptionTypes={}, locationClass={}, locationMethod={}, locationLine={}",
                requestId,
                operationFor(request),
                INTERNAL_ERROR_CODE,
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                details.exceptionTypes(),
                details.locationClass(),
                details.locationMethod(),
                details.locationLine());

        return errorResponse(
                HttpStatus.INTERNAL_SERVER_ERROR,
                INTERNAL_ERROR_CODE,
                "Ocurrió un error interno. Inténtalo más tarde.",
                request,
                requestId);
    }

    private static String operationFor(HttpServletRequest request) {
        String path = request.getRequestURI();
        String contextPath = request.getContextPath();
        if (contextPath != null && !contextPath.isEmpty() && path.startsWith(contextPath)) {
            path = path.substring(contextPath.length());
        }

        if ("POST".equals(request.getMethod()) && "/api/v1/contribuyentes/consultas".equals(path)) {
            return "taxpayer-consultation";
        }
        if ("POST".equals(request.getMethod()) && "/api/v1/vehiculos/consultas".equals(path)) {
            return "vehicle-consultation";
        }
        if ("POST".equals(request.getMethod()) && "/api/v1/licencias/puntos/consultas".equals(path)) {
            return "license-points-consultation";
        }
        return "unmapped";
    }

    private static SafeExceptionDetails safeExceptionDetails(Throwable exception) {
        List<String> exceptionTypes = new ArrayList<>();
        Set<Throwable> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        String locationClass = "unavailable";
        String locationMethod = "unavailable";
        int locationLine = -1;

        Throwable current = exception;
        while (current != null && visited.add(current) && exceptionTypes.size() < MAX_CAUSE_DEPTH) {
            String exceptionType = current.getClass().getSimpleName();
            exceptionTypes.add(exceptionType.isBlank() ? "UnknownException" : exceptionType);

            if ("unavailable".equals(locationClass)) {
                for (StackTraceElement frame : current.getStackTrace()) {
                    if (frame.getClassName().startsWith(APPLICATION_PACKAGE_PREFIX)) {
                        locationClass = frame.getClassName();
                        locationMethod = frame.getMethodName();
                        locationLine = frame.getLineNumber();
                        break;
                    }
                }
            }
            current = current.getCause();
        }

        return new SafeExceptionDetails(
                List.copyOf(exceptionTypes), locationClass, locationMethod, locationLine);
    }

    private record SafeExceptionDetails(
            List<String> exceptionTypes,
            String locationClass,
            String locationMethod,
            int locationLine) {
    }

    private ResponseEntity<ApiError> errorResponse(
            HttpStatus status,
            String code,
            String message,
            HttpServletRequest request) {
        return errorResponse(status, code, message, request, requestId(request));
    }

    private ResponseEntity<ApiError> errorResponse(
            HttpStatus status,
            String code,
            String message,
            HttpServletRequest request,
            String requestId) {
        ApiError body = new ApiError(new ApiError.ErrorDetail(code, message, requestId));
        return ResponseEntity.status(status)
                .body(body);
    }

    private String requestId(HttpServletRequest request) {
        Object attribute = request.getAttribute(RequestIdFilter.REQUEST_ID_ATTRIBUTE);
        if (attribute instanceof String requestId && !requestId.isBlank()) {
            return requestId;
        }
        return UUID.randomUUID().toString();
    }
}
