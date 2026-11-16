# Frontend — Consultas SRI ANT

Aplicación web en React, TypeScript y Vite para consultar contribuyentes y
vehículos mediante la API propia de Spring Boot. El navegador no llama
directamente a SRI ni a ANT.

## Requisitos

- Node.js 20.19 o superior (también 22.12 o superior); se desarrolló y verificó
  con Node 24.18.0.
- npm incluido con Node.js.
- Backend local iniciado en `http://localhost:8080` para realizar consultas.

Vite documenta los requisitos actuales de Node.js en su
[guía de inicio](https://vite.dev/guide/).

## Iniciar en desarrollo

En una terminal PowerShell, inicia primero el backend:

```powershell
cd C:\Dev\Arquitectura\Consultas-SRI-ANT\backend
.\mvnw.cmd spring-boot:run
```

En otra terminal:

```powershell
cd C:\Dev\Arquitectura\Consultas-SRI-ANT\frontend
npm install
npm run dev
```

Abre la dirección local que indique Vite (por defecto `http://localhost:5173`).
El proxy `/api` de Vite reenvía las llamadas al backend de
`http://localhost:8080`, por lo que el navegador usa las rutas `/api/v1/...`
sin necesitar una configuración CORS adicional en desarrollo.

## Configuración de la API

La URL base predeterminada es `/api/v1`. Para otra instalación del backend,
copia `.env.example` a `.env.local` y configura `VITE_API_BASE_URL`, por
ejemplo:

```dotenv
VITE_API_BASE_URL=http://localhost:8080/api/v1
```

Esta variable debe apuntar al backend de este proyecto. No contiene secretos.
El cliente HTTP centraliza las solicitudes POST, usa JSON, limita cada petición
a 15 segundos y traduce códigos de error a mensajes comprensibles. Cuando la
API envía `requestId`, la interfaz lo muestra como referencia.

## Operaciones disponibles

### Consultar contribuyente

`POST /api/v1/contribuyentes/consultas`

```json
{
  "email": "usuario@ejemplo.com",
  "ruc": "0000000000000"
}
```

El correo se valida y se envía al backend como parte del contrato, pero el
backend no lo remite al SRI, almacena ni devuelve. La interfaz solo presenta la
consulta como satisfactoria cuando la clasificación recibida es `NATURAL`; una
clasificación desconocida nunca se presenta como persona natural confirmada.

### Consultar vehículo

`POST /api/v1/vehiculos/consultas`

```json
{
  "placa": "ABC1234"
}
```

El formulario se habilita después de confirmar la consulta de una persona
natural. Presenta los campos vehiculares devueltos y omite los nulos. Si el
backend devuelve `data: []`, muestra que no recibió información y aclara que
eso no confirma por sí solo que el vehículo no exista. Una consulta por placa
no demuestra titularidad.

Los valores anteriores son ejemplos sintéticos; no son consultas reales ni
demuestran una respuesta exitosa del proveedor.

## Interfaz y recursos visuales

La portada y el flujo de consulta están documentados en
[`docs/diseno/rediseño-ui-ux.md`](../docs/diseno/rediseño-ui-ux.md). Las
fotografías se sirven desde `public/images/` en WebP; su fuente, autor y
licencia están registrados en
[`docs/diseno/recursos-visuales.md`](../docs/diseno/recursos-visuales.md).
Las capturas de referencia y las generadas con fixtures Playwright están en
[`docs/diseno/evidencias-ui/`](../docs/diseno/evidencias-ui/).

### ANT

La consulta automática directa de puntos ANT todavía no está integrada. La
interfaz normal enlaza al [trámite oficial de información del conductor en Gob.ec](https://www.gob.ec/ant/tramites/emision-certificado-informacion-conductor),
que es un flujo web oficial y no una API de consulta automática documentada
para esta aplicación. En el flujo normal no hay formulario ni llamada del
navegador a ANT. La consulta automática se habilitará cuando se confirme un
mecanismo autorizado. El estado técnico está descrito en
[`docs/integraciones/diagnostico-endpoints.md`](../docs/integraciones/diagnostico-endpoints.md#estado-de-la-integración-ant).

### Demostración local opt-in

Existe una vista independiente para probar los estados del backend simulado.
En Home aparece como **Demostración ANT** en la navegación y como **Simular
consulta ANT** en la tarjeta del servicio solo cuando Vite está en modo
desarrollo y `VITE_ANT_DEMO=true`. El componente de la demo se abre dentro de
`App.tsx` y tiene el botón **Volver al inicio**; no usa una ruta de React
Router. La build de producción no muestra esos accesos ni permite abrir la
vista, aunque puede conservar la etiqueta condicional del botón en el bundle.

Para habilitarla, establece la variable antes de iniciar Vite y reinicia el
servidor si ya estaba ejecutándose:

```powershell
$env:VITE_ANT_DEMO = 'true'
$env:VITE_API_PROXY_TARGET = 'http://127.0.0.1:8080'
npm run dev -- --host 127.0.0.1 --port 5173 --strictPort
```

En una terminal aparte, el backend debe ejecutarse con el perfil
`local-simulation`. La vista acepta únicamente el fixture sintético fijo
`0000000000`, llama al endpoint local protegido por el perfil backend
`local-simulation`, y siempre muestra que el resultado es `SIMULATED` y no
oficial. No se conecta a ANT. Cuando se habilita Redis exclusivamente para la
demo local, también puede mostrar una respuesta `CACHE` con procedencia
`SIMULATED`, claramente advertida como sintética y posiblemente desactualizada.
Consulta
[`docs/pruebas/demo-ant-local.md`](../docs/pruebas/demo-ant-local.md) para los
comandos, escenarios y evidencia Playwright.

## Datos y privacidad

- Las identificaciones y el correo permanecen en memoria de los componentes
  durante la consulta; no se guardan en `localStorage`, `sessionStorage` ni en
  URLs.
- El frontend no registra cuerpos, identificaciones ni errores de red en la
  consola.
- Las respuestas de error muestran mensajes controlados y, cuando corresponde,
  el `requestId`; no presentan cuerpos externos ni trazas.
- Una recarga del navegador descarta los valores mantenidos por la interfaz.

## Pruebas y compilación

Desde `frontend/`:

```powershell
npm run test
npm run build
npm run lint
```

Las pruebas usan respuestas sintéticas y no realizan consultas reales al SRI ni
a la ANT. Para el contrato de backend, las limitaciones de las integraciones y
la ejecución de sus pruebas, consulta [`../backend/README.md`](../backend/README.md),
[`docs/api/contrato-v1.md`](../docs/api/contrato-v1.md) y el diagnóstico de
integraciones enlazado arriba.

## Prueba integral navegador a backend

Instala las dependencias de desarrollo una vez y descarga Chromium de
Playwright si no está instalado:

```powershell
npm install
npx playwright install chromium
```

Ejecuta la prueba integral desde `frontend/`:

```powershell
npm run test:e2e
npm run test:e2e:ant
```

El comando empaqueta y arranca el JAR real de Spring Boot, inicia Vite y un
servidor HTTP SRI simulado en puertos locales disponibles, y abre Chromium en
modo headless. Verifica consultas sintéticas de contribuyente y vehículo, la
propagación del `requestId` y el manejo seguro de una respuesta HTTP 503 del
proveedor simulado. El perfil E2E del backend rechaza cualquier endpoint SRI
que no apunte al mismo stub HTTP en `127.0.0.1`; Vite también rechaza un proxy
no local en este modo. Al finalizar, el runner detiene los procesos que inició.

La prueba recorre React → proxy Vite → Spring Boot, controladores, servicios y
adaptadores HTTP reales → servidor SRI local simulado → respuesta en Chromium.
No verifica disponibilidad real del SRI y no envía solicitudes a SRI o ANT.
Sus identificaciones, correo y placa son fixtures sintéticos de prueba.
El alcance y los resultados ejecutados están en
[`docs/pruebas/e2e-sri-frontend-backend.md`](../docs/pruebas/e2e-sri-frontend-backend.md).
La prueba E2E de la simulación de puntos se ejecuta por separado con
`npm run test:e2e:ant`; su aislamiento y evidencia están documentados en
[`docs/pruebas/demo-ant-local.md`](../docs/pruebas/demo-ant-local.md).
