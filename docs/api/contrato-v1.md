# Contrato REST v1 — Consultas SRI ANT

**Estado:** implementadas las operaciones SRI de contribuyentes y vehículos. El contrato normalizado de puntos está expuesto solo por un simulador local en el perfil `local-simulation`; la integración ANT real continúa pendiente.

## Convenciones

- Las solicitudes y respuestas usan JSON con UTF-8.
- Cada operación usa `POST` y recibe sus datos en el cuerpo.
- Los ejemplos son sintéticos. `0000000000000` es un valor centinela y no representa una identificación validada.
- `requestId` es un UUID generado por el backend. Si se devuelve el encabezado `X-Request-Id`, debe coincidir con el valor del cuerpo. El backend no confía en un identificador enviado por el cliente.
- Los ejemplos de respuesta describen sobres contractuales. No son respuestas reales de SRI o ANT. Los resultados simulados de puntos se limitan al perfil local enlazado a loopback y se etiquetan `SIMULATED`.

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

La respuesta interna de detalles SRI se observó como objeto en una consulta autorizada previa y como arreglo raíz el 15 de noviembre de 2026. El adaptador admite ambas formas respaldadas por la evidencia y los fixtures. En un arreglo selecciona un único registro cuyo `numeroRuc` coincida exactamente con el RUC consultado; no escoge el primer elemento. Arreglo vacío, ausencia de coincidencia, coincidencias duplicadas, elementos que no sean objetos o identificadores ausentes/no admitidos producen `502 PROVIDER_BAD_RESPONSE`, nunca `404 NOT_FOUND`. Esta normalización interna no cambia el contrato REST: `data` continúa siendo el DTO objeto del contribuyente.

### Consultar vehículo — implementado

`POST /api/v1/vehiculos/consultas`

Entrada:

```json
{
  "placa": "ABC1234"
}
```

El backend valida la placa e invoca el adaptador SRI. Un objeto de vehículo devuelto en la raíz se representa como un elemento de `data`; la variante compatible `data: [...]` también se conserva. Una lista vacía se devuelve como HTTP 200 con `data: []`: la semántica general de la respuesta negativa del proveedor no está confirmada. Una consulta por placa no implica ni verifica titularidad.

### Consultar puntos de licencia — contrato normalizado, simulación local

`POST /api/v1/licencias/puntos/consultas`

Entrada:

```json
{
  "cedula": "0000000000"
}
```

El contrato recibe `cedula` y valida exactamente 10 dígitos ASCII; no comprueba dígito verificador ni existencia y no deriva cédulas de RUC. El requisito funcional es consultar el total de puntos, pero la URL investigada `clp_grid_citaciones.jsp` menciona citaciones. No se ha confirmado que ambos trámites sean equivalentes ni la función de `ps_placa`; por ello la placa no forma parte de esta entrada. En el adaptador real futuro, `ps_tipo_identificacion=CED` será fijo.

Este endpoint solo se registra en el perfil explícito `local-simulation`, que enlaza el servidor a `127.0.0.1`. No consulta ANT: devuelve un valor de prueba y siempre declara `source: "SIMULATED"`. El esquema siguiente es propio de la aplicación y no reproduce una respuesta JSON o HTML real de ANT:

```json
{
  "data": { "totalPuntos": 20 },
  "meta": {
    "requestId": "550e8400-e29b-41d4-a716-446655440000",
    "source": "SIMULATED",
    "retrievedAt": "2026-11-15T16:00:00Z",
    "fetchedAt": "2026-11-15T16:00:00Z",
    "stale": false,
    "warning": "Resultado SIMULATED para pruebas locales; no es información oficial de ANT."
  }
}
```

`20` y la fecha del ejemplo son sintéticos. Un `totalPuntos` igual a cero es un valor presente; una consulta sin información es `404 NOT_FOUND`, no cero. Si la futura consulta ANT falla técnicamente, el caso de uso intenta el último resultado válido en Redis. El fallback devuelve `source: "CACHE"`, conserva `fetchedAt`, marca `stale: true` e incluye una advertencia. El simulador nunca se guarda como ANT. Sin dato cacheado, se mantiene el error técnico `502`, `503` o `504` que corresponda.

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

