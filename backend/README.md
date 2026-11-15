# Backend — Consultas SRI ANT

API Spring Boot para las consultas de contribuyentes y vehículos del SRI. Es una sola aplicación Java desplegable, organizada en módulos lógicos con arquitectura hexagonal ligera. Los adaptadores REST invocan casos de uso; estos dependen de puertos implementados por adaptadores salientes SRI.

El cliente web React + TypeScript está en `../frontend` y consume únicamente la API de este backend.

## Requisitos

- JDK 17.
- Maven Wrapper incluido (`mvnw` / `mvnw.cmd`); no se requiere Maven global. En el primer uso, el wrapper descarga la distribución configurada.
- Se necesita acceso de red para realizar consultas SRI en tiempo de ejecución. El arranque y la comprobación de salud no llaman a SRI.

## Iniciar desde un checkout limpio

Desde `backend/`, en PowerShell:

```powershell
.\mvnw.cmd -B clean verify
.\mvnw.cmd spring-boot:run
```

El servidor escucha en el puerto `8080` de forma predeterminada. Para comprobar el estado:

```powershell
Invoke-RestMethod -Uri http://localhost:8080/actuator/health -Method Get
```

Solo se expone el endpoint `health` de Actuator. Redis y ANT no son necesarios para iniciar esta versión.

## Endpoints implementados

| Método | Ruta | Comportamiento |
|---|---|---|
| `POST` | `/api/v1/contribuyentes/consultas` | Valida email y formato estructural del RUC; luego consulta SRI mediante el caso de uso. El email no se envía a SRI, no se almacena ni se devuelve. |
| `POST` | `/api/v1/vehiculos/consultas` | Valida la placa y consulta SRI mediante el caso de uso. No comprueba titularidad del vehículo. |

Las respuestas exitosas utilizan `{ "data": ..., "meta": { "requestId", "source", "retrievedAt" } }`. `source` es `SRI`; `requestId` coincide con el encabezado `X-Request-Id`. Los DTO REST conservan los tipos JSON escalares y anidados de los campos permitidos.

### Ejemplos PowerShell con valores sintéticos

Estos ejemplos muestran la forma de consumo local. El RUC y la placa son valores sintéticos y no demuestran una consulta exitosa a SRI.

```powershell
$taxpayerBody = @{
  email = 'usuario@ejemplo.com'
  ruc   = '0000000000000'
} | ConvertTo-Json

Invoke-RestMethod `
  -Uri 'http://localhost:8080/api/v1/contribuyentes/consultas' `
  -Method Post `
  -ContentType 'application/json' `
  -Body $taxpayerBody
```

```powershell
$vehicleBody = @{ placa = 'ABC1234' } | ConvertTo-Json

Invoke-RestMethod `
  -Uri 'http://localhost:8080/api/v1/vehiculos/consultas' `
  -Method Post `
  -ContentType 'application/json' `
  -Body $vehicleBody
```

## Validaciones y errores

- Email: obligatorio, formato de correo y máximo 254 caracteres. Se usa únicamente para validar la solicitud.
- RUC: obligatorio y exactamente 13 dígitos ASCII. No se calcula localmente el dígito verificador ni se infiere el tipo de persona.
- Placa: obligatoria, letras y números; no se impone una longitud fija.
- La ausencia confirmada del contribuyente produce `404 NOT_FOUND`.
- SRI `PERSONA NATURAL` se mapea a `TipoPersona.NATURAL`. Las clasificaciones desconocidas, ausentes, nulas o no textuales producen `422 TAXPAYER_TYPE_UNDETERMINED`. No se ha confirmado el valor SRI para personas jurídicas.
- Una respuesta vehicular vacía se mantiene como HTTP 200 con `data: []`; esto no afirma que el vehículo no exista definitivamente.
- Las respuestas SRI inválidas y los errores HTTP del proveedor producen `502 PROVIDER_BAD_RESPONSE`; los fallos de comunicación, `503 PROVIDER_UNAVAILABLE`; y los timeouts, `504 PROVIDER_TIMEOUT`.
- Los errores no devuelven cuerpos SRI, valores enviados ni trazas. El mismo `requestId` aparece en el header y en el cuerpo del error.

## Mapa de paquetes

- `contribuyentes/domain`: RUC, clasificación de persona, modelo de contribuyente y valores JSON tipados neutrales al proveedor.
- `contribuyentes/application`: puertos de entrada/salida y caso de uso de contribuyentes.
- `contribuyentes/adapter/in/rest`: controlador HTTP y DTOs de entrada/salida.
- `contribuyentes/adapter/out/sri`: adaptador HTTP SRI para contribuyentes.
- `vehiculos/domain` y `vehiculos/application`: campos observados, puertos y caso de uso de vehículos.
- `vehiculos/adapter/in/rest`: controlador HTTP y DTOs de entrada/salida.
- `vehiculos/adapter/out/sri`: adaptador HTTP SRI para vehículos.
- `shared/api`: sobre de respuesta, mapeo de errores e identificadores de solicitud.
- `shared/integration/sri`: cliente HTTP, timeouts y excepciones técnicas sanitizadas.

## Pruebas

Ejecuta todas las pruebas y genera el JAR ejecutable:

```powershell
.\mvnw.cmd -B clean verify
```

Las pruebas de adaptadores usan `MockRestServiceServer`; las de controladores usan `MockMvc` y resultados de caso de uso sintéticos. La prueba de salud inicia Spring Boot en un puerto local aleatorio. Las pruebas automatizadas no llaman servicios SRI reales.

## Límites actuales

- Los adaptadores salientes SRI están configurados; una consulta puede fallar si SRI no está disponible o responde con una estructura desconocida.
- Solo se mapea la clasificación confirmada `PERSONA NATURAL`. El valor SRI para personas jurídicas sigue sin verificarse.
- Consultar un vehículo no demuestra que pertenezca a un contribuyente.
- No está confirmado el significado general de la respuesta SRI `data: []` para vehículos.
- ANT no ha devuelto una respuesta funcional en el entorno de diagnóstico. Su parser, flujo de aplicación, caché Redis y fallback no están implementados.
