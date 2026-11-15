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

### ANT

La interfaz informa que los puntos de licencia ANT no están disponibles en esta
versión porque el portal oficial respondió HTTP 502 durante el diagnóstico. No
hay formulario ni llamada de navegador a ANT, no se muestran puntos simulados y
no se interpreta una caída como cero puntos. El estado técnico está descrito en
[`docs/integraciones/diagnostico-endpoints.md`](../docs/integraciones/diagnostico-endpoints.md#estado-de-la-integración-ant).

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
