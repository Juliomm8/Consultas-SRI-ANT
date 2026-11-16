# Backend — Consultas SRI ANT

API Spring Boot para consultas SRI y para probar localmente el contrato normalizado propuesto de puntos de licencia. Es una sola aplicación Java desplegable, organizada en módulos lógicos con arquitectura hexagonal ligera. Los adaptadores REST invocan casos de uso; estos dependen de puertos implementados por adaptadores salientes. Los servicios de aplicación son Java puro e independientes de Spring; las configuraciones externas los ensamblan como beans.

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

## Infraestructura de caché Redis para puntos ANT

El puerto de caché y el adaptador Spring Data Redis están implementados, pero el
adaptador queda desactivado por defecto. Con `REDIS_CACHE_ENABLED=false` (valor
predeterminado) no se crea una conexión Redis ni un bean de caché; el arranque y
`/actuator/health` no requieren un servidor Redis.

Para una prueba local, primero inicia un Redis autorizado y configura el entorno
de la misma terminal PowerShell. El siguiente TTL es solo un ejemplo de
desarrollo, no una política de vigencia aprobada para ANT:

```powershell
$env:REDIS_CACHE_ENABLED = 'true'
$env:REDIS_HOST = '127.0.0.1'
$env:REDIS_PORT = '6379'
$env:REDIS_SSL_ENABLED = 'false'
$env:REDIS_CACHE_TTL = '15m'
$env:REDIS_CACHE_HMAC_SECRET = [Convert]::ToBase64String(
  [Security.Cryptography.RandomNumberGenerator]::GetBytes(32))
# REDIS_USERNAME y REDIS_PASSWORD solo se configuran cuando el servidor lo requiere.
.\mvnw.cmd spring-boot:run
```

Al habilitarla se requieren `REDIS_HOST`, un puerto válido, un TTL positivo y
`REDIS_CACHE_HMAC_SECRET` con al menos 32 bytes UTF-8. El secreto se debe
proporcionar por el entorno; no guardarlo en archivos ni registrarlo. Para Redis
Cloud, configura además las credenciales y TLS que requiera la instancia.

Las claves usan `consultas-sri-ant:ant-points:v2:` seguido de HMAC-SHA-256 de la
cédula y no contienen la identificación en texto plano. El puerto de caché
recibe el objeto de valor `Cedula`, no una cadena genérica. El contenido
resultado se almacena como JSON tipado con solo `totalPuntos`, `fetchedAt` y
`source=ANT`; no incluye la cédula ni un cuerpo opaco del proveedor. El esquema
es propio de la aplicación y no reproduce una respuesta ANT observada. El
adaptador rechaza documentos incompatibles, tipos incorrectos, campos
adicionales y fuentes distintas de `ANT`. Al recuperar se marca `source=CACHE`
y se conserva la fecha original. La suite automatizada usa mocks y no necesita
Redis real.

La expiración Redis configurada por TTL es el único límite de retención que
aplica actualmente el adaptador; no hay una segunda comprobación de antigüedad
contra `fetchedAt`. El ejemplo de TTL anterior es solo para desarrollo. Antes
de activar caché para una fuente ANT real debe acordarse el TTL y la vigencia
máxima aceptable del resultado.

La prueba de integración real es optativa y no forma parte de `clean verify`.
Con Docker disponible, se puede iniciar una instancia local enlazada solo a
loopback y ejecutar desde `backend/`:

```powershell
docker run --rm --detach --name consultas-sri-ant-redis-local -p 127.0.0.1:6379:6379 redis:7.4-alpine
.\mvnw.cmd -B -Dtest=RedisPuntosLicenciaCacheIntegrationIT -Dredis.integration.enabled=true -Dredis.integration.host=127.0.0.1 -Dredis.integration.port=6379 test
docker stop consultas-sri-ant-redis-local
```

Si el puerto local 6379 está ocupado, cambia el puerto publicado y el valor de
`redis.integration.port`. La prueba usa una cédula centinela sintética,
comprueba el guardado ANT existente y el recorrido éxito → timeout → recuperación
del caso de uso simulado contra Redis, incluyendo `fetchedAt`, procedencia,
namespace, contenido mínimo y TTL. El test elimina sus propias claves al
terminar. No llama a ANT. El contenedor se debe detener después;
la ejecución normal no inicia Docker ni Redis.

