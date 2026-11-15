# Contrato REST v1 — Consultas SRI ANT

**Estado:** implementadas las operaciones de contribuyentes y vehículos SRI. La operación de puntos ANT sigue siendo una propuesta. La implementación SRI usa los adaptadores salientes existentes y no implica que ANT esté disponible.

## Convenciones

- Las solicitudes y respuestas usan JSON con UTF-8.
- Cada operación usa `POST` y recibe sus datos en el cuerpo.
- Los ejemplos son sintéticos. `0000000000000` es un valor centinela y no representa una identificación validada.
- `requestId` es un UUID generado por el backend. Si se devuelve el encabezado `X-Request-Id`, debe coincidir con el valor del cuerpo. El backend no confía en un identificador enviado por el cliente.
- Los ejemplos de respuesta describen sobres contractuales. No son respuestas reales de SRI o ANT y no deben simularse en endpoints de producción.

## Operaciones

### Consultar contribuyente — implementado

`POST /api/v1/contribuyentes/consultas`

Entrada:

```json
{
  "email": "usuario@ejemplo.com",
  "ruc": "0000000000000"
}
```

El backend valida el formato estructural del RUC, confirma su existencia en SRI y solicita los detalles solo tras una respuesta afirmativa. La solicitud también valida `email`, pero el correo no se envía a SRI, no se almacena y no se devuelve.

Solo está confirmada la equivalencia SRI `PERSONA NATURAL` → `NATURAL`. Los valores desconocidos, ausentes, nulos o no textuales producen `TAXPAYER_TYPE_UNDETERMINED`; no se infieren equivalencias para personas jurídicas. El adaptador conserva únicamente las propiedades observadas y permitidas, sus tipos JSON y las cuatro propiedades conocidas dentro de `informacionFechasContribuyente`.

### Consultar vehículo — implementado

`POST /api/v1/vehiculos/consultas`

Entrada:

```json
{
  "placa": "ABC1234"
}
```

El backend valida la placa e invoca el adaptador SRI. Un objeto de vehículo devuelto en la raíz se representa como un elemento de `data`; la variante compatible `data: [...]` también se conserva. Una lista vacía se devuelve como HTTP 200 con `data: []`: la semántica general de la respuesta negativa del proveedor no está confirmada. Una consulta por placa no implica ni verifica titularidad.

### Consultar puntos de licencia — propuesta

`POST /api/v1/licencias/puntos/consultas`

Entrada:

```json
{
  "ruc": "0000000000000"
}
```

El comportamiento futuro verificará que el RUC corresponda a una persona natural, obtendrá su cédula solo mediante una regla confirmada y consultará ANT. No se acepta una cédula arbitraria enviada por el navegador. No hay controlador REST funcional para esta operación. El diagnóstico actual recibió HTTP 502 en el portal, el formulario de criterios y la vista de puntos; el flujo, los parámetros reales y el formato de respuesta no están confirmados. La ruta no promete ni devuelve puntos hasta obtener evidencia funcional.

## Respuestas exitosas

Sobre común:

```json
{
  "data": {},
  "meta": {
    "requestId": "550e8400-e29b-41d4-a716-446655440000",
    "source": "SRI",
    "retrievedAt": "2026-01-15T10:30:00Z"
  }
}
```

`source` es `SRI` en estas dos rutas. `retrievedAt` se genera al terminar la consulta y representa el momento de obtención de la respuesta. `requestId` coincide con `X-Request-Id`; el backend genera el identificador y no acepta el del cliente.

Ejemplo sintético de contribuyente (los campos dentro de `datosSRI` dependen de las propiedades permitidas presentes en la respuesta SRI):

```json
{
  "data": {
    "ruc": "0000000000000",
    "tipoPersona": "NATURAL",
    "datosSRI": {
      "numeroRuc": "0000000000000",
      "tipoContribuyente": "PERSONA NATURAL"
    }
  },
  "meta": {
    "requestId": "550e8400-e29b-41d4-a716-446655440000",
    "source": "SRI",
    "retrievedAt": "2026-11-14T20:00:00Z"
  }
}
```

Ejemplo sintético de vehículo (los tipos ilustran el sobre JSON y no reproducen valores de una consulta personal):

```json
{
  "data": [
    {
      "camposSRI": {
        "codigoVehiculo": 123,
        "numeroPlaca": "ABC1234",
        "numeroCamvCpn": null,
        "aplicaCuota": true
      }
    }
  ],
  "meta": {
    "requestId": "550e8400-e29b-41d4-a716-446655440000",
    "source": "SRI",
    "retrievedAt": "2026-11-14T20:00:00Z"
  }
}
```

Estos ejemplos no son respuestas literales del proveedor. Los DTO REST mantienen los números, booleanos, textos, nulos y estructuras anidadas de los campos permitidos.

