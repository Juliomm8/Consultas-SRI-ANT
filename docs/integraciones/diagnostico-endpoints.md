# Diagnóstico técnico de endpoints SRI y ANT

## Estado de la integración ANT

La integración de puntos de licencia sigue bloqueada por respuestas HTTP 502
Bad Gateway del portal oficial. Esta respuesta confirma un fallo de gateway en
las rutas probadas desde el entorno de diagnóstico; no permite asegurar cuál es
la causa interna del proveedor ni concluir que el servicio esté permanentemente
fuera de servicio.

Las rutas en las que se observaron respuestas 502 son:

- `https://consultaweb.ant.gob.ec/PortalWEB/`
- `https://consultaweb.ant.gob.ec/PortalWEB/paginas/clientes/clp_criterio_consulta.jsp`
- `https://consultaweb.ant.gob.ec/PortalWEB/paginas/clientes/clp_grid_puntos.jsp`
- `https://consultaweb.ant.gob.ec/PortalWEB/paginas/clientes/clp_grid_citaciones.jsp`

En las inspecciones disponibles no se obtuvieron formularios ni tablas
funcionales. Las consultas de las páginas de entrada, criterios y puntos se
probaron sin enviar identificaciones personales en el diagnóstico más reciente.
También existieron intentos anteriores de consulta directa con respuestas 502.

Las dos comprobaciones posteriores del portal principal reportadas el
**14 de noviembre de 2026** volvieron a recibir HTTP 502:

| Hora local (UTC-05:00) | Resultado |
|---|---|
| 21:16:18 | HTTP 502 Bad Gateway |
| 21:18:15 | HTTP 502 Bad Gateway |

Estas dos marcas corresponden a las ejecuciones reportadas para este proyecto;
las mediciones detalladas de tiempos y encabezados registradas más abajo
pertenecen a las ejecuciones previas indicadas allí.

### Trámite oficial y ausencia de API pública documentada

