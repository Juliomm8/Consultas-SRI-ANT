package ec.edu.udla.consultas.contribuyentes.adapter.out.sri;

import com.fasterxml.jackson.databind.JsonNode;
import ec.edu.udla.consultas.contribuyentes.application.port.out.ContribuyenteConsultaPort;
import ec.edu.udla.consultas.contribuyentes.domain.Contribuyente;
import ec.edu.udla.consultas.contribuyentes.domain.Ruc;
import ec.edu.udla.consultas.contribuyentes.domain.TipoPersona;
import ec.edu.udla.consultas.contribuyentes.domain.ValorCampoContribuyente;
import ec.edu.udla.consultas.shared.integration.sri.SriIntegrationException;
import ec.edu.udla.consultas.shared.integration.sri.SriResponseSupport;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

@Component
public class SriContribuyenteHttpAdapter implements ContribuyenteConsultaPort {

    private static final Set<String> OBSERVED_DATE_FIELDS = Set.of(
            "fechaInicioActividades",
            "fechaCese",
            "fechaReinicioActividades",
            "fechaActualizacion");

    private static final URI EXISTENCE_ENDPOINT = URI.create(
            "https://srienlinea.sri.gob.ec/sri-catastro-sujeto-servicio-internet/rest/ConsolidadoContribuyente/existePorNumeroRuc");
    private static final URI DETAILS_ENDPOINT = URI.create(
            "https://srienlinea.sri.gob.ec/sri-catastro-sujeto-servicio-internet/rest/ConsolidadoContribuyente/obtenerPorNumerosRuc");

    private final RestClient restClient;
    private final URI existenceEndpoint;
    private final URI detailsEndpoint;

    @Autowired
    public SriContribuyenteHttpAdapter(@Qualifier("sriRestClient") RestClient restClient) {
        this(restClient, EXISTENCE_ENDPOINT, DETAILS_ENDPOINT);
    }

    SriContribuyenteHttpAdapter(RestClient restClient, URI existenceEndpoint, URI detailsEndpoint) {
        this.restClient = Objects.requireNonNull(restClient, "El cliente HTTP es obligatorio.");
        this.existenceEndpoint = Objects.requireNonNull(existenceEndpoint, "El endpoint de existencia es obligatorio.");
        this.detailsEndpoint = Objects.requireNonNull(detailsEndpoint, "El endpoint de detalle es obligatorio.");
    }

    @Override
    public Optional<Contribuyente> consultarPor(Ruc ruc) {
        Objects.requireNonNull(ruc, "El RUC es obligatorio.");

        URI existenceUri = withQueryParameter(existenceEndpoint, "numeroRuc", ruc.value());
        JsonNode existence = SriResponseSupport.getJson(restClient, existenceUri);
        if (!existence.isBoolean()) {
            throw SriIntegrationException.invalidResponse();
        }
        if (!existence.booleanValue()) {
            return Optional.empty();
        }

        URI detailsUri = withQueryParameter(detailsEndpoint, "ruc", ruc.value());
        JsonNode details = SriResponseSupport.getJson(restClient, detailsUri);
        if (!details.isObject() || !matchesRequestedRuc(details, ruc)) {
            throw SriIntegrationException.invalidResponse();
        }

        return Optional.of(new Contribuyente(ruc, mapTipoPersona(details), mapObservedFields(details)));
    }

    private static TipoPersona mapTipoPersona(JsonNode details) {
        JsonNode taxpayerType = details.get("tipoContribuyente");
        if (taxpayerType == null || !taxpayerType.isTextual()) {
            return TipoPersona.NO_DETERMINADA;
        }
        return "PERSONA NATURAL".equals(taxpayerType.textValue())
                ? TipoPersona.NATURAL
                : TipoPersona.NO_DETERMINADA;
    }

    private static Map<String, ValorCampoContribuyente> mapObservedFields(JsonNode details) {
        Map<String, ValorCampoContribuyente> fields = new LinkedHashMap<>();
        Iterator<Map.Entry<String, JsonNode>> properties = details.fields();
        while (properties.hasNext()) {
            Map.Entry<String, JsonNode> property = properties.next();
            String name = property.getKey();
            if (!Contribuyente.CAMPOS_SRI_OBSERVADOS.contains(name)) {
                continue;
            }
            JsonNode value = property.getValue();
            if ("informacionFechasContribuyente".equals(name) && value.isObject()) {
                fields.put(name, mapDateFields(value));
            } else {
                fields.put(name, mapValue(value));
            }
        }
        return fields;
    }

    private static ValorCampoContribuyente mapDateFields(JsonNode dates) {
        Map<String, ValorCampoContribuyente> fields = new LinkedHashMap<>();
        Iterator<Map.Entry<String, JsonNode>> properties = dates.fields();
        while (properties.hasNext()) {
            Map.Entry<String, JsonNode> property = properties.next();
            if (OBSERVED_DATE_FIELDS.contains(property.getKey())) {
                fields.put(property.getKey(), mapValue(property.getValue()));
            }
        }
        return ValorCampoContribuyente.objeto(fields);
    }

    private static ValorCampoContribuyente mapValue(JsonNode value) {
        if (value.isNull()) {
            return ValorCampoContribuyente.nulo();
        }
        if (value.isTextual()) {
            return ValorCampoContribuyente.texto(value.textValue());
        }
        if (value.isNumber()) {
            return ValorCampoContribuyente.numero(value.decimalValue());
        }
        if (value.isBoolean()) {
            return ValorCampoContribuyente.booleano(value.booleanValue());
        }
        if (value.isObject()) {
            Map<String, ValorCampoContribuyente> fields = new LinkedHashMap<>();
            Iterator<Map.Entry<String, JsonNode>> properties = value.fields();
            while (properties.hasNext()) {
                Map.Entry<String, JsonNode> property = properties.next();
                fields.put(property.getKey(), mapValue(property.getValue()));
            }
            return ValorCampoContribuyente.objeto(fields);
        }
        if (value.isArray()) {
            List<ValorCampoContribuyente> items = new ArrayList<>(value.size());
            value.forEach(item -> items.add(mapValue(item)));
            return ValorCampoContribuyente.arreglo(items);
        }
        throw SriIntegrationException.invalidResponse();
    }

    private static boolean matchesRequestedRuc(JsonNode details, Ruc requestedRuc) {
        JsonNode returnedRuc = details.get("numeroRuc");
        if (returnedRuc == null || returnedRuc.isNull()) {
            return false;
        }
        if (!returnedRuc.isTextual() && !returnedRuc.isIntegralNumber()) {
            return false;
        }
        return requestedRuc.value().equals(returnedRuc.asText());
    }

    private static URI withQueryParameter(URI endpoint, String name, String value) {
        return UriComponentsBuilder.fromUri(endpoint)
                .queryParam(name, "{value}")
                .encode(StandardCharsets.UTF_8)
                .buildAndExpand(value)
                .toUri();
    }
}
