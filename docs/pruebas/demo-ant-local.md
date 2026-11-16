# Demostración local de puntos de licencia

## Objetivo y alcance

Esta demostración permite recorrer la interfaz React y el endpoint de puntos que existe solamente para el perfil Spring `local-simulation`. Su propósito es presentar estados de interfaz y resiliencia en un entorno local reproducible. Los resultados son sintéticos; no son datos oficiales y no equivalen a una consulta real a la ANT.

La vista es independiente del flujo normal del portal. Solo aparece cuando Vite está en desarrollo y se activa expresamente `VITE_ANT_DEMO=true`. La ruta de puntos del backend requiere además el perfil `local-simulation`; fuera de ese perfil el controlador no se registra. El simulador y el servidor quedan en loopback (`127.0.0.1`). La vista envía el contrato existente `{"cedula":"##########"}` mediante `POST /api/v1/licencias/puntos/consultas`, con `credentials: omit` y caché HTTP desactivada.

El único valor permitido por la vista es el fixture fijo `0000000000`, de 10 dígitos ASCII. La pantalla limpia el formulario al enviar y no usa almacenamiento persistente, URL, cookies ni logs de consola para conservarlo. No ingreses datos personales en esta demo.

## Arquitectura

```text
React (vista ANT opt-in)
  → Vite dev proxy (HTTP local, loopback)
  → Spring Boot (perfil local-simulation, loopback)
  → PuntosLicenciaRestController
  → ConsultarPuntosLicenciaUseCase / ResilientPuntosLicenciaConsultaAdapter
  → SimulatedAntPuntosLicenciaAdapter (sin llamadas de red)
  → PuntosLicenciaSimulationCachePort (opcional, Redis real local)
```

El runner Playwright dedicado activa también el guard de perfil `e2e`; configura los destinos SRI en un stub local y comprueba que no reciba solicitudes. El escenario ANT se resuelve dentro del backend por el adaptador simulado. No se llama al portal ANT ni a los servicios SRI.

## Ejecución manual

En una terminal PowerShell, inicia el backend:

```powershell
Set-Location .\backend
$env:SPRING_PROFILES_ACTIVE = 'local-simulation'
$env:ANT_SIMULATION_SCENARIO = 'SUCCESS'
.\mvnw.cmd spring-boot:run
```

En otra terminal, inicia Vite enlazado a loopback y activa la vista explícitamente:

```powershell
Set-Location .\frontend
$env:VITE_ANT_DEMO = 'true'
$env:VITE_API_PROXY_TARGET = 'http://127.0.0.1:8080'
npm run dev -- --host 127.0.0.1 --port 5173 --strictPort
```

Abre `http://localhost:5173` y selecciona **Simular consulta ANT** en la tarjeta ANT o **Demostración ANT** en la navegación. La pantalla abre el componente interno `AntPointsDemo`; no tiene una ruta URL independiente. Para regresar, utiliza **Volver al inicio**. Vite lee `VITE_ANT_DEMO` al arrancar: si la variable se establece o cambia después de iniciar el servidor, detén Vite con `Ctrl+C` y vuelve a ejecutar `npm run dev`. Para desactivar la vista, elimina/cambia la variable y reinícialo. Detén el backend local cuando termines. No expongas el servidor de desarrollo a una interfaz pública.

El backend acepta estos escenarios locales, controlados desde el proceso al iniciar:

| `ANT_SIMULATION_SCENARIO` | Comportamiento de la demo |
|---|---|
| `SUCCESS` | Resultado sintético positivo (por defecto, 20 puntos). |
| `ZERO` | Resultado sintético válido con cero puntos. |
| `NO_DATA` | HTTP 404, sin saldo de puntos. |
| `UNAVAILABLE` | HTTP 503 controlado. |
| `TIMEOUT` | HTTP 504 controlado. |
| `INVALID_RESPONSE` | HTTP 502 controlado. |
| `RECOVERY` | Primer intento indisponible y siguiente intento exitoso, sujeto al Circuit Breaker local. |
| `SUCCESS_THEN_TIMEOUT` | Primera invocación exitosa; las siguientes fallan inmediatamente con timeout, sin espera real. Con Redis simulado habilitado, la segunda consulta recupera el primer resultado. |

