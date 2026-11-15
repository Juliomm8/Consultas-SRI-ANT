package ec.edu.udla.consultas.shared.integration.sri;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import java.net.URI;
import java.util.List;

/** Prevents an E2E-profile backend from sending SRI traffic outside its local fixture server. */
@Configuration(proxyBeanMethods = false)
@Profile("e2e")
public class E2eSriEndpointGuard {

    public E2eSriEndpointGuard(
            @Value("${integrations.sri.taxpayer-existence-url}") URI taxpayerExistence,
            @Value("${integrations.sri.taxpayer-details-url}") URI taxpayerDetails,
            @Value("${integrations.sri.vehicle-url}") URI vehicle) {
        List<URI> endpoints = List.of(taxpayerExistence, taxpayerDetails, vehicle);
        URI first = endpoints.get(0);
        boolean allLocalToSameStub = endpoints.stream().allMatch(endpoint ->
                "http".equalsIgnoreCase(endpoint.getScheme())
                        && "127.0.0.1".equals(endpoint.getHost())
                        && endpoint.getPort() > 0
                        && endpoint.getPort() == first.getPort()
                        && endpoint.getUserInfo() == null
                        && endpoint.getQuery() == null
                        && endpoint.getFragment() == null);
        if (!allLocalToSameStub) {
            throw new IllegalStateException(
                    "The e2e profile requires all SRI endpoints to target one local HTTP stub.");
        }
    }
}