## Simulación local de puntos ANT

El contrato de puntos se puede ejercitar con datos sintéticos únicamente en el
perfil `local-simulation`. Ese perfil activa el endpoint
`POST /api/v1/licencias/puntos/consultas` y enlaza Spring Boot exclusivamente a
`127.0.0.1`; la configuración de infraestructura vuelve a comprobar esa
dirección antes de registrar el proveedor. Sin ese perfil y la propiedad
explícita `app.ant.simulation.enabled=true`, no se registra ni el endpoint ni el
simulador.

Desde `backend/`, en PowerShell, inicia el escenario exitoso:

```powershell
$env:SPRING_PROFILES_ACTIVE = 'local-simulation'
$env:ANT_SIMULATION_SCENARIO = 'SUCCESS_THEN_TIMEOUT'
.\mvnw.cmd spring-boot:run
```

Para probar específicamente la recuperación desde Redis con el simulador,
inicia Redis 7.4 enlazado a loopback y define `REDIS_CACHE_ENABLED=true`,
`REDIS_HOST`, `REDIS_PORT`, un `REDIS_CACHE_TTL` positivo y un
`REDIS_CACHE_HMAC_SECRET` de al menos 32 bytes en memoria de la sesión. No
guardes ese secreto en archivos. La primera consulta responde `SIMULATED` y se
guarda en `consultas-sri-ant:ant-points:simulation:v1:`; la segunda produce
timeout y recupera el mismo total y fecha como `source=CACHE`,
`originSource=SIMULATED` y `stale=true`. Las instrucciones completas están en
[`docs/pruebas/demo-ant-local.md`](../docs/pruebas/demo-ant-local.md).

El endpoint acepta solamente una cédula estructural de 10 dígitos ASCII. Para
probar con el centinela sintético ya usado por las pruebas locales:

```powershell
$body = @{ cedula = '0000000000' } | ConvertTo-Json
Invoke-RestMethod `
  -Uri 'http://127.0.0.1:8080/api/v1/licencias/puntos/consultas' `
  -Method Post `
  -ContentType 'application/json' `
  -Body $body
```

La respuesta sintética contiene únicamente `data.totalPuntos` y metadata con
`requestId`, `source`, `originSource`, `retrievedAt`, `fetchedAt`, `stale` y
`warning`. En la primera consulta `source=SIMULATED`, `originSource=SIMULATED` y
`stale=false`. El aviso indica que no es información oficial de ANT. El
centinela sirve solo para desarrollo; no ingreses identificaciones reales en
pruebas locales compartidas.

Los escenarios se eligen por configuración, no por parámetros HTTP:

| `ANT_SIMULATION_SCENARIO` | Comportamiento local |
|---|---|
| `SUCCESS` | Devuelve el total sintético configurable en `ANT_SIMULATION_TOTAL_POINTS` (por defecto 20). |
| `ZERO` | Devuelve un total sintético de cero como valor presente. |
| `NO_DATA` | Devuelve ausencia de información (`404 NOT_FOUND`), no cero. |
| `TIMEOUT` | Simula la clasificación de timeout (`504`); no espera ni realiza red. |
| `UNAVAILABLE` | Simula indisponibilidad (`503`); no realiza red. |
| `INVALID_RESPONSE` | Simula una respuesta no utilizable (`502`) sin generar ni guardar un cuerpo del proveedor. |
| `RECOVERY` | La primera invocación simula indisponibilidad y las siguientes devuelven un resultado sintético. |
| `SUCCESS_THEN_TIMEOUT` | La primera invocación devuelve el resultado sintético y lo guarda solo en la caché local de simulación; las siguientes lanzan timeout para probar el fallback. No espera ni realiza solicitudes de red. |

