package ec.edu.udla.consultas.puntos.adapter.out.ant.simulation;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Settings for the loopback-only ANT points simulator. */
@ConfigurationProperties(prefix = "app.ant.simulation")
public class AntSimulationProperties {

    private Scenario scenario = Scenario.SUCCESS;
    private int totalPoints = 20;

    public Scenario getScenario() {
        return scenario;
    }

    public void setScenario(Scenario scenario) {
        this.scenario = scenario;
    }

    public int getTotalPoints() {
        return totalPoints;
    }

    public void setTotalPoints(int totalPoints) {
        this.totalPoints = totalPoints;
    }

    public enum Scenario {
        SUCCESS,
        ZERO,
        NO_DATA,
        TIMEOUT,
        UNAVAILABLE,
        INVALID_RESPONSE,
        RECOVERY
    }
}