Reinicia el backend al cambiar el escenario. No hay selector de escenarios en la interfaz ni un parámetro de escenario expuesto por HTTP.

## Estados y significado

- **Éxito:** muestra `totalPuntos`, `source=SIMULATED`, `fetchedAt` y un aviso no oficial.
- **Cero:** cero es un resultado sintético válido del escenario `ZERO`; no significa “sin información”. No representa el saldo de una persona.
- **Sin información:** el escenario `NO_DATA` no devuelve un número de puntos. La interfaz explica que no equivale a cero.
- **Indisponibilidad, timeout y respuesta inválida:** sin entrada válida de caché se muestran como errores controlados con `requestId`; no generan un saldo.
- **HTTP 502:** es una falla técnica y no un saldo de cero. La integración real ANT aún no ha proporcionado una respuesta exitosa verificable.

La vista acepta únicamente dos combinaciones: resultado fresco `SIMULATED` con `originSource=SIMULATED` y `stale=false`; o resultado `CACHE` con `originSource=SIMULATED` y `stale=true`. Rechaza `ANT`, una caché de origen ANT y metadata incoherente. En una recuperación muestra la fecha original, una advertencia de posible desactualización y que el origen sigue siendo SIMULATED, no oficial.

### ANT, CACHE y SIMULATED

| Fuente | Significado en la arquitectura | Uso en la demo |
|---|---|---|
| `ANT` | Resultado validado de una futura integración real. | No disponible; no hay adaptador real implementado. |
| `CACHE` | Resultado recuperado del almacenamiento, con `fetchedAt` original y estado potencialmente desactualizado. `originSource` conserva si se guardó desde ANT o SIMULATED. | La demo acepta solo `CACHE` con `originSource=SIMULATED`; lo muestra como no oficial. |
| `SIMULATED` | Fixture determinista del adaptador local. | Se muestra como no oficial y puede guardarse solo en el namespace local de simulación. |

Redis está desactivado por defecto. La caché oficial conserva `consultas-sri-ant:ant-points:v2:` y solo acepta ANT. En `local-simulation`, otra configuración usa `consultas-sri-ant:ant-points:simulation:v1:` y solo admite SIMULATED. Ambas derivan claves HMAC-SHA-256 y almacenan únicamente total, fecha original y fuente; no almacenan cédulas en clave ni payload. Ninguna de las dos rutas puede leer o escribir el namespace de la otra. Esta prueba local no significa que Redis Cloud esté conectado. El Circuit Breaker protege el adaptador simulado; no protege una llamada real a ANT.

### Recorrido de recuperación con Redis 7.4 local

El escenario `SUCCESS_THEN_TIMEOUT` permite comprobar la recuperación de extremo
a extremo. Inicia un Redis 7.4 local enlazado únicamente a loopback. Si se usa el
puerto predeterminado, verifica primero que 6379 esté libre; si no, selecciona
otro puerto y úsalo también en `REDIS_PORT`.

```powershell
docker run --rm --detach --name consultas-sri-ant-redis-demo -p 127.0.0.1:6379:6379 redis:7.4-alpine
```

En la terminal del backend configura el perfil y una clave efímera solo en
memoria, habilita caché y arranca Spring Boot:

```powershell
Set-Location .\backend
$env:SPRING_PROFILES_ACTIVE = 'local-simulation'
$env:ANT_SIMULATION_SCENARIO = 'SUCCESS_THEN_TIMEOUT'
$env:REDIS_CACHE_ENABLED = 'true'
$env:REDIS_HOST = '127.0.0.1'
$env:REDIS_PORT = '6379'
$env:REDIS_CACHE_TTL = '5m'
$env:REDIS_CACHE_HMAC_SECRET = [Convert]::ToHexString([Security.Cryptography.RandomNumberGenerator]::GetBytes(32))
.\mvnw.cmd spring-boot:run
```

En otra terminal arranca Vite con la demo explícita y loopback:

```powershell
Set-Location .\frontend
$env:VITE_ANT_DEMO = 'true'
$env:VITE_API_PROXY_TARGET = 'http://127.0.0.1:8080'
npm run dev -- --host 127.0.0.1
```

