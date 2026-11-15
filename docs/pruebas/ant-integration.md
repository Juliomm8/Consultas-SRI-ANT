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

Las pruebas de backend existentes no llaman ANT. No hay todavía dominio, puertos, servicio, adaptador ni pruebas Java ANT; no se implementa parser ni se conectó el circuit breaker a una llamada real. La configuración nominal `resilience4j.circuitbreaker.instances.antPoints` permanece instalada y sin conexión. Redis y el fallback siguen pendientes.

## Pendientes para verificar una respuesta funcional

- Repetir la navegación del portal cuando sus páginas respondan sin error de gateway.
- Observar el formulario, método, acción, nombres de campos y cualquier campo oculto o requisito de sesión.
- Confirmar si se llega a `clp_grid_puntos.jsp`, su mecanismo de navegación y sus parámetros.
- Obtener una respuesta funcional autorizada y documentar solamente el esquema, tipos y encabezados, sin valores personales.
- Determinar una regla autorizada para relacionar el contrato de entrada RUC con la identificación que requiera ANT; no derivar una cédula automáticamente.
- Solo entonces definir el modelo y el adaptador Java, conectar Resilience4j y diseñar criterios de datos válidos para una futura caché Redis.
