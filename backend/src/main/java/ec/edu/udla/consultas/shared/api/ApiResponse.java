package ec.edu.udla.consultas.shared.api;

public record ApiResponse<T>(T data, ApiMeta meta) {
}
