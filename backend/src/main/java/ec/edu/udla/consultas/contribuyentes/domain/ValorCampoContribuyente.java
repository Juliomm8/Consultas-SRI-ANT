package ec.edu.udla.consultas.contribuyentes.domain;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** JSON-neutral immutable value used to retain the observed SRI field types. */
public record ValorCampoContribuyente(TipoDato tipoDato, Object valor) {

    public enum TipoDato {
        TEXTO,
        NUMERO,
        BOOLEANO,
        NULO,
        OBJETO,
        ARREGLO
    }

    public ValorCampoContribuyente {
        Objects.requireNonNull(tipoDato, "El tipo de dato del campo es obligatorio.");
        valor = switch (tipoDato) {
            case TEXTO -> requireType(valor, String.class, tipoDato);
            case NUMERO -> requireType(valor, BigDecimal.class, tipoDato);
            case BOOLEANO -> requireType(valor, Boolean.class, tipoDato);
            case NULO -> requireNull(valor);
            case OBJETO -> immutableObject(valor);
            case ARREGLO -> immutableArray(valor);
        };
    }

    public static ValorCampoContribuyente texto(String value) {
        return new ValorCampoContribuyente(TipoDato.TEXTO, value);
    }

    public static ValorCampoContribuyente numero(BigDecimal value) {
        return new ValorCampoContribuyente(TipoDato.NUMERO, value);
    }

    public static ValorCampoContribuyente booleano(Boolean value) {
        return new ValorCampoContribuyente(TipoDato.BOOLEANO, value);
    }

    public static ValorCampoContribuyente nulo() {
        return new ValorCampoContribuyente(TipoDato.NULO, null);
    }

    public static ValorCampoContribuyente objeto(Map<String, ValorCampoContribuyente> value) {
        return new ValorCampoContribuyente(TipoDato.OBJETO, value);
    }

    public static ValorCampoContribuyente arreglo(List<ValorCampoContribuyente> value) {
        return new ValorCampoContribuyente(TipoDato.ARREGLO, value);
    }

    /** Returns an immutable Java value tree suitable for JSON serialization at an API boundary. */
    public Object asJsonValue() {
        return switch (tipoDato) {
            case TEXTO, NUMERO, BOOLEANO -> valor;
            case NULO -> null;
            case OBJETO -> objectAsJsonValue();
            case ARREGLO -> arrayAsJsonValue();
        };
    }

    @Override
    public String toString() {
        return "ValorCampoContribuyente[tipoDato=" + tipoDato + ", valor=redactado]";
    }

    private static Object requireType(Object value, Class<?> expectedType, TipoDato tipoDato) {
        if (!expectedType.isInstance(value)) {
            throw new IllegalArgumentException("El valor no coincide con el tipo " + tipoDato + ".");
        }
        return value;
    }

    private static Object requireNull(Object value) {
        if (value != null) {
            throw new IllegalArgumentException("Un campo nulo no puede contener un valor.");
        }
        return null;
    }

    private static Map<String, ValorCampoContribuyente> immutableObject(Object value) {
        if (!(value instanceof Map<?, ?> input)) {
            throw new IllegalArgumentException("Un campo de tipo objeto debe contener propiedades.");
        }
        Map<String, ValorCampoContribuyente> copy = new LinkedHashMap<>();
        input.forEach((key, field) -> {
            if (!(key instanceof String name) || !(field instanceof ValorCampoContribuyente typedValue)) {
                throw new IllegalArgumentException("Las propiedades del objeto deben tener nombres y tipos válidos.");
            }
            copy.put(name, typedValue);
        });
        return Collections.unmodifiableMap(copy);
    }

    private static List<ValorCampoContribuyente> immutableArray(Object value) {
        if (!(value instanceof List<?> input)) {
            throw new IllegalArgumentException("Un campo de tipo arreglo debe contener elementos.");
        }
        List<ValorCampoContribuyente> copy = new ArrayList<>(input.size());
        for (Object item : input) {
            if (!(item instanceof ValorCampoContribuyente typedValue)) {
                throw new IllegalArgumentException("Los elementos del arreglo deben tener tipos explícitos.");
            }
            copy.add(typedValue);
        }
        return Collections.unmodifiableList(copy);
    }

    private Map<String, Object> objectAsJsonValue() {
        Map<String, Object> result = new LinkedHashMap<>();
        objectValue().forEach((name, field) -> result.put(name, field.asJsonValue()));
        return Collections.unmodifiableMap(result);
    }

    private List<Object> arrayAsJsonValue() {
        List<Object> result = new ArrayList<>();
        arrayValue().forEach(field -> result.add(field.asJsonValue()));
        return Collections.unmodifiableList(result);
    }

    @SuppressWarnings("unchecked")
    private Map<String, ValorCampoContribuyente> objectValue() {
        return (Map<String, ValorCampoContribuyente>) valor;
    }

    @SuppressWarnings("unchecked")
    private List<ValorCampoContribuyente> arrayValue() {
        return (List<ValorCampoContribuyente>) valor;
    }
}
