package ec.edu.udla.consultas.vehiculos.application.service;

import ec.edu.udla.consultas.vehiculos.application.port.in.ConsultarVehiculoQuery;
import ec.edu.udla.consultas.vehiculos.application.port.in.ConsultarVehiculoUseCase;
import ec.edu.udla.consultas.vehiculos.application.port.out.VehiculoConsultaPort;
import ec.edu.udla.consultas.vehiculos.domain.Vehiculo;

import java.util.List;
import java.util.Objects;

public class ConsultarVehiculoService implements ConsultarVehiculoUseCase {

    private final VehiculoConsultaPort vehiculoConsultaPort;

    public ConsultarVehiculoService(VehiculoConsultaPort vehiculoConsultaPort) {
        this.vehiculoConsultaPort = Objects.requireNonNull(
                vehiculoConsultaPort,
                "El puerto de consulta de vehículos es obligatorio.");
    }

    @Override
    public List<Vehiculo> consultar(ConsultarVehiculoQuery query) {
        Objects.requireNonNull(query, "La consulta del vehículo es obligatoria.");
        return List.copyOf(vehiculoConsultaPort.consultarPorIdentificador(query.identificador()));
    }
}
