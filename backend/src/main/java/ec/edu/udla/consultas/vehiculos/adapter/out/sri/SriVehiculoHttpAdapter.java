package ec.edu.udla.consultas.vehiculos.adapter.out.sri;

import com.fasterxml.jackson.databind.JsonNode;
import ec.edu.udla.consultas.shared.integration.sri.SriIntegrationException;
import ec.edu.udla.consultas.shared.integration.sri.SriResponseSupport;
import ec.edu.udla.consultas.vehiculos.application.port.out.VehiculoConsultaPort;
import ec.edu.udla.consultas.vehiculos.domain.ValorCampoVehiculo;
import ec.edu.udla.consultas.vehiculos.domain.Vehiculo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Component
public class SriVehiculoHttpAdapter implements VehiculoConsultaPort {

    private final RestClient restClient;
    private final URI vehicleEndpoint;

    @Autowired
    public SriVehiculoHttpAdapter(
            @Qualifier("sriRestClient") RestClient restClient,
            @Value("${integrations.sri.vehicle-url}") String vehicleEndpoint) {
        this(restClient, URI.create(vehicleEndpoint));
    }

    SriVehiculoHttpAdapter(RestClient restClient, URI vehicleEndpoint) {
        this.restClient = Objects.requireNonNull(restClient, "El cliente HTTP es obligatorio.");
        this.vehicleEndpoint = Objects.requireNonNull(vehicleEndpoint, "El endpoint vehicular es obligatorio.");
    }

    @Override
    public List<Vehiculo> consultarPorIdentificador(String identificador) {
        if (identificador == null || identificador.isBlank()) {
            throw new IllegalArgumentException("El identificador vehicular es obligatorio.");
        }

        URI requestUri = UriComponentsBuilder.fromUri(vehicleEndpoint)
                .queryParam("numeroPlacaCampvCpn", "{value}")
                .encode(StandardCharsets.UTF_8)
                .buildAndExpand(identificador)
                .toUri();

        JsonNode response = SriResponseSupport.getJson(
                restClient, requestUri, SriResponseSupport.Operation.VEHICLE_LOOKUP);
        if (!response.isObject()) {
            SriResponseSupport.logUnexpectedStructure(
                    SriResponseSupport.Operation.VEHICLE_LOOKUP, "expected_object", response);
            throw SriIntegrationException.invalidResponse();
        }

        JsonNode data = response.get("data");
        if (data == null) {
            return List.of(mapVehicle(response));
        }
        if (!data.isArray()) {
            SriResponseSupport.logUnexpectedStructure(
                    SriResponseSupport.Operation.VEHICLE_LOOKUP, "expected_data_array", data);
            throw SriIntegrationException.invalidResponse();
        }

        List<Vehiculo> vehicles = new ArrayList<>(data.size());
        for (JsonNode item : data) {
            if (!item.isObject()) {
                SriResponseSupport.logUnexpectedStructure(
                        SriResponseSupport.Operation.VEHICLE_LOOKUP, "expected_vehicle_object", item);
                throw SriIntegrationException.invalidResponse();
            }
            vehicles.add(mapVehicle(item));
        }
        return List.copyOf(vehicles);
    }

    private static Vehiculo mapVehicle(JsonNode item) {
        Map<String, ValorCampoVehiculo> attributes = new LinkedHashMap<>();
        Vehiculo.CAMPOS_SRI_OBSERVADOS.forEach(name -> {
            JsonNode value = item.get(name);
            if (value != null) {
                attributes.put(name, mapScalar(value));
            }
        });
        ValorCampoVehiculo vehicleCode = attributes.get("codigoVehiculo");
        if (vehicleCode == null || vehicleCode.tipoDato() == ValorCampoVehiculo.TipoDato.NULO) {
            SriResponseSupport.logUnexpectedStructure(
                    SriResponseSupport.Operation.VEHICLE_LOOKUP, "required_vehicle_code_missing", item);
            throw SriIntegrationException.invalidResponse();
        }
        return new Vehiculo(attributes);
    }

    private static ValorCampoVehiculo mapScalar(JsonNode value) {
        if (value.isNull()) {
            return new ValorCampoVehiculo(ValorCampoVehiculo.TipoDato.NULO, null);
        }
        if (value.isTextual()) {
            return new ValorCampoVehiculo(ValorCampoVehiculo.TipoDato.TEXTO, value.textValue());
        }
        if (value.isNumber()) {
            return new ValorCampoVehiculo(ValorCampoVehiculo.TipoDato.NUMERO, value.asText());
        }
        if (value.isBoolean()) {
            return new ValorCampoVehiculo(ValorCampoVehiculo.TipoDato.BOOLEANO, value.asText());
        }
        throw SriIntegrationException.invalidResponse();
    }
}
