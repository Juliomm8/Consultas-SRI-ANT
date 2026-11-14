package ec.edu.udla.consultas.vehiculos.application.port.in;

public record ConsultarVehiculoQuery(String identificador) {

    public ConsultarVehiculoQuery {
        if (identificador == null || identificador.isBlank()) {
            throw new IllegalArgumentException("El identificador vehicular es obligatorio.");
        }
    }

    @Override
    public String toString() {
        return "ConsultarVehiculoQuery[identificador=redactado]";
    }
}
