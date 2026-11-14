# Diagnóstico técnico de endpoints SRI y ANT

## Alcance y método

Se ejecutó `scripts/diagnostico-endpoints.ps1` en PowerShell 7.6.9 el **2026-11-14 a las 12:49:37 -05:00** (America/Guayaquil). Las cuatro solicitudes fueron GET y secuenciales, con timeout de 20 segundos, seguimiento explícito de hasta cinco redirecciones y un máximo analizado de 65.536 caracteres por respuesta. No hubo redirecciones. No se enviaron cookies, credenciales ni encabezados de autenticación. Los cuerpos no se escribieron en disco y el script solo muestra su estructura; los valores de los parámetros de diagnóstico no se imprimen.

Se usaron exclusivamente los centinelas indicados en la actividad:

| Servicio | Parámetros enviados |
|---|---|
| Existencia del RUC | `numeroRuc=0000000000000` |
| Información del contribuyente | `ruc=0000000000000` |
| Información del vehículo | `numeroPlacaCampvCpn=NOEXISTE` |
| ANT | `ps_tipo_identificacion=CED`, `ps_identificacion=0000000000`, `ps_placa=` (vacío) |

Estos valores no son identificaciones válidas verificadas. Las respuestas negativas o vacías obtenidas con ellos no demuestran el comportamiento del caso exitoso.

## Actualización — casos exitosos y nueva verificación autorizada

Esta sección registra evidencia resumida de una ronda posterior con datos propios o expresamente autorizados. Los valores personales y los cuerpos completos se omitieron. La fecha y hora de esa ronda no constan en el resumen recibido, por lo que no se asigna una marca temporal inventada. No se repitieron solicitudes HTTP durante la implementación de los adaptadores.

| Servicio | Resultado HTTP y Content-Type | Estructura observada | Caso exitoso | Tiempo |
|---|---|---|---|---|
| Existencia de RUC | 200, `application/json` | Booleano JSON `true` | Verificado; el servicio confirmó existencia para el dato autorizado | Aproximadamente 216 ms |
| Información del contribuyente | 200, `application/json` | Objeto JSON con propiedades `numeroRuc`, `razonSocial`, `estadoContribuyenteRuc`, `actividadEconomicaPrincipal`, `tipoContribuyente`, `regimen`, `categoria`, `obligadoLlevarContabilidad`, `agenteRetencion`, `contribuyenteEspecial`, `informacionFechasContribuyente`, `representantesLegales`, `motivoCancelacionSuspension`, `contribuyenteFantasma` y `transaccionesInexistente` | Verificado; se registraron nombres de propiedades, no valores ni tipos de cada propiedad | No consignado |
| Información del vehículo | 200, `application/json` | Objeto JSON; campos observados en el elemento vehicular: `codigoVehiculo`, `numeroPlaca`, `numeroCamvCpn`, `descripcionMarca`, `descripcionModelo`, `anioAuto`, `descripcionPais`, `ultimoAnioPagado`, `estadoExoneracion`, `aplicaCuota` y `verMensajeElectricos`. Algunos campos opcionales fueron `null`. | Verificado; no demuestra titularidad respecto de un contribuyente | No consignado |
| ANT — consulta web | 502 en dos intentos consecutivos, `text/html` | Página HTML genérica de gateway, sin formularios, tablas ni redirecciones | No verificado; no se observó respuesta funcional | No consignado |

Los nombres de propiedades anteriores reflejan el esquema resumido de la prueba autorizada. Los tipos de las propiedades SRI no fueron incluidos en esa evidencia y no se infieren aquí. El adaptador vehicular conserva los tipos JSON escalares recibidos para los campos permitidos, en vez de asignar tipos de negocio no confirmados. La clasificación SRI se mantiene como `NO_DETERMINADA`: aunque se observó `tipoContribuyente`, sus valores y equivalencias con `NATURAL` o `JURIDICA` no fueron verificados.

### Estado de la prueba ANT con placa

El registro de la ejecución anterior indicó `ps_placa` vacío incluso después de la autorización del usuario. Por tanto, no se puede afirmar que la placa se enviara en aquella ronda. Se corrigió `scripts/validar-casos-exitosos.ps1`: ahora una respuesta afirmativa construye y envía una segunda consulta con la placa codificada; una respuesta negativa omite esa consulta y conserva solo la prueba inicial con `ps_placa` vacío. Cada solicitud ANT permite como máximo un único reintento, diez segundos después, solo ante HTTP 502, 503 o 504. Esta corrección se probó sin ejecutar solicitudes HTTP.