En la demo envía el fixture sintético `0000000000` dos veces: la primera
respuesta es `SIMULATED` y escribe solo en el namespace de simulación; la
segunda representa el timeout y responde desde Redis con `source=CACHE`,
`originSource=SIMULATED`, `stale=true`, el mismo total y el mismo `fetchedAt`.
No se realizan llamadas a ANT o SRI. Termina Vite y Spring Boot con Ctrl+C,
detén el contenedor creado para esta prueba con
`docker stop consultas-sri-ant-redis-demo`, y elimina las variables del proceso
PowerShell con `Remove-Item Env:REDIS_CACHE_HMAC_SECRET, Env:REDIS_CACHE_ENABLED,
Env:REDIS_HOST, Env:REDIS_PORT, Env:REDIS_CACHE_TTL, Env:SPRING_PROFILES_ACTIVE,
Env:ANT_SIMULATION_SCENARIO` (y `Env:VITE_ANT_DEMO`, `Env:VITE_API_PROXY_TARGET`
en la terminal de Vite). El secreto nunca se escribe a disco.

## Verificaciones automatizadas

Ejecuta las suites desde `frontend/`:

```powershell
npm test
npm run lint
npm run build
npm run test:e2e
npm run test:e2e:ant
npm run test:e2e:ant:redis
```

`npm run test:e2e` conserva las pruebas E2E SRI existentes contra un stub local. `npm run test:e2e:ant` construye el backend, inicia Spring Boot con `e2e,local-simulation`, enlaza Vite y Spring a puertos loopback disponibles, configura un stub SRI local como guardia y ejecuta Playwright contra el simulador sin Redis. `npm run test:e2e:ant:redis` inicia un contenedor Redis `7.4-alpine` efímero, verifica su versión, activa la caché de simulación con un secreto aleatorio en memoria y ejecuta una prueba independiente de éxito seguido por recuperación tras timeout. Los runners detienen los procesos y el contenedor que ellos mismos iniciaron. Ninguna suite accede a ANT o SRI reales.

Vitest cubre la advertencia permanente, resultado positivo, cero, ausencia de información, indisponibilidad, timeout, respuesta inválida, request ID, rechazo de fuentes/procedencias incoherentes, validación y ausencia de almacenamiento persistente. Playwright sin Redis comprueba el recorrido local del simulador; Playwright con Redis real comprueba que el primer resultado se almacena y que la segunda invocación regresa como caché con origen simulado, fecha original, aviso stale, sin llamadas externas ni identificaciones en URL, almacenamiento o consola.

La prueba JUnit optativa `RedisPuntosLicenciaCacheIntegrationIT` también
ejecuta el caso de uso contra Redis real. Para correrla manualmente, inicia un
Redis 7.4 en loopback y ejecuta desde `backend/`:

```powershell
.\mvnw.cmd -B "-Dtest=RedisPuntosLicenciaCacheIntegrationIT" "-Dredis.integration.enabled=true" "-Dredis.integration.host=127.0.0.1" "-Dredis.integration.port=6379" test
```

Usa únicamente el fixture `0000000000`; verifica escritura física, lectura,
TTL, payload mínimo y aislamiento de namespaces. El test elimina sus dos claves
derivadas al terminar.

## Evidencias académicas

El runner de demo sin Redis genera una captura auténtica del resultado sintético exitoso en:

[`09-ant-simulado.png`](evidencias-ant-redis/ejecuciones/20261116-013654/frontend/e2e-ant/09-ant-simulado.png)

