# Diagnóstico y pruebas de integración ANT

## Resultado manual del portal oficial

Prueba de cabeceras ejecutada el **2026-11-14 a las 21:01:25 -05:00** desde PowerShell 7.6.9. Se hicieron solicitudes GET secuenciales, sin parámetros personales, credenciales ni cookies externas. La cookie jar de la inspección quedó solo en memoria. La inspección estructural del cuerpo ocurrió en otra ejecución del mismo día; su hora exacta no quedó registrada. La tabla resume ambas observaciones sin atribuir la inspección del cuerpo a la hora de las cabeceras.

| Página | Estado | Content-Type | Tiempo | Redirecciones | Set-Cookie | Resultado estructural |
|---|---:|---|---:|---:|---|---|
| `https://consultaweb.ant.gob.ec/PortalWEB/` | 502 | `text/html` | 376 ms | 0 | No | Título `502 Bad Gateway`; sin formulario ni tabla |
| `https://consultaweb.ant.gob.ec/PortalWEB/paginas/clientes/clp_criterio_consulta.jsp` | 502 | `text/html` | 79 ms | 0 | No | Título `502 Bad Gateway`; sin formulario ni tabla |
| `https://consultaweb.ant.gob.ec/PortalWEB/paginas/clientes/clp_grid_puntos.jsp` | 502 | `text/html` | 79 ms | 0 | No | Título `502 Bad Gateway`; sin formulario ni tabla |

En las respuestas tampoco se observó `WWW-Authenticate`, `Retry-After` ni cookies de ANT en la jar. Como las páginas no llegaron a contenido funcional, no puede concluirse si el flujo normal requiere sesión, campos ocultos, JavaScript/AJAX, iframe, CAPTCHA o autenticación. No se determinó el método/acción del formulario, la navegación a la página de puntos, los parámetros reales ni las columnas de resultados. El 502 indica una falla de gateway/upstream desde este entorno; no demuestra que el servicio esté roto.

No se enviaron identificaciones, placas, credenciales ni consultas personales. No se conservaron cuerpos HTML ni valores de cookies.

## Diagnóstico local PowerShell

Comandos usados, sin ejecutar una consulta real:

```powershell
pwsh -NoProfile -File .\scripts\tests\validar-casos-exitosos.tests.ps1
```

```powershell
$tokens = $null
$errors = $null
[System.Management.Automation.Language.Parser]::ParseFile(
  (Resolve-Path .\scripts\validar-casos-exitosos.ps1),
  [ref]$tokens,
  [ref]$errors
) | Out-Null
if ($errors.Count -gt 0) { throw 'PowerShell syntax error' }
```

La suite de diagnóstico usa fixtures sintéticos y reemplaza la capa HTTP por respuestas en memoria. Cubre respuestas HTML/JSON y HTML mal formado, formularios/campos ocultos, tabla y encabezados, CAPTCHA, indicadores AJAX/iframe, clasificación HTTP, timeout, redirecciones, privacidad de identificadores, consentimiento S/N de placa y reintentos limitados a una única segunda llamada tras 10 segundos para HTTP 502, 503 o 504. También verifica que un portal con error o un formulario con requisitos desconocidos no solicite ni envíe identificaciones. No realiza solicitudes de red.

`-SoloANT` inspecciona las rutas oficiales y comparte una cookie jar efímera solo dentro de `consultaweb.ant.gob.ec`. Solo solicita una cédula por `Read-Host -AsSecureString` si una respuesta posterior entrega un único formulario GET simple que coincide exactamente con la ruta y campos observados, sin campos ocultos, query en el `action`, scripts, AJAX, iframe ni indicios de CAPTCHA o autenticación. En otro caso termina sin solicitar ni enviar datos personales. Si el formulario se valida, envía primero `ps_placa` vacío y solicita autorización antes de preguntar por una placa para la variante opcional.

## Verificación backend

La verificación final se ejecutó desde `backend/`:

```powershell
.\mvnw.cmd -B clean verify
```

Resultado final: el **2026-11-14 a las 21:12:57 -05:00**, `BUILD SUCCESS`; 81 pruebas Java aprobadas, sin fallos, errores ni omisiones; el JAR `consultas-sri-ant-backend-0.0.1-SNAPSHOT.jar` se generó. La suite final de PowerShell también pasó y los dos archivos PowerShell superaron análisis sintáctico en PowerShell 7.6.9.

