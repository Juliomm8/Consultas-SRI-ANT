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
import org.springframework.beans.factory.annotation.Value;
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

    private final RestClient restClient;
    private final URI existenceEndpoint;
    private final URI detailsEndpoint;

    @Autowired
    public SriContribuyenteHttpAdapter(
            @Qualifier("sriRestClient") RestClient restClient,
            @Value("${integrations.sri.taxpayer-existence-url}") String existenceEndpoint,
            @Value("${integrations.sri.taxpayer-details-url}") String detailsEndpoint) {
        this(restClient, URI.create(existenceEndpoint), URI.create(detailsEndpoint));
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
        JsonNode existence = SriResponseSupport.getJson(
                restClient, existenceUri, SriResponseSupport.Operation.TAXPAYER_EXISTENCE);
        if (!existence.isBoolean()) {
            SriResponseSupport.logUnexpectedStructure(
                    SriResponseSupport.Operation.TAXPAYER_EXISTENCE, "expected_boolean", existence);
            throw SriIntegrationException.invalidResponse();
        }
        if (!existence.booleanValue()) {
            return Optional.empty();
        }

        URI detailsUri = withQueryParameter(detailsEndpoint, "ruc", ruc.value());
        JsonNode detailsResponse = SriResponseSupport.getJson(
                restClient, detailsUri, SriResponseSupport.Operation.TAXPAYER_DETAILS);
        JsonNode details = selectTaxpayerRecord(detailsResponse, ruc);

        return Optional.of(new Contribuyente(
                ruc,
                mapTipoPersona(details),
                mapObservedFields(details, SriResponseSupport.Operation.TAXPAYER_DETAILS)));
    }

    private static JsonNode selectTaxpayerRecord(JsonNode detailsResponse, Ruc requestedRuc) {
        if (detailsResponse.isObject()) {
            if (!matchesRequestedRuc(detailsResponse, requestedRuc)) {
                SriResponseSupport.logUnexpectedStructure(
                        SriResponseSupport.Operation.TAXPAYER_DETAILS,
                        "object_record_identifier_missing_or_mismatched",
                        detailsResponse);
                throw SriIntegrationException.invalidResponse();
            }
            return detailsResponse;
        }

        if (!detailsResponse.isArray()) {
            SriResponseSupport.logUnexpectedStructure(
                    SriResponseSupport.Operation.TAXPAYER_DETAILS,
                    "expected_object_or_array",
                    detailsResponse);
            throw SriIntegrationException.invalidResponse();
        }
        if (detailsResponse.isEmpty()) {
            SriResponseSupport.logUnexpectedStructure(
                    SriResponseSupport.Operation.TAXPAYER_DETAILS,
                    "empty_details_array_after_positive_existence",
                    detailsResponse);
            throw SriIntegrationException.invalidResponse();
        }

        JsonNode matchingRecord = null;
        for (JsonNode record : detailsResponse) {
            if (!record.isObject()) {
                SriResponseSupport.logUnexpectedStructure(
                        SriResponseSupport.Operation.TAXPAYER_DETAILS,
                        "array_element_not_object",
                        record);
                throw SriIntegrationException.invalidResponse();
            }
            if (!hasSupportedRuc(record)) {
                SriResponseSupport.logUnexpectedStructure(
                        SriResponseSupport.Operation.TAXPAYER_DETAILS,
                        "array_record_identifier_missing_or_invalid",
                        record);
                throw SriIntegrationException.invalidResponse();
            }
            if (matchesRequestedRuc(record, requestedRuc)) {
                if (matchingRecord != null) {
                    SriResponseSupport.logUnexpectedStructure(
                            SriResponseSupport.Operation.TAXPAYER_DETAILS,
                            "multiple_records_match_requested_identifier",
                            detailsResponse);
                    throw SriIntegrationException.invalidResponse();
                }
                matchingRecord = record;
            }
        }

        if (matchingRecord == null) {
            SriResponseSupport.logUnexpectedStructure(
                    SriResponseSupport.Operation.TAXPAYER_DETAILS,
                    "no_record_matches_requested_identifier",
                    detailsResponse);
            throw SriIntegrationException.invalidResponse();
        }
        return matchingRecord;
    }

    private static TipoPersona mapTipoPersona(JsonNode details) {
        JsonNode taxpayerType = details.get("tipoContribuyente");
        if (taxpayerType == null) {
            SriResponseSupport.logTaxpayerClassification("missing", false);
            return TipoPersona.NO_DETERMINADA;
        }
        if (taxpayerType.isNull()) {
            SriResponseSupport.logTaxpayerClassification("null", false);
            return TipoPersona.NO_DETERMINADA;
        }
        if (!taxpayerType.isTextual()) {
            SriResponseSupport.logTaxpayerClassification("non_textual", false);
            return TipoPersona.NO_DETERMINADA;
        }
        boolean naturalPerson = "PERSONA NATURAL".equals(taxpayerType.textValue());
        SriResponseSupport.logTaxpayerClassification(
                naturalPerson ? "confirmed_natural" : "unrecognized_text",
                naturalPerson);
        return naturalPerson ? TipoPersona.NATURAL : TipoPersona.NO_DETERMINADA;
    }

    private static Map<String, ValorCampoContribuyente> mapObservedFields(
            JsonNode details,
            SriResponseSupport.Operation operation) {
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
                fields.put(name, mapDateFields(value, operation));
            } else {
                fields.put(name, mapValue(value, operation));
            }
        }
        return fields;
    }

    private static ValorCampoContribuyente mapDateFields(
            JsonNode dates,
            SriResponseSupport.Operation operation) {
        Map<String, ValorCampoContribuyente> fields = new LinkedHashMap<>();
        Iterator<Map.Entry<String, JsonNode>> properties = dates.fields();
        while (properties.hasNext()) {
            Map.Entry<String, JsonNode> property = properties.next();
            if (OBSERVED_DATE_FIELDS.contains(property.getKey())) {
                fields.put(property.getKey(), mapValue(property.getValue(), operation));
            }
        }
        return ValorCampoContribuyente.objeto(fields);
    }

    private static ValorCampoContribuyente mapValue(
            JsonNode value,
            SriResponseSupport.Operation operation) {
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
                fields.put(property.getKey(), mapValue(property.getValue(), operation));
            }
            return ValorCampoContribuyente.objeto(fields);
        }
        if (value.isArray()) {
            List<ValorCampoContribuyente> items = new ArrayList<>(value.size());
            value.forEach(item -> items.add(mapValue(item, operation)));
            return ValorCampoContribuyente.arreglo(items);
        }
        SriResponseSupport.logUnexpectedStructure(operation, "unsupported_json_value_type", value);
        throw SriIntegrationException.invalidResponse();
    }

    private static boolean matchesRequestedRuc(JsonNode details, Ruc requestedRuc) {
        JsonNode returnedRuc = details.get("numeroRuc");
        if (!hasSupportedRuc(details)) {
            return false;
        }
        return requestedRuc.value().equals(returnedRuc.asText());
    }

    private static boolean hasSupportedRuc(JsonNode details) {
        JsonNode returnedRuc = details.get("numeroRuc");
        return returnedRuc != null
                && !returnedRuc.isNull()
                && (returnedRuc.isTextual() || returnedRuc.isIntegralNumber());
    }

    private static URI withQueryParameter(URI endpoint, String name, String value) {
        return UriComponentsBuilder.fromUri(endpoint)
                .queryParam(name, "{value}")
                .encode(StandardCharsets.UTF_8)
                .buildAndExpand(value)
                .toUri();
    }
}