| Escenario | Resultado esperado | Prueba | Evidencia |
|---|---|---|---|
| `SUCCESS` | HTTP 200, total sintético, `SIMULATED` y aviso no oficial. | Vitest, controlador Spring y Playwright local. | Captura Playwright del resultado sintético. |
| `ZERO` | HTTP 200 y `totalPuntos=0`, mostrado como saldo de prueba válido. | Pruebas del adaptador/controlador y Vitest. | Resultado automatizado; no representa una consulta real. |
| `NO_DATA` | HTTP 404, sin convertir la ausencia a cero. | Pruebas del controlador y Vitest. | Resultado automatizado. |
| `UNAVAILABLE` | HTTP 503 y `requestId`, sin saldo. | Pruebas del controlador y Vitest. | Resultado automatizado. |
| `TIMEOUT` | HTTP 504 y mensaje de timeout, sin saldo. | Pruebas del controlador y Vitest. | Resultado automatizado. |
| `INVALID_RESPONSE` | HTTP 502 controlado, sin cuerpo interno. | Pruebas del controlador y Vitest. | Resultado automatizado. |
| Feature desactivada | Sin bandera, no aparece el acceso a la demo; build de producción no la habilita. | Prueba de la compuerta y build normal. | Resultado automatizado. |

Las pruebas unitarias que usan mocks o fixtures verifican el comportamiento de código, no la disponibilidad de proveedores. La captura documenta solo una sesión local con datos sintéticos; no prueba conectividad ni resultados de la ANT.

El runner optativo con Redis 7.4 genera capturas auténticas del flujo local de
persistencia simulada y recuperación:

| Escenario | Resultado esperado | Prueba | Evidencia |
|---|---|---|---|
| Primera invocación `SUCCESS_THEN_TIMEOUT` | `SIMULATED`, `originSource=SIMULATED`, stale falso; escritura en namespace simulado. | Playwright → Vite → Spring → caso de uso → Redis 7.4. | [Captura de éxito simulado](evidencias-ant-redis/ejecuciones/20261116-013654/frontend/e2e-ant-redis/ant-redis-simulated-success.png) |
| Segunda invocación | `CACHE`, `originSource=SIMULATED`, `stale=true`, total y `fetchedAt` originales. | Mismo recorrido, con timeout inmediato del simulador. | [Captura de recuperación CACHE](evidencias-ant-redis/ejecuciones/20261116-013654/frontend/e2e-ant-redis/ant-redis-cache-recovery.png) |

Estas capturas prueban solo la simulación y Redis local, no conectividad ni
resultados oficiales de ANT.

## Última ejecución verificada

### Ejecución histórica — 15 de noviembre de 2026

El **15 de noviembre de 2026** se ejecutaron las verificaciones de esta entrega, sin llamadas a SRI o ANT reales:

- Backend: `..\mvnw.cmd -B clean verify`, **159 pruebas aprobadas**, 0 fallos, 0 errores y JAR generado.
- Frontend: `npm test`, **30 pruebas aprobadas**; `npm run lint` pasó.
- Producción: `npm run build` pasó con `VITE_ANT_DEMO=true`. El escaneo de esa ejecución comprobó un conjunto de marcadores y no debe interpretarse como una inspección exhaustiva de todas las cadenas estáticas del bundle. La bandera de acceso depende del modo de desarrollo, por lo que el botón y la vista no se muestran en producción.
- E2E SRI existente: `npm run test:e2e`, **2 pruebas aprobadas**.
- Demo local: `npm run test:e2e:ant`, **1 prueba Playwright aprobada**. El stub de guardia SRI recibió cero solicitudes y el runner no configuró ningún proveedor externo.

El comando Playwright ANT genera la captura sintética enlazada arriba. Los procesos de backend y Vite creados por los runners se detienen al terminar.

### Ejecución de recuperación con Redis 7.4 — 15 de noviembre de 2026

Se volvió a ejecutar la verificación después de aislar el namespace simulado y
conectar el escenario `SUCCESS_THEN_TIMEOUT` a Redis local. No hubo consultas a
SRI ni ANT reales.

- Backend: `..\mvnw.cmd -B clean verify`, **165 pruebas aprobadas**, 0 fallos y
  0 errores; se generó el JAR.
- Redis real opcional: `RedisPuntosLicenciaCacheIntegrationIT`, **2 pruebas
  aprobadas** contra Redis 7.4 local.
- Frontend: `npm test`, **33 pruebas aprobadas**; `npm run lint` y
  `npm run build` pasaron. La interfaz de demostración no es accesible desde
  el build normal; una revisión adicional detectó una etiqueta condicional
  retenida en el bundle, según se explica más abajo.
