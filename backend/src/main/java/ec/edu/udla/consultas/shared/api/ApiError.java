package ec.edu.udla.consultas.shared.api;

public record ApiError(ErrorDetail error) {

    public record ErrorDetail(String code, String message, String requestId) {
    }
}
