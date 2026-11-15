package ec.edu.udla.consultas;

import ec.edu.udla.consultas.contribuyentes.adapter.in.rest.ContribuyenteRestController;
import ec.edu.udla.consultas.contribuyentes.application.port.in.ConsultarContribuyenteUseCase;
import ec.edu.udla.consultas.puntos.application.port.out.PuntosLicenciaCachePort;
import ec.edu.udla.consultas.puntos.application.port.in.ConsultarPuntosLicenciaUseCase;
import ec.edu.udla.consultas.puntos.adapter.in.rest.PuntosLicenciaRestController;
import ec.edu.udla.consultas.puntos.application.port.out.PuntosLicenciaConsultaPort;
import ec.edu.udla.consultas.vehiculos.adapter.in.rest.VehiculoRestController;
import ec.edu.udla.consultas.vehiculos.application.port.in.ConsultarVehiculoUseCase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class ConsultasApplicationTests {

    @Autowired
    private ApplicationContext applicationContext;

    @Test
    void contextLoads() {
        assertThat(applicationContext.getBeansOfType(ConsultarContribuyenteUseCase.class))
                .hasSize(1);
        assertThat(applicationContext.getBeansOfType(ConsultarVehiculoUseCase.class))
                .hasSize(1);
        assertThat(applicationContext.getBeansOfType(ContribuyenteRestController.class))
                .hasSize(1);
        assertThat(applicationContext.getBeansOfType(VehiculoRestController.class))
                .hasSize(1);
        assertThat(applicationContext.getBeansOfType(ConsultarPuntosLicenciaUseCase.class)).isEmpty();
        assertThat(applicationContext.getBeansOfType(PuntosLicenciaConsultaPort.class)).isEmpty();
        assertThat(applicationContext.getBeansOfType(PuntosLicenciaRestController.class)).isEmpty();
        assertThat(applicationContext.getBeansOfType(PuntosLicenciaCachePort.class))
                .isEmpty();
        assertThat(applicationContext.getBeansOfType(RedisConnectionFactory.class))
                .isEmpty();
        assertThat(applicationContext.getBeansOfType(StringRedisTemplate.class))
                .isEmpty();
    }
}
