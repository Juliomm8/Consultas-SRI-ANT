package ec.edu.udla.consultas.vehiculos.application.port.in;

import ec.edu.udla.consultas.vehiculos.domain.Vehiculo;

import java.util.List;

public interface ConsultarVehiculoUseCase {

    List<Vehiculo> consultar(ConsultarVehiculoQuery query);
}
