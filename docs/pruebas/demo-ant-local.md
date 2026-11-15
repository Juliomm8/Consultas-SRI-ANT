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
```

El runner Playwright dedicado activa también el guard de perfil `e2e`; configura los destinos SRI en un stub local y comprueba que no reciba solicitudes. El escenario ANT se resuelve dentro del backend por el adaptador simulado. No se llama al portal ANT ni a los servicios SRI.

## Ejecución manual

En una terminal PowerShell, inicia el backend:

```powershell
cd C:\Dev\Arquitectura\Consultas-SRI-ANT\backend
$env:SPRING_PROFILES_ACTIVE = 'local-simulation'
$env:ANT_SIMULATION_SCENARIO = 'SUCCESS'
.\mvnw.cmd spring-boot:run
```

En otra terminal, inicia Vite enlazado a loopback y activa la vista explícitamente:

```powershell
cd C:\Dev\Arquitectura\Consultas-SRI-ANT\frontend
$env:VITE_ANT_DEMO = 'true'
$env:VITE_API_PROXY_TARGET = 'http://127.0.0.1:8080'
npm run dev -- --host 127.0.0.1
```

Abre la URL local mostrada por Vite y selecciona **Abrir demostración ANT**. Para desactivar la vista, detén Vite o elimina/cambia `VITE_ANT_DEMO` y reinícialo. Detén el backend local cuando termines. No expongas el servidor de desarrollo a una interfaz pública.

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

Reinicia el backend al cambiar el escenario. No hay selector de escenarios en la interfaz ni un parámetro de escenario expuesto por HTTP.

## Estados y significado

- **Éxito:** muestra `totalPuntos`, `source=SIMULATED`, `fetchedAt` y un aviso no oficial.
- **Cero:** cero es un resultado sintético válido del escenario `ZERO`; no significa “sin información”. No representa el saldo de una persona.
- **Sin información:** el escenario `NO_DATA` no devuelve un número de puntos. La interfaz explica que no equivale a cero.
- **Indisponibilidad, timeout y respuesta inválida:** se muestran como errores controlados con `requestId`; no generan un saldo ni datos de caché ficticios.
- **HTTP 502:** es una falla técnica y no un saldo de cero. La integración real ANT aún no ha proporcionado una respuesta exitosa verificable.

La vista solo presenta un resultado si el backend lo identifica como `SIMULATED`, con `stale=false` y metadata válida. Rechaza una fuente inesperada como `ANT` o `CACHE` en esta ruta de demostración. La simulación nunca se guarda en Redis como resultado ANT.

### ANT, CACHE y SIMULATED

| Fuente | Significado en la arquitectura | Uso en la demo |
|---|---|---|
| `ANT` | Resultado validado de una futura integración real. | No disponible; no hay adaptador real implementado. |
| `CACHE` | Último resultado ANT válido recuperado de Redis, con `fetchedAt` original y estado potencialmente desactualizado. | No se simula ni se presenta en esta vista. |
| `SIMULATED` | Fixture determinista del adaptador local. | Única fuente que la vista acepta. Siempre aparece como no oficial. |

Redis está desactivado por defecto. La infraestructura de caché y sus pruebas no significan que Redis Cloud esté conectado. El Circuit Breaker local protege el adaptador simulado; no protege una llamada real a ANT.

## Verificaciones automatizadas

Ejecuta las suites desde `frontend/`:

```powershell
npm test
npm run lint
npm run build
npm run test:e2e
npm run test:e2e:ant
```

`npm run test:e2e` conserva las pruebas E2E SRI existentes contra un stub local. `npm run test:e2e:ant` construye el backend, inicia Spring Boot con `e2e,local-simulation`, enlaza Vite y Spring a puertos loopback disponibles, configura un stub SRI local como guardia y ejecuta Playwright contra el simulador ANT local. El runner termina los procesos que inicia. Ninguna suite accede a ANT o SRI reales.

Vitest cubre la advertencia permanente, resultado positivo, cero, ausencia de información, indisponibilidad, timeout, respuesta inválida, request ID, rechazo de fuentes no simuladas, validación y ausencia de almacenamiento persistente. Playwright comprueba el recorrido React → Vite → Spring Boot → controlador → caso de uso → simulador, las cabeceras y el sobre de respuesta, que el stub SRI no reciba solicitudes, y que la identificación no aparezca en URL, almacenamiento ni consola.

## Evidencias académicas

El runner Playwright genera una captura auténtica del resultado sintético exitoso en:

[`evidencias/ant-demo/demo-ant-local-success.png`](evidencias/ant-demo/demo-ant-local-success.png)

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

## Última ejecución verificada

El **15 de noviembre de 2026** se ejecutaron las verificaciones de esta entrega, sin llamadas a SRI o ANT reales:

- Backend: `..\mvnw.cmd -B clean verify`, **159 pruebas aprobadas**, 0 fallos, 0 errores y JAR generado.
- Frontend: `npm test`, **30 pruebas aprobadas**; `npm run lint` pasó.
- Producción: `npm run build` pasó con `VITE_ANT_DEMO=true`; se revisó el bundle y no contiene el texto, botón ni ruta de la demo.
- E2E SRI existente: `npm run test:e2e`, **2 pruebas aprobadas**.
- Demo local: `npm run test:e2e:ant`, **1 prueba Playwright aprobada**. El stub de guardia SRI recibió cero solicitudes y el runner no configuró ningún proveedor externo.

El comando Playwright ANT genera la captura sintética enlazada arriba. Los procesos de backend y Vite creados por los runners se detienen al terminar.

## Limitaciones pendientes

- No existe una respuesta funcional real de puntos ANT ni un parser/adaptador ANT definitivo.
- El esquema `data.totalPuntos` es el contrato normalizado de la aplicación, no un campo confirmado del proveedor.
- La integración real, la vigencia aceptable de los datos, la semántica de ausencia y cualquier uso de `ps_placa` siguen pendientes de evidencia autorizada.
- Redis Cloud no está configurado; el fallback ANT no se ha comprobado contra una indisponibilidad real del proveedor.