El `CircuitBreaker` `antPoints` envuelve solo el puerto saliente activo en este
perfil y cuenta únicamente `PuntosLicenciaProviderException`; la ausencia de
información y errores de validación no se cuentan como fallos del proveedor.
Esto prueba la frontera de resiliencia con un simulador y no protege una llamada
real a ANT. Redis continúa desactivado por defecto. Fuera de `local-simulation`,
el puerto Redis existente conserva el namespace
`consultas-sri-ant:ant-points:v2:` y acepta solo resultados `ANT`. En el perfil
`local-simulation`, con Redis habilitado explícitamente, se registra un puerto
independiente que escribe únicamente `SIMULATED` bajo
`consultas-sri-ant:ant-points:simulation:v1:`. Ambos usan HMAC-SHA-256; la
identificación no aparece en la clave ni en el payload. El documento almacena
solo `totalPuntos`, `fetchedAt` y la fuente de origen.

Si el proveedor simulado falla después de un éxito, el caso de uso recupera ese
resultado local y responde `source=CACHE`, `originSource=SIMULATED`,
`stale=true`, conserva el `fetchedAt` original y avisa que no es información
oficial y puede estar desactualizada. Una ausencia legítima de información no
activa el fallback. La demo React acepta únicamente resultados frescos
`SIMULATED` o resultados `CACHE` cuyo origen explícito sea `SIMULATED`.

La integración ANT real sigue sin adaptador y no ha producido datos `ANT` en la
aplicación. Este flujo demuestra Redis local con datos sintéticos, no una
consulta real, ni una conexión a Redis Cloud.

## Endpoints implementados

| Método | Ruta | Comportamiento |
|---|---|---|
| `POST` | `/api/v1/contribuyentes/consultas` | Valida email y formato estructural del RUC; luego consulta SRI mediante el caso de uso. El email no se envía a SRI, no se almacena ni se devuelve. |
| `POST` | `/api/v1/vehiculos/consultas` | Valida la placa y consulta SRI mediante el caso de uso. No comprueba titularidad del vehículo. |
| `POST` | `/api/v1/licencias/puntos/consultas` | Solo con el perfil local `local-simulation`; devuelve `SIMULATED` en una consulta nueva o `CACHE` con `originSource=SIMULATED` al recuperar el resultado simulado desde el namespace Redis aislado. No consulta ANT. |

Las respuestas SRI exitosas utilizan `{ "data": ..., "meta": { "requestId", "source", "retrievedAt" } }`, con `source=SRI`; `requestId` coincide con el encabezado `X-Request-Id`. La ruta de puntos local usa el esquema extendido documentado arriba. En él, `source` describe la respuesta actual (`SIMULATED` o `CACHE`) y `originSource` conserva la fuente original (`SIMULATED` en esta demostración).

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
- El contrato de puntos distingue un total presente de `0` de la ausencia de información, que en el simulador local produce `404 NOT_FOUND`. Un error técnico nunca se convierte en cero.
- La respuesta interna de detalles SRI se observó como objeto en una consulta autorizada previa y como arreglo raíz el 15 de noviembre de 2026. El adaptador acepta ambas formas y, para arreglos, selecciona el único registro cuyo `numeroRuc` coincide exactamente con el solicitado. Arreglos vacíos, sin coincidencia, con coincidencias duplicadas o con elementos/identificadores inválidos producen `502 PROVIDER_BAD_RESPONSE`, no `404`.
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
- `puntos/domain` y `puntos/application`: cédula estructural, resultado normalizado, puertos y caso de uso con fallback.
- `puntos/adapter/out/ant/simulation`: simulador local y decorator Circuit Breaker; no contiene cliente HTTP ANT.
- `puntos/adapter/out/redis`: almacenamiento JSON tipado, TTL y claves HMAC-SHA-256; el adaptador ANT oficial y el adaptador simulado usan puertos y namespaces separados.
- `puntos/adapter/in/rest`: endpoint de simulación habilitado solo en perfil local y enlazado a loopback.

## Pruebas

Ejecuta todas las pruebas y genera el JAR ejecutable:

```powershell
.\mvnw.cmd -B clean verify
```

Las pruebas de adaptadores SRI usan `MockRestServiceServer`; las de controladores usan `MockMvc` y resultados sintéticos. También se prueban la simulación local, los estados del Circuit Breaker y el fallback mediante un puerto de caché falso. La prueba de salud inicia Spring Boot en un puerto local aleatorio. Las pruebas automatizadas no llaman servicios SRI ni ANT reales.

