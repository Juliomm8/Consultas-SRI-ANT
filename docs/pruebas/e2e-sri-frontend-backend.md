# Prueba integral E2E: navegador, frontend y backend SRI

## Propósito

Esta prueba cubre la cadena React → Vite → Spring Boot → controladores →
servicios de aplicación → adaptadores HTTP SRI → servidor SRI simulado →
respuesta representada en Chromium.

React, Vite, el JAR de Spring Boot, sus controladores, servicios y adaptadores
son los componentes reales del proyecto. Solo el servidor externo SRI se
simula, con fixtures sintéticos que siguen la estructura ya usada en las
pruebas de los adaptadores: un booleano JSON de existencia, un arreglo JSON de
contribuyentes y un objeto JSON raíz de vehículo.

## Recorridos cubiertos

1. Consulta de contribuyente: valida que la petición llegue al backend por el
   proxy Vite, que Spring consulte existencia y luego detalles en el stub, que
   se seleccione el registro del arreglo y que React presente la clasificación
   confirmada. Se comprueba la coincidencia del `requestId` de respuesta y
   encabezado.
2. Consulta de vehículo: en el mismo recorrido, valida que el adaptador envíe
   el parámetro contractual al stub y que React presente los campos recibidos.
3. Error de proveedor: el stub responde HTTP 503 durante la verificación de
   existencia. El backend aplica el contrato existente (`502
   PROVIDER_BAD_RESPONSE`) y React presenta un mensaje seguro, sin el cuerpo
   del proveedor ni las entradas sintéticas.

Son tres recorridos funcionales distribuidos en **dos pruebas Playwright**:
la primera prueba incluye los recorridos exitosos de contribuyente y vehículo;
la segunda cubre el error HTTP 503. Por eso Playwright reporta 2 pruebas, no 3.

El stub conserva únicamente operación, método, nombres de parámetros y código
de respuesta. No persiste ni devuelve los valores de parámetros. El navegador
bloquea solicitudes a hosts no locales. En el perfil `e2e`, el backend falla al
iniciar si cualquiera de sus tres destinos SRI no usa HTTP en `127.0.0.1` y el
mismo puerto del stub.

## Ejecución

Desde `frontend/`:

```powershell
npm install
npx playwright install chromium
npm run test:e2e
```

`npm run test:e2e` empaqueta el backend con Maven Wrapper, selecciona puertos
locales disponibles, inicia el stub, el JAR y Vite, ejecuta Playwright y detiene
los procesos iniciados incluso si una prueba falla. Los artefactos de Playwright
quedan en `frontend/node_modules/.cache/e2e-results/`.

## Alcance y limitaciones

- Es una prueba E2E local con un proveedor SRI simulado; no acredita la
  disponibilidad ni el comportamiento actual de los servicios reales del SRI.
- No consulta ANT, no utiliza Redis y no valida infraestructura desplegada.
- El perfil y las comprobaciones de loopback están dirigidos a impedir que esta
  ejecución E2E contacte servicios SRI externos.
- Las entradas del fixture son sintéticas y no representan personas ni
  vehículos reales.

## Resultado de esta ejecución

Ejecución anterior del 15 de noviembre de 2026:

- `frontend`: `npm run test` — 20 pruebas aprobadas.
- `frontend`: `npm run build` — compilación TypeScript y build Vite aprobados.
- `frontend`: `npm run lint` — aprobado sin errores.
- `backend`: `.\mvnw.cmd -B clean verify` — BUILD SUCCESS, 106 pruebas,
  0 fallos, 0 errores y JAR empaquetado.
- E2E: `npm run test:e2e` — 2 pruebas Playwright aprobadas en Chromium.

La ejecución E2E recibió las llamadas del adaptador únicamente en el stub
loopback: tres GET para el flujo correcto de contribuyente y vehículo, y un GET
con HTTP 503 para el escenario de error. El stub confirmó los nombres de
parámetros del contrato; los valores no se incluyen en su registro. El navegador
no intentó acceder a hosts externos, los identificadores no quedaron en
`localStorage` o `sessionStorage`, el runner verificó que los logs del backend
no incluyeran valores de las solicitudes ni el marcador del cuerpo externo, y
terminó sus procesos locales.
Estos resultados son de fixtures sintéticos y no son consultas reales al SRI.

## Verificación de auditoría integral

Verificado el **15 de noviembre de 2026**:

- `frontend`: `npm run test` — 20 pruebas Vitest aprobadas.
- `frontend`: `npm run build` — compilación TypeScript y build Vite aprobados.
- `frontend`: `npm run lint` — aprobado sin errores.
- `backend`: `.\mvnw.cmd -B clean verify` — `BUILD SUCCESS`, 109 pruebas,
  0 fallos, 0 errores y 0 omitidas; JAR empaquetado.
- E2E: `npm run test:e2e` — 2 pruebas Playwright aprobadas en Chromium,
  que cubren los tres recorridos descritos arriba.
- Diagnóstico local: `pwsh -NoProfile -File .\scripts\tests\validar-casos-exitosos.tests.ps1`
  — aprobado.

Todas estas comprobaciones fueron locales y utilizaron fixtures o servicios
locales; no se realizaron consultas reales a SRI ni ANT.

### Reejecución con caché Redis opcional

El **15 de noviembre de 2026**, después de añadir la infraestructura Redis
desactivada por defecto:

- `backend`: `.\mvnw.cmd -B clean verify` — `BUILD SUCCESS`, 125 pruebas,
  0 fallos, 0 errores y 0 omitidas; JAR empaquetado. La prueba de contexto
  confirmó que no se crean beans Redis cuando la opción está desactivada.
- E2E: `npm run test:e2e` — 2 pruebas Playwright aprobadas en Chromium. El
  runner inició los procesos locales y el stub SRI, y los detuvo al terminar.

El E2E usa únicamente el stub local y no consulta SRI ni ANT reales. Después,
la prueba opcional `RedisPuntosLicenciaCacheIntegrationIT` se ejecutó aparte
contra un contenedor Redis 7.4 local, sin convertir Redis en una dependencia de
la suite normal.

## Ronda de verificación para publicación

Ejecutada nuevamente el **15 de noviembre de 2026**, sin consultas a servicios
externos:

- Backend: `mvnw.cmd -B clean verify` desde `backend/` — `BUILD SUCCESS`,
  **159 pruebas**, 0 fallos, 0 errores y 0 omitidas; JAR generado.
- Frontend: `npm test` — **20 pruebas aprobadas**; `npm run lint` y
  `npm run build` también pasaron.
- E2E: `npm run test:e2e` — **2 pruebas Playwright aprobadas**. El escenario
  exitoso cubre contribuyente y vehículo; el otro verifica el error del
  proveedor.
- PowerShell: `scripts/tests/validar-casos-exitosos.tests.ps1` desde la raíz
  — aprobado con fixtures en memoria, sin solicitudes de red.
- Redis: `RedisPuntosLicenciaCacheIntegrationIT` — **1 prueba aprobada** contra
  Redis 7.4 efímero en loopback; el contenedor se eliminó al terminar.

La suite Redis es optativa y separada de las 159 pruebas de Maven. Estas
comprobaciones verifican componentes locales, fixtures y el stub SRI; no
demuestran una consulta ANT funcional ni conectividad de Redis Cloud.
