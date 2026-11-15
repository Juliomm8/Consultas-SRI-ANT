package ec.edu.udla.consultas.shared.api;

import ec.edu.udla.consultas.contribuyentes.application.exception.ContribuyenteNoEncontradoException;
import ec.edu.udla.consultas.contribuyentes.application.exception.ContribuyenteNoEsPersonaNaturalException;
import ec.edu.udla.consultas.contribuyentes.application.exception.TipoPersonaNoDeterminadaException;
import ec.edu.udla.consultas.contribuyentes.domain.exception.RucFormatoInvalidoException;
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

import java.util.Arrays;
import java.util.UUID;
import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {

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
        return switch (exception.kind()) {
            case TIMEOUT -> errorResponse(
                    HttpStatus.GATEWAY_TIMEOUT,
                    "PROVIDER_TIMEOUT",
                    "El servicio SRI excedió el tiempo de espera.",
                    request);
            case UNAVAILABLE -> errorResponse(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "PROVIDER_UNAVAILABLE",
                    "El servicio SRI no está disponible.",
                    request);
            case HTTP_ERROR, INVALID_RESPONSE -> errorResponse(
                    HttpStatus.BAD_GATEWAY,
                    "PROVIDER_BAD_RESPONSE",
                    "El servicio SRI devolvió una respuesta no utilizable.",
                    request);
        };
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleUnexpectedError(
            Exception exception,
            HttpServletRequest request) {
        String requestId = requestId(request);
        String stackTrace = Arrays.stream(exception.getStackTrace())
                .map(StackTraceElement::toString)
                .collect(Collectors.joining(" | "));
        LOGGER.error(
                "Unexpected API error; requestId={}, exceptionType={}, stackTrace={}",
                requestId,
                exception.getClass().getName(),
                stackTrace);

        return errorResponse(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "INTERNAL_ERROR",
                "Ocurrió un error interno. Inténtalo más tarde.",
                request,
                requestId);
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
