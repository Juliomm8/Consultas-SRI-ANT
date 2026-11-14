package ec.edu.udla.consultas.vehiculos.application;

import ec.edu.udla.consultas.vehiculos.application.port.in.ConsultarVehiculoQuery;
import ec.edu.udla.consultas.vehiculos.application.service.ConsultarVehiculoService;
import ec.edu.udla.consultas.vehiculos.domain.ValorCampoVehiculo;
import ec.edu.udla.consultas.vehiculos.domain.Vehiculo;
import ec.edu.udla.consultas.vehiculos.application.port.out.VehiculoConsultaPort;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ConsultarVehiculoServiceTests {

    @Test
    void delegatesTheIdentifierToTheOutputPortAndReturnsAnImmutableResult() {
        String syntheticIdentifier = "SYNTHETIC123";
        Vehiculo vehicle = new Vehiculo(Map.of(
                "codigoVehiculo",
                new ValorCampoVehiculo(ValorCampoVehiculo.TipoDato.NUMERO, "123")));
        CapturingVehiculoConsultaPort port = new CapturingVehiculoConsultaPort(List.of(vehicle));
        ConsultarVehiculoService service = new ConsultarVehiculoService(port);

        List<Vehiculo> result = service.consultar(new ConsultarVehiculoQuery(syntheticIdentifier));

        assertEquals(syntheticIdentifier, port.requestedIdentifier);
        assertEquals(List.of(vehicle), result);
        assertThrows(UnsupportedOperationException.class, () -> result.add(vehicle));
    }

    private static final class CapturingVehiculoConsultaPort implements VehiculoConsultaPort {

        private final List<Vehiculo> result;
        private String requestedIdentifier;

        private CapturingVehiculoConsultaPort(List<Vehiculo> result) {
            this.result = result;
        }

        @Override
        public List<Vehiculo> consultarPorIdentificador(String identificador) {
            requestedIdentifier = identificador;
            return result;
        }
    }
}