Para una consulta real futura, `source` podrá distinguir `ANT` de `CACHE`; `stale` indicará si se entrega un resultado anterior; `fetchedAt` conservará la fecha original; y `warning` explicará el uso de información potencialmente desactualizada. El contrato normalizado de puntos (`data.totalPuntos` y esta metadata) sí está implementado en el endpoint de simulación local. No reproduce una respuesta real de ANT: todavía se desconocen los campos, tipos y semántica de la respuesta del proveedor.

## Validaciones de entrada

| Campo | Validación sintáctica | Validación de negocio pendiente |
|---|---|---|
| `email` | Obligatorio, formato de correo y máximo 254 caracteres. | Confirmar propósito operativo y política de retención. |
| `ruc` | Obligatorio y exactamente 13 dígitos ASCII. | Dígito verificador, reglas oficiales, existencia y tipo de persona natural. |
| `cedula` | Obligatoria solo en el endpoint local de simulación de puntos; exactamente 10 dígitos ASCII. | Dígito verificador, existencia de la persona y mecanismo autorizado de consulta ANT. No se deriva del RUC. |
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
| 502 | `PROVIDER_BAD_RESPONSE` | Respuesta inválida o error HTTP del proveedor. |
| 503 | `PROVIDER_UNAVAILABLE` | El proveedor no está disponible o el circuito está abierto. |
| 504 | `PROVIDER_TIMEOUT` | El proveedor excedió el timeout configurado. |
| 500 | `INTERNAL_ERROR` | Error interno inesperado. |

Los errores anteriores están implementados en `GlobalExceptionHandler`. Las respuestas no incluyen trazas, nombres de clases internas, valores recibidos, cuerpos de SRI ni detalles sensibles. El header `X-Request-Id` coincide con `error.requestId`. Los errores técnicos no se transforman en ausencia de datos.

## Resultados parciales y caché ANT

Las rutas SRI son operaciones independientes y no forman una consulta agregada. Una ruta no devolverá un resultado exitoso parcial ni combinará datos de varias fuentes.

Para SRI no se define fallback con datos antiguos. Para puntos, el caso de uso ya implementa el fallback al puerto Redis ante un fallo técnico y conserva la fecha original. La infraestructura Redis persiste solo `totalPuntos`, `fetchedAt` y `source=ANT`; la cédula solo participa en una clave HMAC-SHA-256 y no aparece en el payload. Redis sigue desactivado por defecto. No existe un adaptador real que pueda producir resultados `ANT`, así que no se ha verificado el fallback con datos provenientes del proveedor. El Circuit Breaker `antPoints` envuelve la frontera del simulador bajo el perfil local y no una llamada real.

La integración real ANT continúa pendiente de una respuesta funcional y un flujo autorizado. El trámite oficial del certificado es un flujo web para el usuario y no confirma una API automática. El contrato normalizado de la aplicación no afirma conocer los campos reales de ANT. Quedan por confirmar el mecanismo de consulta, la semántica de ausencia de información, el uso de `ps_placa`, los criterios para validar un total recibido, la política de TTL y la vigencia máxima aceptable de un dato cacheado.

## Decisiones pendientes

- Confirmar reglas de uso y valores semánticos aún no verificados de las consultas SRI, incluida la clasificación para personas jurídicas y el significado general de `data: []` en vehículos.
- Contrastar con la fuente oficial las reglas de RUC y de identificación de persona natural.
- Verificar el mecanismo autorizado para obtener y usar directamente la cédula requerida por ANT; no derivarla del RUC.
- Definir las condiciones de timeout, errores del proveedor, TTL y vigencia del respaldo Redis.
- Confirmar cómo se extrae y valida `totalPuntos` desde una respuesta funcional ANT; `data.totalPuntos` es el modelo normalizado interno, no un campo confirmado del proveedor.
- Confirmar la semántica exacta de la ausencia de información, las fechas y si `warning` será nulo o estará ausente cuando los datos no sean antiguos.
