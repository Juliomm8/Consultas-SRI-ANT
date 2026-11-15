package ec.edu.udla.consultas.puntos.adapter.out.ant.simulation;

import ec.edu.udla.consultas.puntos.adapter.out.ant.ResilientPuntosLicenciaConsultaAdapter;
import ec.edu.udla.consultas.puntos.application.port.in.ConsultarPuntosLicenciaUseCase;
import ec.edu.udla.consultas.puntos.application.port.out.PuntosLicenciaCachePort;
import ec.edu.udla.consultas.puntos.application.port.out.PuntosLicenciaConsultaPort;
import ec.edu.udla.consultas.puntos.application.service.ConsultarPuntosLicenciaService;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;

import java.util.Optional;

/** Composition root enabled only by the local simulation profile. */
@Configuration(proxyBeanMethods = false)
@Profile("local-simulation")
@ConditionalOnProperty(prefix = "app.ant.simulation", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(AntSimulationProperties.class)
public class AntSimulationConfiguration {

    @Bean
    public PuntosLicenciaConsultaPort puntosLicenciaConsultaPort(
            AntSimulationProperties properties,
            CircuitBreakerRegistry circuitBreakerRegistry,
            Environment environment) {
        String serverAddress = environment.getProperty("server.address", "");
        if (!"127.0.0.1".equals(serverAddress)) {
            throw new IllegalStateException(
                    "El perfil de simulación ANT solo puede iniciar enlazado a 127.0.0.1.");
        }
        var simulator = new SimulatedAntPuntosLicenciaAdapter(properties);
        return new ResilientPuntosLicenciaConsultaAdapter(
                simulator, circuitBreakerRegistry.circuitBreaker("antPoints"));
    }

    @Bean
    public ConsultarPuntosLicenciaUseCase consultarPuntosLicenciaUseCase(
            PuntosLicenciaConsultaPort provider,
            Optional<PuntosLicenciaCachePort> cache) {
        return new ConsultarPuntosLicenciaService(provider, cache);
    }
}