En la revisión QA del **2026-11-15 a las 01:21:27 -05:00** se volvió a ejecutar
desde `backend/` el comando `.\mvnw.cmd -B verify` (sin la fase `clean`): `BUILD SUCCESS`,
103 pruebas Java aprobadas, 0 fallos, 0 errores y 0 omitidas; el JAR se generó.
Esta ejecución fue automatizada y local: no realizó consultas reales a ANT ni
al SRI. El resultado de 81 pruebas anterior se conserva como registro de su
fecha, no como el conteo vigente.

Después de activar la prueba HTTP que no tenía `@Test`, el **2026-11-15 a las
01:31:28 -05:00** se ejecutaron `-Dtest=SriResponseSupportTests test` (10 pruebas
aprobadas) y `.\mvnw.cmd -B clean verify`: `BUILD SUCCESS`,
104 pruebas Java aprobadas, 0 fallos, 0 errores y 0 omitidas; el JAR se generó.
Estas pruebas fueron locales y sintéticas; no se consultaron ANT ni SRI.

En la auditoría integral del **2026-11-15**, se volvió a ejecutar desde
`backend/` `.\mvnw.cmd -B clean verify`: `BUILD SUCCESS`, 109 pruebas Java
aprobadas, 0 fallos, 0 errores y 0 omitidas. Fue una verificación local; no se
realizaron consultas reales a ANT ni al SRI.

Durante la preparación de la infraestructura de caché, el **2026-11-15** se
ejecutó nuevamente desde `backend/`:

```powershell
.\mvnw.cmd -B clean verify
```

Resultado: `BUILD SUCCESS`, 125 pruebas Java aprobadas, 0 fallos, 0 errores y 0 omitidas; Maven
generó el JAR. Las pruebas Redis usan mocks y fixtures JSON sintéticos. No se
conectó una instancia Redis real ni se realizaron consultas a ANT o SRI. Redis
está desactivado por defecto; la integración ANT, el circuit breaker conectado
y el fallback continúan pendientes.

Como comprobación separada y optativa, el **2026-11-15 a las 03:43:46 -05:00**
pasó 1 prueba `RedisPuntosLicenciaCacheIntegrationIT` contra Redis 7.4 en un
contenedor efímero local enlazado a loopback. Verificó escritura, lectura,
fecha original, origen `CACHE` y TTL; la clave de prueba se eliminó y el
contenedor se detuvo. Esta comprobación no consulta ni implementa ANT.

Después de alinear el contrato con cédula, el **2026-11-15 a las 03:57:23
-05:00** `.\mvnw.cmd -B clean verify` volvió a pasar: 139 pruebas, 0 fallos,
0 errores y 0 omitidas; JAR generado. La suite fue sintética y no consultó ANT
ni SRI. A las **03:57:42 -05:00** pasó la prueba optativa Redis contra un
contenedor local Redis 7.4; comprobó que la cédula no aparece ni en la clave ni
en el payload almacenado. El contenedor se detuvo y no se ejecutó una consulta
ANT.

## Estado actual del desarrollo

El contrato normalizado de la aplicación contiene solo `data.totalPuntos` como
entero y metadata `source`, `fetchedAt`, `stale` y `warning` (junto a
`requestId` y `retrievedAt`). No reproduce una respuesta ANT: el servicio real
sigue sin una respuesta funcional verificada. El requisito es consultar puntos
de licencia, mientras que la ruta investigada `clp_grid_citaciones.jsp` menciona
citaciones; no se ha confirmado que sean equivalentes. `ps_tipo_identificacion`
se prevé fijo en `CED`; la función de `ps_placa` sigue desconocida y no forma
parte de la entrada normalizada.

El backend implementa un modo de simulación limitado al perfil
`local-simulation`, enlazado a `127.0.0.1`. Sus escenarios cubren éxito, cero,
sin información, timeout, indisponibilidad y recuperación. Toda respuesta
simulada usa `source=SIMULATED`; no hace llamadas HTTP ni se persiste en Redis.
El endpoint no está registrado en el perfil por defecto y el frontend no lo
consume.

El caso de uso consulta el puerto de proveedor, distingue `totalPuntos=0` de
`Optional.empty()`, guarda resultados solamente cuando la fuente es `ANT` y,
ante fallos técnicos, intenta recuperar el último resultado válido del puerto
Redis. Un fallback usa `source=CACHE`, mantiene `fetchedAt`, establece
`stale=true` y añade `warning`. Si falla el proveedor y la caché no tiene una
entrada válida, se conserva el error técnico; nunca se devuelve cero como
reemplazo del error.

