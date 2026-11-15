package ec.edu.udla.consultas.puntos.adapter.out.ant.simulation;

import ec.edu.udla.consultas.puntos.application.port.out.PuntosLicenciaConsultaPort;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class AntSimulationConfigurationTests {

    @Test
    void createsNoSimulationProviderWhenExplicitEnablementIsMissing() {
        new ApplicationContextRunner()
                .withUserConfiguration(AntSimulationConfiguration.class)
                .withPropertyValues("spring.profiles.active=local-simulation")
                .withBean(CircuitBreakerRegistry.class, CircuitBreakerRegistry::ofDefaults)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBeansOfType(PuntosLicenciaConsultaPort.class)).isEmpty();
                });
    }

    @Test
    void refusesToEnableSimulationWhenTheServerIsNotBoundToLoopback() {
        new ApplicationContextRunner()
                .withUserConfiguration(AntSimulationConfiguration.class)
                .withPropertyValues(
                        "spring.profiles.active=local-simulation",
                        "app.ant.simulation.enabled=true",
                        "app.ant.simulation.scenario=SUCCESS",
                        "server.address=0.0.0.0")
                .withBean(CircuitBreakerRegistry.class, CircuitBreakerRegistry::ofDefaults)
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasStackTraceContaining("solo puede iniciar enlazado a 127.0.0.1");
                });
    }
}