## Interpretación actual de las integraciones

- **Contribuyentes SRI:** la existencia se interpreta únicamente desde el booleano JSON. `false` es ausencia confirmada y evita la segunda llamada. `true` habilita la lectura de detalles. HTTP 204, un cuerpo no booleano, JSON ilegible, RUC de respuesta ausente o distinto y errores HTTP son fallos técnicos, no ausencia.
- **Vehículos SRI:** el adaptador consume el arreglo `data` del objeto de respuesta observado y conserva solo los campos permitidos listados arriba. Una respuesta con `data: []` se traduce en una lista vacía según el formato observado con el centinela; la semántica de todos los casos negativos aún no se confirmó formalmente. Consultar una placa no prueba titularidad.
- **ANT:** el 502 sigue siendo una falla de gateway/upstream. La página funcional, la extracción de puntos, la necesidad de cookies o sesión, los campos, los selectores y el cálculo de puntos continúan sin verificar. No se almacena una página de error como resultado válido.

Los adaptadores SRI usan HTTP GET y timeouts configurados de 3 segundos para conexión y 5 segundos para lectura. Los errores de red, timeout, HTTP y contenido inválido se representan con excepciones técnicas sanitizadas: no conservan causas HTTP que puedan incluir URL o cuerpos.

Resilience4j está incluido y tiene una configuración nominal `antPoints` (ventana de 5 llamadas, umbral de 50 %, 30 segundos en abierto y 2 llamadas de prueba). La configuración todavía no está conectada a un adaptador ANT. Redis, recuperación desde caché y metadata de datos obsoletos permanecen pendientes porque no existe una respuesta ANT válida que permita definir el modelo. Cuando ANT no esté disponible y aún no haya un resultado validado en caché, el futuro servicio deberá devolver un error controlado de proveedor no disponible; no se debe responder cero puntos.

## 1. SRI — verificar existencia del RUC

| Campo | Resultado |
|---|---|
| Servicio | SRI, existencia por número de RUC |
| URL | `https://srienlinea.sri.gob.ec/sri-catastro-sujeto-servicio-internet/rest/ConsolidadoContribuyente/existePorNumeroRuc` |
| Parámetros | `numeroRuc` (centinela sintético) |
| Método | GET |
| Fecha y hora | 2026-11-14 12:49:37 -05:00, ejecución secuencial |
| Código HTTP | 200 |
| Content-Type | `application/json` |
| Tiempo | 157 ms |
| Redirecciones | Ninguna |
| Estructura | JSON primitivo booleano: `false` |
| Resultado | Host y ruta respondieron; el centinela produjo un resultado negativo. No se puede distinguir con esta prueba si `false` expresa solo inexistencia o también formato no aceptado. |
| Caso exitoso | No verificado |
| Limitaciones | No se consultó un RUC de prueba válido autorizado. La respuesta no valida por sí sola el algoritmo ni las reglas de persona natural. |

El encabezado `Cache-Control` observado fue `no-store, must-revalidate, no-cache, max-age=0`. Se recibió un encabezado `Set-Cookie`; su valor no se guardó. La solicitud se completó sin enviar cookies, lo que no demuestra si una secuencia válida posterior las necesitaría.

## 2. SRI — información del contribuyente

| Campo | Resultado |
|---|---|
| Servicio | SRI, información del contribuyente |
| URL | `https://srienlinea.sri.gob.ec/sri-catastro-sujeto-servicio-internet/rest/ConsolidadoContribuyente/obtenerPorNumerosRuc` |
| Parámetros | `ruc` (centinela sintético). Se utilizó `?ruc=`, forma normalizada del `?&ruc=` de la consigna. |
| Método | GET |
| Fecha y hora | 2026-11-14 12:49:37 -05:00, ejecución secuencial |
| Código HTTP | 204 No Content |
| Content-Type | No informado |
| Tiempo | 44 ms |
| Redirecciones | Ninguna |
| Estructura | Respuesta sin cuerpo |
| Resultado | El host y la ruta respondieron con 204 para el centinela; no se devolvieron objeto, lista, mensaje ni campos que se puedan inspeccionar. |
| Caso exitoso | No verificado |
| Limitaciones | No se puede determinar si 204 significa identificación inexistente, parámetro rechazado u otra condición del servicio. Hace falta un RUC de prueba válido autorizado o documentación oficial de la semántica. |