La [Guía Oficial de Trámites y Servicios de Gob.ec](https://www.gob.ec/ant/tramites/emision-certificado-informacion-conductor),
con información proporcionada por la ANT, describe la emisión del certificado
de información del conductor e indica que incluye los puntos de la licencia.
La ficha consultada indica como última actualización el 2024-09-03. Su flujo en
línea contempla identificación de la persona, correo electrónico, generación y
pago de una orden y aceptación de términos por correo. La guía describe un
trámite web para usuarios; no especifica una API pública de consulta automática
ni un contrato de integración para este proyecto. En la investigación realizada
no se localizó documentación oficial pública de esa API. Esto no permite afirmar
que no exista una interfaz privada o no publicada.

El trámite del certificado y la consulta automática que se pretendía implementar
son mecanismos distintos. El frontend solo enlaza a la guía oficial; no inicia
el trámite, no envía identificaciones y no consulta puntos.

### Comprobación HTTP sin parámetros — 15 de noviembre de 2026

Se hicieron GET puntuales, secuenciales y sin parámetros, cookies ni
identificaciones desde el entorno local. Las cuatro páginas devolvieron HTTP
502 con `text/html`:

| Ruta | HTTP | Content-Type | Tiempo |
|---|---:|---|---:|
| `/PortalWEB/` | 502 | `text/html` | 482 ms |
| `/PortalWEB/paginas/clientes/clp_criterio_consulta.jsp` | 502 | `text/html` | 363 ms |
| `/PortalWEB/paginas/clientes/clp_grid_puntos.jsp` | 502 | `text/html` | 369 ms |
| `/PortalWEB/paginas/clientes/clp_grid_citaciones.jsp` | 502 | `text/html` | 371 ms |

La respuesta acredita un error de gateway en la ruta de red probada, no su
origen interno. No se puede atribuir con esta evidencia a la aplicación ANT,
al gateway de ANT o a un intermediario de red. Tampoco se puede inferir el
formato de una respuesta funcional ni si el flujo requiere sesión. Estas
solicitudes directas no pasaron por el backend del proyecto, que todavía no
tiene una operación ANT.

### Trabajo realizado

La investigación se realizó de forma incremental:

1. Se probó directamente el endpoint indicado por la actividad.
2. Se comprobaron los códigos HTTP y la estructura de las respuestas recibidas.
3. Se crearon scripts reproducibles que resumen las respuestas sin guardar los
   cuerpos ni imprimir identificaciones.
4. Se incorporó el modo aislado `-SoloANT` para no ejecutar consultas SRI que
   no son necesarias para diagnosticar ANT.
5. Se investigaron el portal principal y las páginas relacionadas de criterios,
   puntos y citaciones.
6. El diagnóstico incorporó inspección estructural de formularios, cookies en
   memoria, redirecciones, HTML y nombres de campos ocultos, sin imprimir sus
   valores.
7. Se repitieron las comprobaciones para determinar si el error podía ser
   transitorio.
8. Las respuestas siguieron siendo HTTP 502; no apareció una página funcional
   que permitiera verificar el flujo.

El trabajo no se abandonó: estas pruebas buscaron distinguir un problema en
nuestra forma de consulta de la disponibilidad observada en el gateway externo.
El material obtenido no permite atribuir con certeza la causa interna.

### Estado técnico actual

- No hay una respuesta funcional de puntos ANT verificada.
- No se localizó una especificación oficial pública de API para consulta automática.
- El trámite oficial del certificado de información del conductor incluye puntos,
  pero es un flujo web separado y no confirma una API consumible por la aplicación.
- La finalidad del cuarto servicio en los requisitos es consultar el total de
  puntos de la licencia; la URL investigada `clp_grid_citaciones.jsp` identifica
  una vista de citaciones. No hay evidencia de que ambos conceptos o rutas sean
  equivalentes.
- No se confirmó el formulario real ni sus parámetros definitivos.
- El backend define un esquema normalizado propio para la aplicación:
  `data.totalPuntos` como entero y metadata `source`, `fetchedAt`, `stale` y
  `warning` (además del `requestId` y `retrievedAt` del sobre HTTP). Esto no es
  una reproducción del JSON ni del HTML real de ANT.
- Se implementó un simulador local con respuestas marcadas `SIMULATED`; solo se
  activa mediante el perfil `local-simulation`, que enlaza el servidor a
  `127.0.0.1`. El simulador no hace solicitudes HTTP ni produce evidencia sobre
  la respuesta de ANT.
- No se implementó un parser ni un cliente HTTP ANT real.
- El circuit breaker `antPoints` envuelve el puerto del simulador únicamente en
  ese perfil; todavía no protege una llamada real al proveedor.
- El caso de uso conecta el fallback al puerto Redis. El adaptador Redis está
  desactivado por defecto, almacena solo `totalPuntos`, `fetchedAt` y
  `source=ANT`, y rechaza resultados `SIMULATED`. El fallback se prueba con un
  puerto falso; no se ha verificado con una respuesta ANT real.
- Una respuesta 502, HTML de error o cuerpo vacío no se almacenará como resultado
  válido.
- Un total sintético de cero es un valor presente bajo `SIMULATED`; ausencia de
  información se representa separadamente. Un error técnico nunca se convierte
  en cero.

La automatización de la integración debe esperar a una respuesta funcional y a
la confirmación del flujo legítimo y de un mecanismo de integración autorizado.
Mientras tanto, el frontend enlaza al trámite oficial, informa que la consulta
directa no está integrada y no consume el simulador local.

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
| Información del contribuyente | 200, `application/json` | En una ronda autorizada previa se documentó un objeto JSON; el 15 de noviembre de 2026 el diagnóstico Java observó un arreglo JSON raíz. El modo `-SoloTipoContribuyente` extrajo el valor categórico de su registro. | Verificado en ambas rondas; los detalles dependen de la forma recibida | No consignado |
| Información del vehículo | 200, `application/json` | Objeto JSON directamente en la raíz con las 24 propiedades documentadas abajo. Algunos campos opcionales fueron `null`. | Verificado; no demuestra titularidad respecto de un contribuyente | No consignado |
| ANT — consulta web | 502 en dos intentos consecutivos, `text/html` | Página HTML genérica de gateway, sin formularios, tablas ni redirecciones | No verificado; no se observó respuesta funcional | No consignado |

### Discrepancia de la raíz JSON de detalles SRI (15 de noviembre de 2026)

La evidencia de ejecución registra `taxpayer-existence` con HTTP 200,
`application/json` y raíz booleana; `taxpayer-details` respondió HTTP 200,
`application/json`, pero con raíz de tipo arreglo. El adaptador Java de esa
versión solo aceptaba un objeto raíz, por lo que registró `expected_object`,
generó `INVALID_RESPONSE` y el manejador lo convirtió en `502
PROVIDER_BAD_RESPONSE`. La causa confirmada fue la incompatibilidad de forma
JSON en el adaptador, no un HTTP 502 del proveedor en esa ejecución.

El modo `-SoloTipoContribuyente` pudo extraer el campo porque usa
`ConvertFrom-Json -AsHashtable` sin `-NoEnumerate`: PowerShell desenvuelve un
arreglo raíz de un solo elemento y entrega ese registro como hashtable. El
script anterior y sus pruebas cubrían el objeto, no el arreglo. Se agregó un
fixture sintético para documentar y comprobar el comportamiento de un arreglo
de un registro; para arreglos con varios registros el diagnóstico no elige una
categoría arbitrariamente.

La implementación Java conserva la compatibilidad con el objeto raíz previo,
respaldado por la evidencia autorizada anterior y sus fixtures. Para un arreglo
selecciona exactamente un objeto cuyo `numeroRuc` coincida con el solicitado.
Arreglos vacíos, múltiples coincidencias, ausencia de coincidencia, elementos
no objeto y `numeroRuc` ausente o con tipo no admitido se consideran respuestas
inválidas, no ausencia confirmada del contribuyente. No se guardaron valores
personales ni cuerpos reales.

Los nombres de propiedades de contribuyente reflejan el esquema resumido de la respuesta autorizada previa. En la respuesta más reciente se confirmó que los detalles llegan bajo una raíz arreglo; las propiedades se leen del registro seleccionado, sin registrar ni conservar valores personales. Los tipos no se infieren salvo `tipoContribuyente`, observado como `string`. Una consulta autorizada confirmó el valor categórico `PERSONA NATURAL`, que se mapea a `TipoPersona.NATURAL`. No hay evidencia suficiente para mapear personas jurídicas.

La respuesta vehicular positiva observada es un objeto con los campos del vehículo directamente en la raíz. La respuesta negativa del diagnóstico inicial es otra estructura: un objeto envolvente con `mensajeServidor`, `objeto: null` y `data: []`. No se toma `data: []` como prueba universal de inexistencia; solo se registra el resultado de esa solicitud centinela. El adaptador y sus pruebas distinguen ambas formas. Las pruebas automatizadas usan JSON sintético y no vuelven a llamar a SRI.

Las 24 propiedades permitidas en `Vehiculo.CAMPOS_SRI_OBSERVADOS` son: `codigoVehiculo`, `numeroPlaca`, `numeroCamvCpn`, `colorVehiculo1`, `colorVehiculo2`, `cilindraje`, `nombreClase`, `descripcionMarca`, `descripcionModelo`, `anioAuto`, `descripcionPais`, `mensajeMotivoAuto`, `aplicaCuota`, `fechaUltimaMatricula`, `fechaCaducidadMatricula`, `fechaCompraRegistro`, `fechaRevision`, `descripcionCanton`, `descripcionServicio`, `ultimoAnioPagado`, `prohibidoEnajenar`, `observacion`, `estadoExoneracion` y `verMensajeElectricos`. El adaptador conserva los tipos JSON `string`, `number`, `boolean` y `null` mediante `ValorCampoVehiculo`, sin convertirlos a tipos de negocio no verificados. No se infiere titularidad.

El script `scripts/validar-casos-exitosos.ps1` puede mostrar el valor de `tipoContribuyente` únicamente tras una autorización S explícita y solo si supera un filtro conservador de texto categórico breve. Se permiten letras Unicode, espacios, guiones y guiones bajos hasta 64 caracteres; se rechazan números, emails, URLs, patrones de token, controles, cadenas vacías y cadenas demasiado largas. La salida se limita a ese campo; el resto del cuerpo y los valores personales se omiten. Las pruebas locales del script usan fixtures sintéticos y no hacen solicitudes HTTP. La clasificación del dominio solo reconoce la equivalencia confirmada `PERSONA NATURAL`; otros valores permanecen `NO_DETERMINADA`.

Para ejecutar solo la inspección del tipo de contribuyente:

```powershell
pwsh -NoProfile -File .\scripts\validar-casos-exitosos.ps1 -SoloTipoContribuyente
```

Este modo solicita únicamente un RUC mediante `Read-Host -AsSecureString`, consulta primero la existencia y pide detalles solo si SRI devuelve `true`. Antes de mostrar el valor categórico seguro solicita consentimiento S/N. Ante una entrada inválida, resultado negativo, error o respuesta no inspeccionable, termina sin pedir otros datos. No consulta vehículos ni ANT, y no ejecuta reintentos ANT. Las pruebas del modo usan respuestas HTTP simuladas y verifican que no se invocan esos flujos; no se realizaron consultas reales durante esta modificación. El valor `PERSONA NATURAL` ya está confirmado y mapeado; la clasificación de personas jurídicas sigue pendiente.

### Estado de la prueba ANT con placa

El registro de la ejecución anterior indicó `ps_placa` vacío incluso después de la autorización del usuario. Por tanto, no se puede afirmar que la placa se enviara en aquella ronda. Se corrigió `scripts/validar-casos-exitosos.ps1`: ahora una respuesta afirmativa construye y envía una segunda consulta con la placa codificada; una respuesta negativa omite esa consulta y conserva solo la prueba inicial con `ps_placa` vacío. Cada solicitud ANT permite como máximo un único reintento, diez segundos después, solo ante HTTP 502, 503 o 504. Esta corrección se probó sin ejecutar solicitudes HTTP.

## Nueva inspección del flujo web oficial de ANT

Se inspeccionaron secuencialmente, mediante GET y sin parámetros personales, el portal inicial, la página de criterios y la vista de puntos. La inspección de cabeceras se ejecutó el **2026-11-14 a las 21:01:25 -05:00** (PowerShell 7.6.9). Se usó una `CookieContainer` limitada al host oficial y solo en memoria; no se enviaron credenciales. La inspección estructural del cuerpo se realizó en una ejecución anterior del mismo día; su hora exacta no quedó registrada. No se guardaron cuerpos ni valores de cookies.

| Ruta | HTTP | Content-Type | Tiempo | Redirecciones | Set-Cookie / cookies en jar | WWW-Authenticate | Retry-After |
|---|---:|---|---:|---:|---|---|---|
| `/PortalWEB/` | 502 | `text/html` | 376 ms | Ninguna | No / 0 | No | No observado |
| `/PortalWEB/paginas/clientes/clp_criterio_consulta.jsp` | 502 | `text/html` | 79 ms | Ninguna | No / 0 | No | No observado |
| `/PortalWEB/paginas/clientes/clp_grid_puntos.jsp` | 502 | `text/html` | 79 ms | Ninguna | No / 0 | No | No observado |

Las páginas examinadas mostraron el título genérico `502 Bad Gateway`, sin formularios ni tablas. No se identificaron nombres de campos, campos ocultos, encabezados de tabla ni indicadores útiles en esas respuestas. No se observó una redirección, requisito de autenticación en cabecera ni cookie de sesión; al ser respuestas de gateway, esto **no demuestra** que el portal funcional prescinda de sesión, formularios, JavaScript, iframe, CAPTCHA o autenticación. Tampoco se confirmó cómo se llega a la vista de puntos ni qué parámetros requiere.

Se añadió `-SoloANT` al diagnóstico. Primero inspecciona el portal y las rutas de referencia con una cookie jar efímera; solicita una cédula mediante `Read-Host -AsSecureString` únicamente si la página de criterios entrega un único formulario GET compatible con la ruta y los campos conocidos, sin campos ocultos, query en el `action`, scripts, AJAX, iframe, CAPTCHA ni autenticación. En tal caso prueba primero `ps_placa` vacío y pregunta antes de solicitar una placa privada para una segunda variante. Los valores y las URLs completas con parámetros no se muestran ni se escriben. Si el portal inicial o el formulario no se pueden confirmar, termina sin solicitar ni enviar identificaciones. En esta ronda no se ingresaron cédulas ni placas y no se ejecutaron consultas con datos personales.

El modo conserva un único reintento diez segundos después solo para HTTP 502, 503 o 504 en cada consulta de ANT; no reintenta la navegación de descubrimiento. Ninguna respuesta de gateway se interpreta como puntos ni como resultado funcional. No se implementó un adaptador Java ni parser ANT: la forma de consulta y la respuesta de puntos todavía no tienen evidencia suficiente.

## Interpretación actual de las integraciones

- **Contribuyentes SRI:** la existencia se interpreta únicamente desde el booleano JSON. `false` es ausencia confirmada y evita la segunda llamada. `true` habilita la lectura de detalles, que puede llegar como objeto raíz (formato previo observado) o como arreglo raíz (formato observado el 15 de noviembre de 2026). En arreglos se selecciona un único objeto con `numeroRuc` coincidente. Un arreglo vacío o ambiguo, sin coincidencia, con elemento inválido, un cuerpo vacío/no JSON, un RUC de respuesta ausente o distinto y los errores HTTP son fallos técnicos, no ausencia. El valor exacto `tipoContribuyente: "PERSONA NATURAL"` se clasifica como `TipoPersona.NATURAL`; los valores desconocidos, ausentes, nulos o no textuales quedan `NO_DETERMINADA`. No se implementa equivalencia para personas jurídicas.
- **Vehículos SRI:** la respuesta positiva observada contiene el vehículo directamente en el objeto raíz. El adaptador la convierte en una lista de un elemento, como exige el puerto actual, y conserva solo los campos permitidos listados arriba. También admite el envoltorio con `data` como compatibilidad: `data: []` produce una lista vacía, pero su semántica más allá del centinela no está confirmada; el arreglo no vacío se conserva como variante no verificada. JSON mal formado, campos obligatorios ausentes (`codigoVehiculo`) o formas desconocidas son errores de respuesta. Consultar una placa no prueba titularidad.
- **ANT:** las tres rutas de navegación investigadas devuelven 502, y la consulta directa ya había devuelto 502 en dos intentos consecutivos. Esto confirma una falla de gateway/upstream desde este entorno, no que el servicio esté roto. La página funcional, la extracción de puntos, la necesidad de cookies o sesión, los campos, los selectores y el cálculo de puntos continúan sin verificar. El contrato normalizado `totalPuntos` fue definido para la aplicación con autorización académica; no se ha confirmado como campo ni como esquema del proveedor. El requisito de puntos y el nombre de ruta `clp_grid_citaciones.jsp` mantienen una discrepancia pendiente. No se almacena una página de error como resultado válido.

Los adaptadores SRI usan HTTP GET y timeouts configurados de 3 segundos para conexión y 5 segundos para lectura. Los errores de red, timeout, HTTP y contenido inválido se representan con excepciones técnicas sanitizadas: no conservan causas HTTP que puedan incluir URL o cuerpos.

Resilience4j está incluido y conserva la configuración `antPoints` (ventana de 5 llamadas, mínimo de 3, umbral de 50 %, 30 segundos en abierto y 2 llamadas de prueba). El decorator ejecuta esa protección alrededor del puerto simulado en el perfil local; no existe todavía una llamada ANT real que proteger. Los errores técnicos tipados cuentan como fallos, mientras que la ausencia de información y la validación no. El caso de uso consulta Redis tras un fallo técnico, y Redis devuelve la fuente `CACHE` con el `fetchedAt` original; el TTL sigue siendo configurable, no es una política definitiva de vigencia. Como no existe un adaptador real, no hay datos `ANT` producidos por la aplicación para respaldar ese flujo actualmente. Sin entrada cacheada, timeout e indisponibilidad se conservan como errores controlados; nunca se responde cero.

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
| Caso exitoso | No verificado con el centinela; una consulta posterior autorizada devolvió HTTP 200 y JSON booleano `true` (ver sección de casos exitosos). |
| Limitaciones | La prueba con centinela no valida el algoritmo ni las reglas de persona natural. |

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
| Caso exitoso | No verificado con el centinela; rondas autorizadas previas y del 15 de noviembre de 2026 devolvieron HTTP 200 con raíz JSON objeto y arreglo, respectivamente. La nueva observación fue un arreglo con un registro del que el modo seguro extrajo el campo categórico. |
| Limitaciones | No se puede determinar si 204 significa identificación inexistente, parámetro rechazado u otra condición del servicio. El mapeo de tipos de persona jurídica no está confirmado. |

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
| Estructura | Para el centinela: objeto con `mensajeServidor` (objeto con `texto` string), `objeto: null` y `data: []`. En una consulta autorizada posterior: objeto JSON vehicular directamente en la raíz; campos observados listados en la sección de casos exitosos. |
| Resultado | La ruta respondió con `data: []` para el centinela. Una consulta autorizada posterior sí devolvió un objeto vehicular raíz; algunos campos opcionales fueron null. |
| Caso exitoso | Verificado en la consulta autorizada posterior; los valores personales no se conservaron. |
| Limitaciones | La semántica general de `data: []` no está confirmada y no equivale por sí sola a inexistencia definitiva. Una búsqueda por placa tampoco demuestra titularidad respecto de un contribuyente. |

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

| Servicio | Accesibilidad observada | Respuestas observadas | Caso exitoso |
|---|---|---|---|
| SRI existencia RUC | Accesible: HTTP 200 | Centinela: JSON booleano `false`; prueba autorizada posterior: booleano `true` | Verificado con dato autorizado |
| SRI información contribuyente | Accesible: centinela HTTP 204; pruebas autorizadas HTTP 200 | Centinela sin cuerpo; evidencia previa con objeto y evidencia del 15-11-2026 con arreglo raíz | Verificado con dato autorizado; Java normaliza ambas formas |
| SRI vehículo | Accesible: HTTP 200 | Centinela: envoltorio con `data: []`; caso autorizado: objeto vehicular en la raíz | Verificado con dato autorizado |
| ANT | No verificable funcionalmente: HTTP 502 HTML | Página de gateway | No verificable |

Los dos primeros servicios SRI y el de vehículo son accesibles desde este entorno con las rutas y parámetros utilizados. Las respuestas con centinelas no son casos exitosos. ANT devolvió HTTP 502, que se registra como limitación de esta prueba y no como evidencia suficiente de que el servicio esté roto.

## Análisis arquitectónico y decisiones pendientes

1. **Consumo Java del SRI.** Las tres rutas SRI aceptaron conexiones HTTPS y devolvieron respuestas HTTP sin que el diagnóstico enviara cookies o credenciales. Las consultas autorizadas posteriores verificaron respuestas exitosas para existencia, información del contribuyente y vehículo. La clasificación natural está confirmada; falta confirmar la equivalencia para personas jurídicas y la semántica general de 204 y `data: []`.
2. **Estructuras observadas.** La verificación de existencia devuelve un booleano JSON. Con el centinela, los detalles de contribuyente produjeron 204 sin cuerpo y la respuesta vehicular un envoltorio con `data: []`. En consultas autorizadas, los detalles del contribuyente se observaron primero como objeto y el 15 de noviembre de 2026 como arreglo raíz; los detalles vehiculares se observaron como objeto directamente en la raíz. Java selecciona por `numeroRuc` dentro del arreglo. El valor categórico confirmado `PERSONA NATURAL` se mapea a `TipoPersona.NATURAL`; no se infieren equivalencias no confirmadas.
3. **Vehículos.** La ruta está técnicamente accesible y se verificó una respuesta exitosa con un objeto raíz. El adaptador mapea esa forma a una lista de un vehículo y conserva el soporte para el envoltorio `data`. La respuesta por placa no prueba que el vehículo pertenezca al contribuyente consultado.
4. **ANT y extracción.** La única respuesta HTML fue la página `502 Bad Gateway`; no era la página funcional JSP. Por eso no hay evidencia para decidir si el resultado ANT es HTML o JSON, qué selectores serían estables ni cómo extraer puntos. No se intentó evadir controles de acceso.
5. **Sesiones y cookies.** El SRI incluyó `Set-Cookie` en las tres respuestas, pero las peticiones de esta prueba no enviaron cookies y aun así obtuvieron HTTP 200/204. Esto no demuestra que una consulta exitosa o una secuencia ANT no requiera sesión. Para ANT la condición continúa desconocida.
6. **Normalización de adaptadores.** Los adaptadores SRI normalizan la respuesta booleana de existencia, separan ausencia de errores técnicos y manejan las formas observadas para información de contribuyentes (objeto y arreglo) y vehículos. Para arreglos de contribuyentes, solo se acepta una coincidencia única con el RUC consultado; listas vacías o ambiguas son respuestas inválidas, no ausencia. El adaptador clasifica `PERSONA NATURAL`; valores no confirmados quedan indeterminados. ANT sigue pendiente de respuesta funcional y formato permitido.
7. **Datos para completar la verificación.** Los casos autorizados confirmaron respuestas exitosas de existencia, información del contribuyente, clasificación natural y vehículo; no se guardaron valores personales. Sigue pendiente confirmar el valor que identifica a personas jurídicas. Para ANT hace falta una respuesta funcional accesible y confirmar el propósito de `ps_placa`. No se incorporaron identificaciones al repositorio.
8. **Identificación ANT.** La consulta diagnosticada utiliza `ps_tipo_identificacion=CED` y `ps_identificacion` con una cédula de 10 dígitos. El DTO propuesto ya recibe `cedula` y aplica únicamente validación estructural ASCII; no valida dígito verificador ni existencia de la persona. No se derivará una cédula de los primeros diez dígitos de un RUC. El mecanismo autorizado para capturar y usar esa identificación debe definirse antes de activar una consulta automática.
9. **Correo electrónico.** Los endpoints diagnosticados no requieren ni recibieron email. Hasta que el contrato del proveedor o los requisitos de privacidad indiquen lo contrario, el correo se queda en el flujo de la aplicación y no se envía a SRI ni ANT.
10. **Resilience4j.** El Circuit Breaker `antPoints` cuenta únicamente `PuntosLicenciaProviderException` y decora el puerto activo en el perfil de simulación. La suite ejercita los estados cerrado, abierto y half-open con recuperación. No se ha conectado una llamada real ANT ni se han configurado reintentos de proveedor; validaciones y ausencia de información no se cuentan como fallo técnico.
11. **Redis.** La infraestructura recibe `Cedula`, deriva la clave con HMAC-SHA-256 v2, conserva el `fetchedAt` original y aplica TTL configurable. El payload estricto contiene únicamente `totalPuntos`, `fetchedAt` y `source=ANT`; el adaptador rechaza fuentes simuladas, tipos incorrectos, campos adicionales y documentos corruptos. El servicio intenta el puerto de caché ante fallos técnicos. La cédula no está en el payload ni el simulador puede escribir resultados. No hay todavía una fuente ANT real con la que probar el ciclo completo. La caducidad aceptable sigue pendiente de decisión.
12. **Resultados parciales.** No se comprobó todavía la semántica de agregación cuando una fuente responde y otra falla. Antes de implementarla se debe definir si cada módulo falla de forma independiente y cómo se representa un resultado parcial, sin presentar un fallback antiguo como dato actual.

## Ejecución reproducible

Desde la raíz del repositorio, en PowerShell:

```powershell
.\scripts\diagnostico-endpoints.ps1
```

El script permite ajustar el timeout (1–60 s), el máximo de redirecciones (0–10) y el tamaño de muestra analizada (1.024–262.144 caracteres). Por defecto utiliza 20 segundos, cinco redirecciones y 65.536 caracteres. Las respuestas y los valores de los parámetros no se imprimen ni se guardan.
