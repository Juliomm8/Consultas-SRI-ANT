package ec.edu.udla.consultas.vehiculos.application.port.out;

import ec.edu.udla.consultas.vehiculos.domain.Vehiculo;

import java.util.List;

public interface VehiculoConsultaPort {

    /** Returns the SRI data array; an empty list mirrors an observed empty {@code data} array. */
    List<Vehiculo> consultarPorIdentificador(String identificador);
}