Resilience4j `antPoints` envuelve el límite de consulta simulado solo bajo el
perfil local y registra fallos técnicos tipados. La suite prueba cerrado,
abierto y recuperación desde half-open, además de que ausencia de datos y
errores de validación no cuentan como caídas. Esto no demuestra protección de
una llamada real a ANT. Redis mantiene HMAC-SHA-256 v2, TTL configurable y un
payload JSON estricto de `totalPuntos`, `fetchedAt` y `source=ANT`; la cédula no
aparece en el payload. La suite normal no llama ANT ni necesita Redis real; la
prueba Docker optativa histórica se describe arriba.

## Pendientes para verificar una respuesta funcional

- Repetir la navegación del portal cuando sus páginas respondan sin error de gateway.
- Observar el formulario, método, acción, nombres de campos y cualquier campo oculto o requisito de sesión.
- Confirmar si se llega a `clp_grid_puntos.jsp`, su mecanismo de navegación y sus parámetros.
- Obtener una respuesta funcional autorizada y documentar solamente el esquema, tipos y encabezados, sin valores personales.
- Confirmar el mecanismo autorizado para capturar y usar directamente la cédula requerida por ANT; no derivarla del RUC.
- Obtener primero una respuesta funcional y autorizada para confirmar cómo normalizar el total de puntos, el flujo legítimo, la discrepancia de la ruta de citaciones y el uso opcional de `ps_placa`; después conectar un adaptador real al puerto ya definido y acordar la edad máxima aceptable y la política del TTL.
- El DTO propuesto de puntos ya recibe una cédula estructural de 10 dígitos, acorde con `ps_tipo_identificacion=CED`. No se valida el dígito verificador ni se deriva desde el RUC. Sigue pendiente confirmar un mecanismo autorizado para obtener y usar esa identificación en una consulta automática.

## Reejecución para publicación

El **15 de noviembre de 2026**, las verificaciones se repitieron sin llamadas a proveedores:

- Backend, a las **12:19:12 -05:00**: `mvnw.cmd -B clean verify` desde `backend/`, `BUILD SUCCESS`, **159 pruebas**, 0 fallos, 0 errores y 0 omitidas; JAR generado.
- Frontend: `npm test`, **20 aprobadas**; `npm run lint` y `npm run build` pasaron.
- E2E: `npm run test:e2e`, **2 pruebas Playwright aprobadas**. La primera prueba incluye los recorridos exitosos de contribuyente y vehículo; la segunda cubre el HTTP 503 del proveedor simulado.
- PowerShell: `scripts/tests/validar-casos-exitosos.tests.ps1`, aprobado con fixtures locales y sin solicitudes de red.
- Redis, a las **12:21:09 -05:00**: `RedisPuntosLicenciaCacheIntegrationIT`, **1 prueba aprobada** contra Redis 7.4 efímero enlazado a loopback; no se montó volumen y el contenedor se eliminó.

Los procesos locales de E2E terminaron. Estas pruebas no consultaron SRI ni ANT reales y no validan Redis Cloud.

## Verificación de la implementación normalizada local

El **2026-11-15 a las 11:52:01 -05:00**, `backend/` pasó `.\mvnw.cmd -B clean verify`: **159 pruebas**, 0 fallos, 0 errores y 0 omitidas; `BUILD SUCCESS` y JAR generado. Las pruebas de caso de uso cubren resultado positivo, cero, ausencia de información, fallos técnicos, fallback y metadatos con puertos/fixtures sintéticos. Esto valida el comportamiento local, no una respuesta real de ANT.

En la misma ronda, el frontend pasó **20 pruebas unitarias**, `npm run build` y `npm run lint`. Playwright reportó **2 pruebas E2E aprobadas**: `consulta contribuyente y vehículo recorre React, Vite, Spring Boot y el stub SRI` ejecuta ambos recorridos SRI dentro de una prueba; `traduce un HTTP 503 del proveedor a un error seguro en la interfaz` verifica el error. Así se explican los tres recorridos descritos en el informe y los dos casos registrados por Playwright, sin alterar el conteo.

La prueba optativa `RedisPuntosLicenciaCacheIntegrationIT` pasó **1 prueba** contra Redis 7.4 en Docker, enlazado a loopback; comprobó persistencia JSON tipada, `fetchedAt`, origen `CACHE` y TTL. El contenedor temporal fue detenido y eliminado. Ninguna de estas verificaciones hizo solicitudes a ANT o SRI.