- E2E SRI: `npm run test:e2e`, **3 pruebas aprobadas** usando stubs locales.
- E2E ANT aislado: `npm run test:e2e:ant`, **7 pruebas aprobadas**.
- E2E ANT con Redis: `npm run test:e2e:ant:redis`, **1 prueba aprobada** con un
  contenedor Redis 7.4 efímero.
- Las dos invocaciones del E2E Redis confirmaron `SIMULATED` seguido de
  `CACHE`, conservando el total y `fetchedAt`; no hubo llamadas externas ni
  persistencia del identificador sintético.

Los runners detuvieron sus propios procesos y contenedores. Las capturas de
éxito y recuperación están enlazadas en la tabla de evidencias anterior.

### Cierre de evidencias reproducibles — 15 de noviembre de 2026

La ejecución `20261115-204859` conservó logs y reporters inspeccionados para
descartar cédulas y credenciales antes de publicarlos como evidencia. El JUnit
optativo se conectó a Redis 7.4.12 real en Docker por loopback; el timeout del
segundo recorrido lo produjo el simulador, no ANT.
Las copias de los reportes excluyen rutas de usuario y propiedades del entorno
local; los resultados y nombres de casos se conservan.

| Comprobación | Resultado de esta ejecución | Evidencia |
|---|---|---|
| `RedisPuntosLicenciaCacheIntegrationIT` | 2 aprobadas, 0 fallos/errores/omitidas, Redis 7.4.12; el contenedor fue detenido y eliminado. | [XML JUnit](evidencias-ant-redis/junit/20261115-204859/TEST-ec.edu.udla.consultas.puntos.adapter.out.redis.RedisPuntosLicenciaCacheIntegrationIT.xml), [log sanitizado](evidencias-ant-redis/ejecuciones/20261115-204859/redis-junit-integration.txt), [ciclo de vida](evidencias-ant-redis/redis/20261115-204859/redis-container-lifecycle.txt). |
| `npm test` | 33 aprobadas. | [Log sanitizado](evidencias-ant-redis/frontend/20261115-204859/npm-test.txt). |
| `npm run test` con reporter JUnit | 33 aprobadas, 0 fallos. | [XML Vitest](evidencias-ant-redis/frontend/20261115-204859/vitest-results.xml), [log sanitizado](evidencias-ant-redis/frontend/20261115-204859/npm-run-test-junit.txt). |
| `npm run lint` y `npm run build` | Ambos aprobados; los marcadores revisados de la demo no aparecen en el bundle normal. | [Log lint sanitizado](evidencias-ant-redis/frontend/20261115-204859/npm-run-lint.txt), [log build sanitizado](evidencias-ant-redis/frontend/20261115-204859/npm-run-build.txt), [escaneo del bundle](evidencias-ant-redis/frontend/20261115-204859/production-demo-exclusion.txt). |
| `npm run test:e2e` | 3 aprobadas contra stubs locales. | [Reporte Playwright JSON](evidencias-ant-redis/frontend/20261115-204859/playwright-e2e-results.json), [log sanitizado](evidencias-ant-redis/frontend/20261115-204859/npm-run-test-e2e.txt). |
| `npm run test:e2e:ant` | 7 aprobadas con el simulador local, sin Redis; las capturas nuevas no sobrescriben las anteriores. | [Reporte Playwright JSON](evidencias-ant-redis/frontend/20261115-204859/playwright-ant-results.json), [log sanitizado](evidencias-ant-redis/frontend/20261115-204859/npm-run-test-e2e-ant.txt). |

Las capturas históricas de la ejecución `20261115-204859` se conservaron localmente.
Para evitar publicar PNG idénticos, las referencias actuales apuntan a las copias
canónicas de la ejecución del 16 de noviembre. La captura `CACHE` de interfaz es
un fixture; las capturas de éxito y recuperación de Redis son de un recorrido
distinto con infraestructura Redis real.

El runner `npm run test:e2e:ant:redis` no se volvió a ejecutar en este cierre;
las dos capturas Redis preexistentes se conservaron sin cambios. El índice de
archivos y la explicación de mocks, fixtures y límites están en el [README de
evidencias ANT + Redis](evidencias-ant-redis/README.md). Los reportes de esta
ejecución no contienen identificaciones, credenciales ni claves HMAC.

