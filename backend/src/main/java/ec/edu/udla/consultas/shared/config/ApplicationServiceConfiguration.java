package ec.edu.udla.consultas.shared.config;

import ec.edu.udla.consultas.contribuyentes.application.port.in.ConsultarContribuyenteUseCase;
import ec.edu.udla.consultas.contribuyentes.application.port.out.ContribuyenteConsultaPort;
import ec.edu.udla.consultas.contribuyentes.application.service.ConsultarContribuyenteService;
import ec.edu.udla.consultas.vehiculos.application.port.in.ConsultarVehiculoUseCase;
import ec.edu.udla.consultas.vehiculos.application.port.out.VehiculoConsultaPort;
import ec.edu.udla.consultas.vehiculos.application.service.ConsultarVehiculoService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Spring composition root for the application use cases. */
@Configuration(proxyBeanMethods = false)
public class ApplicationServiceConfiguration {

    @Bean
    public ConsultarContribuyenteUseCase consultarContribuyenteUseCase(
            ContribuyenteConsultaPort contribuyenteConsultaPort) {
        return new ConsultarContribuyenteService(contribuyenteConsultaPort);
    }

    @Bean
    public ConsultarVehiculoUseCase consultarVehiculoUseCase(
            VehiculoConsultaPort vehiculoConsultaPort) {
        return new ConsultarVehiculoService(vehiculoConsultaPort);
    }
}
