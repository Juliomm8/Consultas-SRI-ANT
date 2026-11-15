# Verificación de la API REST SRI

Fecha de ejecución: 2026-11-14. Java detectado por Maven: 17.0.20.1. La versión del proyecto es Spring Boot 3.5.16.

## Verificación completa

Comando ejecutado desde `backend/`:

```powershell
.\mvnw.cmd -B clean verify
```

Resultado: `BUILD SUCCESS`; 81 pruebas, 0 fallos, 0 errores y 0 omitidas. Maven generó y reempaquetó:

`backend/target/consultas-sri-ant-backend-0.0.1-SNAPSHOT.jar`

La misma suite completa se ejecutó antes de documentar este resultado. No se realizaron solicitudes a los servicios SRI reales.

## Pruebas de endpoints y adaptadores

| Grupo | Ejecución | Resultado | Tipo de prueba |
|---|---|---|---|
| Dominio (contribuyente y RUC) | Incluido en `clean verify` | 15 pruebas aprobadas | Unitarias; valores sintéticos |
| Casos de uso (contribuyentes y vehículos) | Incluido en `clean verify` | 7 pruebas aprobadas | Unitarias |
| DTOs y validación de entrada | Incluido en `clean verify` | 14 pruebas aprobadas | Unitarias; entradas sintéticas |
| Adaptador SRI de contribuyentes | Incluido en `clean verify` | 12 pruebas aprobadas | HTTP simulado con `MockRestServiceServer`; fixture JSON sintético |
| Adaptador SRI de vehículos | Incluido en `clean verify` | 13 pruebas aprobadas | HTTP simulado con `MockRestServiceServer`; fixture JSON sintético |
| Controladores REST | Incluido en `clean verify` | 12 pruebas aprobadas | `MockMvc`; casos de uso simulados |
| Manejo de errores y soporte compartido | Incluido en `clean verify` | 6 pruebas aprobadas | Handler y soporte/configuración SRI, sin llamadas externas |
| Contexto y health | Incluido en `clean verify` | 2 pruebas aprobadas | Arranque de Spring y HTTP local aleatorio |
| Suite completa existente y ampliada | `clean verify` | 81 pruebas aprobadas | Unitaria, slice HTTP, adaptador HTTP simulado y arranque local |

También se ejecutó la selección enfocada siguiente y pasó con 32 pruebas:

```powershell
.\mvnw.cmd -B "-Dtest=ContribuyenteTest,SriContribuyenteHttpAdapterTests,ConsultationRestControllerTests,BackendHealthEndpointTests" test
```

Los tests REST comprobaron ambas rutas POST, las validaciones, JSON mal formado, los sobres de error, los códigos HTTP, la correlación del `requestId`, la lista vehicular vacía y la ausencia de campos sensibles en los errores. Los casos exitosos de controlador usan respuestas sintéticas; no demuestran una consulta externa en ejecución.

Las pruebas de adaptador usan `MockRestServiceServer`, incluida la preservación de propiedades permitidas, tipos escalares y datos anidados del contribuyente. No hacen conexiones a SRI.

## Health local

`BackendHealthEndpointTests` inició el servidor Spring Boot embebido en un puerto local aleatorio y solicitó `GET /actuator/health`. Resultado observado: HTTP 200 con estado `UP`. La comprobación no necesitó Redis, ANT ni SRI.

## Alcance de la evidencia

- Los resultados HTTP automatizados provienen de MockMvc, `MockRestServiceServer` o del servidor Spring local, según la tabla.
- Los identificadores, emails, placas, nombres y datos SRI en los fixtures son sintéticos. No se incorporaron datos de consultas autorizadas.
- No se ejecutaron consultas reales a SRI durante esta implementación.
- La respuesta SRI para personas jurídicas y la semántica general de `data: []` en vehículos continúan pendientes de confirmación.
- ANT no tiene una respuesta funcional confirmada; su integración y el respaldo Redis siguen pendientes.