### Ejecución reproducible — 16 de noviembre de 2026

Se repitieron las suites solicitadas y se guardaron logs sanitizados, reportes y
capturas en una carpeta nueva. No hubo consultas a SRI ni ANT reales.

- Backend `mvnw -B clean verify`: **165 pruebas**, 0 fallos, 0 errores y
  0 omitidas; BUILD SUCCESS y JAR generado. Los XML de Surefire son de esta
  ejecución ordinaria.
- `RedisPuntosLicenciaCacheIntegrationIT`: **2 pruebas** separadas, aprobadas
  contra Redis 7.4.12 real en Docker por loopback. No se suman a las 165 pruebas
  ordinarias.
- `npm test` y `npm run test`: **33 pruebas aprobadas en cada invocación** de la
  misma suite; lint y build también pasaron. El escaneo del bundle estándar no
  encontró los marcadores revisados de la demo ANT.
- `npm run test:e2e`: **3 aprobadas** con stub SRI local.
- `npm run test:e2e:ant`: **7 aprobadas** con simulador local, sin Redis.
- `npm run test:e2e:ant:redis`: **1 aprobada**; verificó `SIMULATED` → Redis →
  timeout del simulador → `CACHE`, con `originSource=SIMULATED`, `stale=true` y
  total y `fetchedAt` conservados.

En una comprobación adicional del 16 de noviembre de 2026 se abrió el build de
producción local con Playwright. No aparecieron el acceso del encabezado, el
botón de la tarjeta ANT ni la vista de demostración. El JavaScript compilado sí
conserva la etiqueta condicional «Abrir simulación académica»; esto no habilita
la función ni permite abrirla en producción. Por tanto, la garantía comprobada
es de inaccesibilidad en la interfaz de producción, no la eliminación de cada
cadena relacionada con la demo del bundle.

El primer intento de la prueba JUnit optativa de Redis no llegó a ejecutar casos:
PowerShell dividió argumentos Maven `-D`. Se corrigió la invocación pasando cada
argumento por separado; el segundo intento ejecutó y aprobó las dos pruebas.
El log del primer intento se conserva y está identificado como tal.

Enlaces a los [informes del backend](evidencias-ant-redis/ejecuciones/20261116-013654/backend/),
[logs y reportes frontend](evidencias-ant-redis/ejecuciones/20261116-013654/frontend/),
[capturas SRI](evidencias-ant-redis/ejecuciones/20261116-013654/frontend/e2e-sri/),
[escenarios ANT fixture](evidencias-ant-redis/ejecuciones/20261116-013654/frontend/e2e-ant/)
y [recorrido Playwright con Redis local](evidencias-ant-redis/ejecuciones/20261116-013654/frontend/e2e-ant-redis/).
El índice de evidencias enumera archivos concretos y explica mocks, fixtures y
alcances.

## Limitaciones pendientes

- No existe una respuesta funcional real de puntos ANT ni un parser/adaptador ANT definitivo.
- El esquema `data.totalPuntos` es el contrato normalizado de la aplicación, no un campo confirmado del proveedor.
- La integración real, la vigencia aceptable de los datos, la semántica de ausencia y cualquier uso de `ps_placa` siguen pendientes de evidencia autorizada.
- Redis Cloud no está configurado; el fallback ANT no se ha comprobado contra una indisponibilidad real del proveedor.

## Evidencia de la verificación final

La ejecución final del 16 de noviembre de 2026, posterior a los cambios de navegación, tiene sus resultados y límites en el [registro de verificación final](evidencias-final/20261116/README.md). La [captura de Home](evidencias-final/20261116/frontend/ant/home-ant-demo-access.png) muestra el acceso de desarrollo desde el encabezado y la tarjeta. Las capturas del recorrido con Spring Boot y del recorrido con Redis 7.4.12 real se conservan, respectivamente, en [ANT local](evidencias-ant-redis/ejecuciones/20261116-final-ant-local/) y [ANT + Redis](evidencias-ant-redis/ejecuciones/20261116-final-ant-redis/). La recuperación es desde el simulador y no demuestra una consulta o caída real de ANT.