En la ejecución reproducible del 16 de noviembre de 2026, `clean verify`
ejecutó 165 pruebas ordinarias (0 fallos, errores u omitidas) y generó el JAR.
Las dos pruebas optativas `RedisPuntosLicenciaCacheIntegrationIT` se ejecutaron
por separado contra Redis 7.4.12 real en Docker enlazado a loopback; no forman
parte del total Surefire de 165. Los [informes y logs sanitizados](../docs/pruebas/evidencias-ant-redis/ejecuciones/20261116-013654/backend/)
registran ambos resultados. La recuperación cubierta utiliza `SIMULATED` y
Redis local; no prueba el fallo de un proveedor ANT real.

## Límites actuales

- Los adaptadores salientes SRI están configurados; la consulta de contribuyentes normaliza los formatos de detalles observados (objeto y arreglo). Una forma JSON no válida o ambigua se trata como error técnico y no como inexistencia.
- Solo se mapea la clasificación confirmada `PERSONA NATURAL`. El valor SRI para personas jurídicas sigue sin verificarse.
- Consultar un vehículo no demuestra que pertenezca a un contribuyente.
- No está confirmado el significado general de la respuesta SRI `data: []` para vehículos.
- ANT no ha devuelto una respuesta funcional en el entorno de diagnóstico. La investigación no localizó documentación oficial pública de una API de consulta automática; el trámite oficial del certificado es un flujo web separado. El backend tiene un endpoint disponible solo en perfil local para devolver resultados `SIMULATED`; no hay cliente ni parser ANT.
- El caso de uso separa el puerto de caché oficial (`ANT`) del puerto exclusivo de simulación (`SIMULATED`). Los namespaces HMAC son independientes; Redis permanece desactivado por defecto. El fallback de simulación se comprueba contra Redis 7.4 real mediante la prueba optativa y Playwright. No hay adaptador real que produzca datos `ANT`, por lo que el fallback de un proveedor oficial no se ha demostrado. `antPoints` protege el simulador, no una llamada real.
- `ConsultarPuntosRequest` recibe `cedula` y valida solo 10 dígitos ASCII. No valida el dígito verificador ni la existencia de la persona y no deriva cédulas del RUC. El contrato normalizado es propio de la aplicación: `totalPuntos` entero y metadata de fuente/fecha/antigüedad; no se afirma que represente el HTML o JSON real de ANT. El requisito solicita puntos, mientras que la URL investigada `clp_grid_citaciones.jsp` identifica una vista de citaciones; no se ha confirmado que ambas consultas sean equivalentes. `ps_placa` no forma parte de la entrada actual y su función permanece desconocida.

## Diagnóstico y privacidad de logs

Los registros propios correlacionan errores con el `requestId` generado por el backend. El identificador aparece en `X-Request-Id`, en el sobre de error y en los eventos de diagnóstico SRI. Para investigar una falla, busca ese identificador y revisa la operación etiquetada, el código interno, el estado HTTP del proveedor, el tipo de excepción y, para errores internos, la primera ubicación del código de la aplicación.

El manejador global registra nombres de tipos de excepción en una cadena limitada de causas y una ubicación técnica de clase, método y línea. No entrega el objeto `Throwable` al logger: por eso los mensajes, trazas completas y mensajes de causas no se escriben en estos eventos. Los diagnósticos SRI usan etiquetas controladas y metadatos como estado, tipo de contenido, duración y tipo raíz JSON; no registran URI, parámetros, cuerpos ni mensajes de transporte.

Los logs de aplicación excluyen cuerpos de solicitudes y respuestas, RUC, cédulas, correos, placas, cookies y tokens. Las respuestas HTTP al cliente conservan mensajes genéricos y no contienen clases internas, trazas ni mensajes de excepciones. Al investigar, utiliza el `requestId` y los metadatos técnicos; no agregues logs de payloads o excepciones completas. Esta política describe los eventos que emite el código de la aplicación y no constituye una certificación de seguridad del sistema ni sustituye la revisión de la configuración del recolector de logs del entorno.