También se observaron `Cache-Control: no-store, must-revalidate, no-cache, max-age=0` y `Set-Cookie` (valor omitido). No se enviaron cookies.

## 3. SRI — información del vehículo

| Campo | Resultado |
|---|---|
| Servicio | SRI, consulta de vehículo por placa/CAMPV/CPN |
| URL | `https://srienlinea.sri.gob.ec/sri-matriculacion-vehicular-recaudacion-servicio-internet/rest/BaseVehiculo/obtenerPorNumeroPlacaOPorNumeroCampvOPorNumeroCpn` |
| Parámetros | `numeroPlacaCampvCpn` (centinela sintético `NOEXISTE`) |
| Método | GET |
| Fecha y hora | 2026-11-14 12:49:37 -05:00, ejecución secuencial |
| Código HTTP | 200 |
| Content-Type | `application/json` |
| Tiempo | 57 ms |
| Redirecciones | Ninguna |
| Estructura | Objeto JSON con `mensajeServidor` (objeto con `texto` de tipo string), `objeto` (null) y `data` (arreglo vacío). No se conservó el valor de `texto`. |
| Resultado | La ruta respondió y entregó JSON con arreglo `data` vacío para el centinela. No se observó información de vehículo ni propietario. |
| Caso exitoso | No verificado |
| Limitaciones | Se necesita una placa, CAMPV o CPN de prueba autorizado para conocer el esquema de un resultado con datos. Una búsqueda por placa tampoco demuestra titularidad respecto de un contribuyente. |

Se observaron `Cache-Control: no-store, must-revalidate, no-cache, max-age=0` y `Set-Cookie` (valor omitido). La petición funcionó sin enviar cookies, pero eso no verifica todas las condiciones de una consulta válida.

## 4. ANT — consulta web de citaciones/puntos

| Campo | Resultado |
|---|---|
| Servicio | ANT, página web de consulta |
| URL | `https://consultaweb.ant.gob.ec/PortalWEB/paginas/clientes/clp_grid_citaciones.jsp` |
| Parámetros | `ps_tipo_identificacion=CED`, `ps_identificacion` (centinela sintético), `ps_placa` vacío |
| Método | GET |
| Fecha y hora | 2026-11-14 12:49:37 -05:00, ejecución secuencial |
| Código HTTP | 502 Bad Gateway |
| Content-Type | `text/html` |
| Tiempo | 377 ms |
| Redirecciones | Ninguna |
| Estructura | HTML de error con título `502 Bad Gateway`; 0 formularios y 0 tablas en el cuerpo recibido. |
| Resultado | Se recibió una página HTML de gateway en vez del contenido funcional del portal. Es una respuesta HTTP del servidor o gateway en la ruta de red; la prueba no permite atribuir el fallo a la aplicación ANT ni concluir que el endpoint esté roto. |
| Caso exitoso | No verificable |
| Limitaciones | No se observó la página real de consulta. No se pueden confirmar sus campos, tablas, encabezados de puntos/licencia, mensajes, CAPTCHA, autenticación, sesión o cookies. Tampoco se pudo medir el efecto de `ps_placa` vacío. |

El cuerpo observado no mostró formularios ni tablas ni un título de autenticación/CAPTCHA; como solo se recibió la página 502, esto no permite afirmar que el portal no use esos mecanismos.

## Comparación

| Servicio | Accesibilidad observada | Respuesta del centinela | Caso exitoso |
|---|---|---|---|
| SRI existencia RUC | Accesible: HTTP 200 | JSON booleano `false` | No verificado |
| SRI información contribuyente | Accesible: HTTP 204 | Sin cuerpo ni Content-Type | No verificado |
| SRI vehículo | Accesible: HTTP 200 | JSON con `data: []`, `objeto: null` y `mensajeServidor.texto` string | No verificado |
| ANT | No verificable funcionalmente: HTTP 502 HTML | Página de gateway | No verificable |

Los dos primeros servicios SRI y el de vehículo son accesibles desde este entorno con las rutas y parámetros utilizados. Las respuestas con centinelas no son casos exitosos. ANT devolvió HTTP 502, que se registra como limitación de esta prueba y no como evidencia suficiente de que el servicio esté roto.

## Análisis arquitectónico y decisiones pendientes

