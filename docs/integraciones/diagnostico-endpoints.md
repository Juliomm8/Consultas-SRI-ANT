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
| Información del vehículo | 200, `application/json` | Objeto JSON directamente en la raíz con las 24 propiedades documentadas abajo. Algunos campos opcionales fueron `null`. | Verificado; no demuestra titularidad respecto de un contribuyente | No consignado |
| ANT — consulta web | 502 en dos intentos consecutivos, `text/html` | Página HTML genérica de gateway, sin formularios, tablas ni redirecciones | No verificado; no se observó respuesta funcional | No consignado |

Los nombres de propiedades anteriores reflejan el esquema resumido de la prueba autorizada. Los tipos de las propiedades SRI no fueron incluidos en esa evidencia y no se infieren aquí, salvo `tipoContribuyente`, observado como `string`. Una consulta autorizada posterior confirmó el valor categórico `PERSONA NATURAL`, que el adaptador mapea a `TipoPersona.NATURAL`. No se registró ni conservó el RUC, nombre, razón social ni otros valores personales. No hay evidencia suficiente para mapear el valor de personas jurídicas.

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

- **Contribuyentes SRI:** la existencia se interpreta únicamente desde el booleano JSON. `false` es ausencia confirmada y evita la segunda llamada. `true` habilita la lectura de detalles. El valor exacto `tipoContribuyente: "PERSONA NATURAL"` se clasifica como `TipoPersona.NATURAL`; valores desconocidos, propiedad ausente o nula, y tipos JSON no textuales quedan `NO_DETERMINADA`. No se implementa equivalencia para personas jurídicas. HTTP 204, un cuerpo no booleano, JSON ilegible, RUC de respuesta ausente o distinto y errores HTTP son fallos técnicos, no ausencia.
- **Vehículos SRI:** la respuesta positiva observada contiene el vehículo directamente en el objeto raíz. El adaptador la convierte en una lista de un elemento, como exige el puerto actual, y conserva solo los campos permitidos listados arriba. También admite el envoltorio con `data` como compatibilidad: `data: []` produce una lista vacía, pero su semántica más allá del centinela no está confirmada; el arreglo no vacío se conserva como variante no verificada. JSON mal formado, campos obligatorios ausentes (`codigoVehiculo`) o formas desconocidas son errores de respuesta. Consultar una placa no prueba titularidad.
- **ANT:** las tres rutas de navegación investigadas devuelven 502, y la consulta directa ya había devuelto 502 en dos intentos consecutivos. Esto confirma una falla de gateway/upstream desde este entorno, no que el servicio esté roto. La página funcional, la extracción de puntos, la necesidad de cookies o sesión, los campos, los selectores y el cálculo de puntos continúan sin verificar. No se almacena una página de error como resultado válido.

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
| Caso exitoso | No verificado con el centinela; una consulta posterior autorizada devolvió HTTP 200 con un objeto JSON y los nombres de propiedades descritos arriba. |
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
| SRI información contribuyente | Accesible: centinela HTTP 204; prueba autorizada posterior HTTP 200 | Centinela sin cuerpo; caso autorizado con objeto JSON | Verificado con dato autorizado |
| SRI vehículo | Accesible: HTTP 200 | Centinela: envoltorio con `data: []`; caso autorizado: objeto vehicular en la raíz | Verificado con dato autorizado |
| ANT | No verificable funcionalmente: HTTP 502 HTML | Página de gateway | No verificable |

Los dos primeros servicios SRI y el de vehículo son accesibles desde este entorno con las rutas y parámetros utilizados. Las respuestas con centinelas no son casos exitosos. ANT devolvió HTTP 502, que se registra como limitación de esta prueba y no como evidencia suficiente de que el servicio esté roto.

## Análisis arquitectónico y decisiones pendientes

1. **Consumo Java del SRI.** Las tres rutas SRI aceptaron conexiones HTTPS y devolvieron respuestas HTTP sin que el diagnóstico enviara cookies o credenciales. Las consultas autorizadas posteriores verificaron respuestas exitosas para existencia, información del contribuyente y vehículo. La clasificación natural está confirmada; falta confirmar la equivalencia para personas jurídicas y la semántica general de 204 y `data: []`.
2. **Estructuras observadas.** La verificación de existencia devuelve un booleano JSON. Con el centinela, la consulta del contribuyente produjo 204 sin cuerpo y la vehicular produjo un envoltorio con `data: []`. En consultas autorizadas posteriores, el contribuyente devolvió un objeto JSON y el vehículo un objeto JSON directamente en la raíz. El valor categórico confirmado `PERSONA NATURAL` se mapea a `TipoPersona.NATURAL`; no se deben inferir equivalencias para valores no confirmados.
3. **Vehículos.** La ruta está técnicamente accesible y se verificó una respuesta exitosa con un objeto raíz. El adaptador mapea esa forma a una lista de un vehículo y conserva el soporte para el envoltorio `data`. La respuesta por placa no prueba que el vehículo pertenezca al contribuyente consultado.
4. **ANT y extracción.** La única respuesta HTML fue la página `502 Bad Gateway`; no era la página funcional JSP. Por eso no hay evidencia para decidir si el resultado ANT es HTML o JSON, qué selectores serían estables ni cómo extraer puntos. No se intentó evadir controles de acceso.
5. **Sesiones y cookies.** El SRI incluyó `Set-Cookie` en las tres respuestas, pero las peticiones de esta prueba no enviaron cookies y aun así obtuvieron HTTP 200/204. Esto no demuestra que una consulta exitosa o una secuencia ANT no requiera sesión. Para ANT la condición continúa desconocida.
6. **Normalización de adaptadores.** Los adaptadores SRI normalizan la respuesta booleana de existencia, separan ausencia de errores técnicos y manejan las formas observadas para información del contribuyente y vehículos. El adaptador clasifica `PERSONA NATURAL`; los valores no confirmados quedan indeterminados. El adaptador ANT no debe implementarse hasta observar una respuesta funcional y determinar una fuente y formato permitidos.
7. **Datos para completar la verificación.** Los casos autorizados confirmaron respuestas exitosas de existencia, información del contribuyente, clasificación natural y vehículo; no se guardaron valores personales. Sigue pendiente confirmar el valor que identifica a personas jurídicas. Para ANT hace falta una respuesta funcional accesible y confirmar el propósito de `ps_placa`. No se incorporaron identificaciones al repositorio.
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