Para puntos ANT, la metadata deberá permitir distinguir una consulta actual de una respuesta de caché. Por ejemplo:

```json
{
  "data": {},
  "meta": {
    "requestId": "550e8400-e29b-41d4-a716-446655440000",
    "source": "CACHE",
    "retrievedAt": "2026-01-15T10:30:00Z",
    "stale": true,
    "fetchedAt": "2026-01-14T08:00:00Z",
    "warning": "Se muestran datos almacenados anteriormente."
  }
}
```

Para ANT, `source` será `ANT` o `CACHE`; `stale` indicará si se entrega un resultado antiguo; `fetchedAt` señalará cuándo se obtuvo originalmente ese dato; y `warning` explicará el uso de información antigua. Esta forma no está implementada. Los nombres y tipos de los datos de puntos deben confirmarse antes de implementarlos.

## Validaciones de entrada

| Campo | Validación sintáctica | Validación de negocio pendiente |
|---|---|---|
| `email` | Obligatorio, formato de correo y máximo 254 caracteres. | Confirmar propósito operativo y política de retención. |
| `ruc` | Obligatorio y exactamente 13 dígitos ASCII. | Dígito verificador, reglas oficiales, existencia y tipo de persona natural. |
| `placa` | Obligatoria; letras y números, sin imponer una longitud fija en esta etapa. | Confirmar formatos vigentes y reglas del servicio SRI. |

La validación de RUC de este bloque es estructural. No calcula dígitos verificadores ni determina si el RUC existe o pertenece a una persona natural. No se valida la propiedad del vehículo.

## Errores HTTP

Todos los errores usarán el mismo sobre:

```json
{
  "error": {
    "code": "VALIDATION_ERROR",
    "message": "Los datos enviados no son válidos.",
    "requestId": "550e8400-e29b-41d4-a716-446655440000"
  }
}
```

| HTTP | Código estable | Uso |
|---|---|---|
| 400 | `VALIDATION_ERROR` | Uno o más campos no cumplen el contrato. |
| 400 | `MALFORMED_JSON` | El cuerpo no puede interpretarse como JSON. |
| 404 | `NOT_FOUND` | El proveedor confirma que el dato solicitado no existe. |
| 422 | `IDENTIFICATION_NOT_NATURAL_PERSON` | Para una clasificación jurídica confirmada; el adaptador actual no infiere `JURIDICA` mientras su valor SRI no esté verificado. |
| 422 | `TAXPAYER_TYPE_UNDETERMINED` | El proveedor no devolvió una clasificación reconocida. |
| 502 | `PROVIDER_BAD_RESPONSE` | Respuesta SRI inválida o error HTTP del proveedor. |
| 503 | `PROVIDER_UNAVAILABLE` | No fue posible comunicarse con SRI. |
| 504 | `PROVIDER_TIMEOUT` | SRI excedió el timeout configurado. |
| 500 | `INTERNAL_ERROR` | Error interno inesperado. |

Los errores anteriores están implementados en `GlobalExceptionHandler`. Las respuestas no incluyen trazas, nombres de clases internas, valores recibidos, cuerpos de SRI ni detalles sensibles. El header `X-Request-Id` coincide con `error.requestId`. Los errores técnicos no se transforman en ausencia de datos.

## Resultados parciales y caché ANT

Las tres rutas son operaciones independientes y no forman una consulta agregada. Una ruta no devolverá un resultado exitoso parcial ni combinará datos de varias fuentes.

Para SRI no se define fallback con datos antiguos en este contrato. Si la futura integración no obtiene una respuesta utilizable, aplicará el error correspondiente. Para ANT, el backend podrá devolver el último resultado válido de Redis únicamente cuando una política futura lo considere utilizable; la respuesta deberá señalar `source: CACHE`, `stale: true`, `fetchedAt` y `warning`. Sin una respuesta ANT ni caché utilizable se usará 503 o 504 según la causa.

La ruta ANT continúa pendiente de una respuesta funcional. Quedan por decidir el TTL de Redis, la edad máxima aceptable, los criterios de validez del dato, la respuesta en caso de caché vencida y los campos reales de la respuesta ANT.

## Decisiones pendientes

- Confirmar reglas de uso y valores semánticos aún no verificados de las consultas SRI, incluida la clasificación para personas jurídicas y el significado general de `data: []` en vehículos.
- Contrastar con la fuente oficial las reglas de RUC y de identificación de persona natural.
- Verificar técnicamente el acceso a ANT y la regla permitida para obtener la cédula a partir del RUC.
- Definir las condiciones de timeout, errores del proveedor, TTL y vigencia del respaldo Redis.
- Confirmar los campos de `data`, la semántica exacta de las fechas y si `warning` será nulo o estará ausente cuando los datos no sean antiguos.