1. **Consumo Java del SRI.** Las tres rutas SRI aceptaron conexiones HTTPS y devolvieron respuestas HTTP sin que el diagnóstico enviara cookies o credenciales. Esto hace viable probar un cliente HTTP Java en un siguiente bloque. Aún hay que verificar el resultado con datos de prueba autorizados y definir la semántica de 204, respuestas negativas, errores HTTP y esquemas completos.
2. **Estructuras observadas.** La verificación de existencia devuelve un booleano JSON. La consulta del contribuyente produjo 204 sin cuerpo con el centinela. La consulta vehicular devuelve un objeto JSON con las propiedades observadas arriba, pero sin elementos en `data`. No se deben convertir estos resultados de prueba en modelos de dominio completos.
3. **Vehículos.** La ruta está técnicamente accesible y la respuesta JSON es analizable. La consulta exitosa y sus atributos reales siguen pendientes. La respuesta por placa no prueba que el vehículo pertenezca al contribuyente consultado.
4. **ANT y extracción.** La única respuesta HTML fue la página `502 Bad Gateway`; no era la página funcional JSP. Por eso no hay evidencia para decidir si el resultado ANT es HTML o JSON, qué selectores serían estables ni cómo extraer puntos. No se intentó evadir controles de acceso.
5. **Sesiones y cookies.** El SRI incluyó `Set-Cookie` en las tres respuestas, pero las peticiones de esta prueba no enviaron cookies y aun así obtuvieron HTTP 200/204. Esto no demuestra que una consulta exitosa o una secuencia ANT no requiera sesión. Para ANT la condición continúa desconocida.
6. **Normalización de adaptadores.** El adaptador SRI tendrá que normalizar la respuesta booleana de existencia y separar claramente ausencia de errores técnicos. Para información y vehículos tendrá que manejar 204, JSON vacío y el objeto JSON observado sin asumir campos aún no confirmados. El adaptador ANT no debe implementarse hasta observar una respuesta funcional y determinar una fuente y formato permitidos.
7. **Datos para probar éxito.** Hace falta documentación oficial con un ejemplo no personal o datos de prueba autorizados para RUC válido, persona natural y vehículo. Para ANT también hace falta una respuesta funcional accesible y confirmar el propósito de `ps_placa`. No se usaron identificaciones reales.
8. **RUC y cédula.** No se verificó una regla oficial que permita obtener una cédula a partir de los primeros diez dígitos de cualquier RUC de persona natural. El servidor no debe confiar en una cédula arbitraria del navegador ni derivarla sin una regla confirmada.
9. **Correo electrónico.** Los endpoints diagnosticados no requieren ni recibieron email. Hasta que el contrato del proveedor o los requisitos de privacidad indiquen lo contrario, el correo se queda en el flujo de la aplicación y no se envía a SRI ni ANT.
10. **Resilience4j futuro.** Conviene tratar timeouts, errores DNS/TLS/conexión, respuestas 5xx (incluido un 502) y respuestas ilegibles/incompatibles con el esquema como fallos técnicos del proveedor. Una política de reintentos acotada debe considerar que son GET y respetar `Retry-After` si aparece. Entradas inválidas, resultados negativos confirmados y ausencia de datos no deben contabilizarse automáticamente como indisponibilidad. La política definitiva dependerá de los códigos y esquemas verificados.
11. **Redis futuro.** Guardar únicamente el último resultado ANT válido y los campos mínimos confirmados que la aplicación necesite, junto con `fetchedAt` y metadatos para distinguir origen `ANT`/`CACHE` y datos antiguos. No almacenar respuestas HTML completas, cookies, errores ni resultados vacíos como último dato válido. El esquema, clave y TTL quedan pendientes porque aún no se observó una respuesta funcional de ANT.
12. **Resultados parciales.** No se comprobó todavía la semántica de agregación cuando una fuente responde y otra falla. Antes de implementarla se debe definir si cada módulo falla de forma independiente y cómo se representa un resultado parcial, sin presentar un fallback antiguo como dato actual.

## Ejecución reproducible

Desde la raíz del repositorio, en PowerShell:

```powershell
.\scripts\diagnostico-endpoints.ps1
```

El script permite ajustar el timeout (1–60 s), el máximo de redirecciones (0–10) y el tamaño de muestra analizada (1.024–262.144 caracteres). Por defecto utiliza 20 segundos, cinco redirecciones y 65.536 caracteres. Las respuestas y los valores de los parámetros no se imprimen ni se guardan.
