# Contrato REST v1 — Consultas SRI ANT

**Estado:** propuesta interna. Este documento define el contrato previsto; no implica que existan endpoints de consulta ni que las integraciones SRI o ANT estén implementadas.

## Convenciones

- Las solicitudes y respuestas usan JSON con UTF-8.
- Cada operación usa `POST` y recibe sus datos en el cuerpo.
- Los ejemplos son ilustrativos. `0000000000000` es un RUC sintético y no representa una identificación validada.
- `requestId` es un UUID generado por el backend. Si se devuelve el encabezado `X-Request-Id`, debe coincidir con el valor del cuerpo. El backend no confía en un identificador enviado por el cliente.
- Los ejemplos de respuesta describen sobres contractuales. No son respuestas reales de SRI o ANT y no deben simularse en endpoints de producción.

## Operaciones

### Consultar contribuyente

`POST /api/v1/contribuyentes/consultas`

Entrada:

```json
{
  "email": "usuario@ejemplo.com",
  "ruc": "0000000000000"
}
```

El comportamiento futuro validará la identificación de persona natural, verificará su existencia en SRI y obtendrá la información disponible del contribuyente. El contrato no presupone nombres ni campos devueltos por SRI.

### Consultar vehículo

`POST /api/v1/vehiculos/consultas`

Entrada:

```json
{
  "placa": "ABC1234"
}
```

El comportamiento futuro consultará el vehículo por placa en SRI. Una consulta de vehículo no implica ni verifica que este pertenezca al contribuyente que envía la solicitud.

### Consultar puntos de licencia

`POST /api/v1/licencias/puntos/consultas`

Entrada:

```json
{
  "ruc": "0000000000000"
}
```

El comportamiento futuro verificará que el RUC corresponda a una persona natural, obtendrá su cédula solo mediante una regla confirmada y consultará ANT. No se acepta una cédula arbitraria enviada por el navegador.

## Respuestas exitosas previstas

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

La forma de `data` queda pendiente de confirmar con la información que realmente provea cada integración. `retrievedAt` indica cuándo el backend obtuvo la respuesta.

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

Para ANT, `source` será `ANT` o `CACHE`; `stale` indicará si se entrega un resultado antiguo; `fetchedAt` señalará cuándo se obtuvo originalmente ese dato; y `warning` explicará el uso de información antigua. Los nombres y tipos de los datos de puntos deben confirmarse antes de implementarlos.

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

| HTTP | Código estable previsto | Uso |
|---|---|---|
| 400 | `VALIDATION_ERROR` | Uno o más campos no cumplen el contrato. |
| 400 | `MALFORMED_JSON` | El cuerpo no puede interpretarse como JSON. |
| 404 | `NOT_FOUND` | El proveedor confirma que el dato solicitado no existe. |
| 422 | `IDENTIFICATION_NOT_NATURAL_PERSON` | El RUC tiene formato válido, pero no corresponde a persona natural. |
| 502 | `PROVIDER_BAD_RESPONSE` | Respuesta externa inesperada o ilegible. |
| 503 | `PROVIDER_UNAVAILABLE` | Proveedor no disponible y sin respaldo utilizable. |
| 504 | `PROVIDER_TIMEOUT` | Timeout del proveedor sin respaldo utilizable. |
| 500 | `INTERNAL_ERROR` | Error interno inesperado. |

Las respuestas no incluirán trazas, nombres de clases internas, valores de campos recibidos ni detalles sensibles. En este bloque se implementan los errores 400 de validación y JSON mal formado, además del 500 inesperado. Los errores dependientes de proveedores quedan documentados para bloques posteriores.

## Resultados parciales y caché ANT

Las tres rutas son operaciones independientes y no forman una consulta agregada. Una ruta no devolverá un resultado exitoso parcial ni combinará datos de varias fuentes.

Para SRI no se define fallback con datos antiguos en este contrato. Si la futura integración no obtiene una respuesta utilizable, aplicará el error correspondiente. Para ANT, el backend podrá devolver el último resultado válido de Redis únicamente cuando una política futura lo considere utilizable; la respuesta deberá señalar `source: CACHE`, `stale: true`, `fetchedAt` y `warning`. Sin una respuesta ANT ni caché utilizable se usará 503 o 504 según la causa.

Quedan por decidir el TTL de Redis, la edad máxima aceptable, los criterios de validez del dato, la respuesta en caso de caché vencida y los campos reales de la respuesta ANT.

## Decisiones pendientes

- Confirmar disponibilidad, reglas de uso y estructura de respuestas de las consultas SRI.
- Contrastar con la fuente oficial las reglas de RUC y de identificación de persona natural.
- Verificar técnicamente el acceso a ANT y la regla permitida para obtener la cédula a partir del RUC.
- Definir las condiciones de timeout, errores del proveedor, TTL y vigencia del respaldo Redis.
- Confirmar los campos de `data`, la semántica exacta de las fechas y si `warning` será nulo o estará ausente cuando los datos no sean antiguos.
