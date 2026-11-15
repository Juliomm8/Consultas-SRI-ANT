# Verificación de la API REST SRI

Fecha de ejecución: 2026-11-14. Java detectado por Maven: 17.0.20.1. La versión del proyecto es Spring Boot 3.5.16.

## Verificación completa

El resultado de 81 pruebas que sigue corresponde a la ejecución histórica del
2026-11-14. En una revisión QA posterior, el **2026-11-15 a las 01:21:27 -05:00**,
se ejecutó desde `backend/`:

```powershell
.\mvnw.cmd -B verify
```

Resultado de esa ejecución: `BUILD SUCCESS`; **103 pruebas**, 0 fallos, 0 errores
y 0 omitidas.

En la corrección de la prueba HTTP, el **2026-11-15 a las 01:31:28 -05:00**,
se ejecutaron `-Dtest=SriResponseSupportTests test` y después, desde `backend/`:

```powershell
.\mvnw.cmd -B clean verify
```

Resultado de esa ejecución: `BUILD SUCCESS`; **104 pruebas**, 0 fallos,
0 errores y 0 omitidas. `SriResponseSupportTests` reportó 10 pruebas, incluida
`mapsHttpProviderErrorsAndLogsOnlySafeMetadata`; Maven generó el JAR
`consultas-sri-ant-backend-0.0.1-SNAPSHOT.jar`. Se usaron pruebas locales,
MockMvc y `MockRestServiceServer`; no hubo solicitudes reales al SRI ni a la ANT.

En la auditoría integral del **2026-11-15**, desde `backend/`, se ejecutó de
nuevo:

```powershell
.\mvnw.cmd -B clean verify
```

Resultado más reciente antes de preparar la caché Redis: `BUILD SUCCESS`;
**109 pruebas**, 0 fallos, 0 errores y 0 omitidas. Surefire registró 109 pruebas
en 14 informes y Maven generó el JAR. La ejecución fue local y sintética; no se
consultaron SRI ni ANT.

En la preparación de la infraestructura Redis, el **2026-11-15**, se ejecutó
desde `backend/`:

```powershell
.\mvnw.cmd -B clean verify
```

Resultado: `BUILD SUCCESS`; **125 pruebas**, 0 fallos, 0 errores y 0 omitidas.
Surefire registró 125 pruebas en 17 informes y Maven generó el JAR. La prueba de
contexto comprobó que Redis permanece desactivado sin beans de conexión o caché;
las pruebas del adaptador usaron mocks. No se consultaron servicios SRI/ANT ni
una instancia Redis real.

Además, el **2026-11-15 a las 03:43:46 -05:00** se ejecutó de forma optativa
`-Dtest=RedisPuntosLicenciaCacheIntegrationIT` contra un contenedor Docker
`redis:7.4-alpine` enlazado solo a loopback: 1 prueba aprobada, 0 fallos, 0
errores y 0 omitidas. Verificó lectura/escritura JSON, `fetchedAt`, origen
`CACHE` y TTL; eliminó su clave y el contenedor temporal se detuvo. Esta prueba
no forma parte de `clean verify` ni realizó consultas SRI/ANT.

Después de alinear el DTO y el puerto de caché con `Cedula`, el **2026-11-15 a
las 03:57:23 -05:00** se volvió a ejecutar desde `backend/` `.\mvnw.cmd -B
clean verify`: `BUILD SUCCESS`, **139 pruebas**, 0 fallos, 0 errores y 0
omitidas. Surefire generó 17 informes y Maven generó el JAR. No se realizaron
consultas SRI/ANT ni se usó Redis real en esta suite.

El **2026-11-15 a las 03:57:42 -05:00** pasó nuevamente 1 prueba optativa
`RedisPuntosLicenciaCacheIntegrationIT` contra Redis 7.4 en un contenedor local
enlazado a loopback. Verificó la clave HMAC derivada de `Cedula`, escritura y
lectura, exclusión de la cédula del contenido almacenado, `fetchedAt`, origen
`CACHE` y TTL. No se consultó ANT; se eliminó la clave de prueba y el contenedor
se detuvo.

Comando de la ejecución histórica original del 2026-11-14, desde `backend/`:

```powershell
.\mvnw.cmd -B clean verify
```

Resultado histórico: `BUILD SUCCESS`; 81 pruebas, 0 fallos, 0 errores y 0 omitidas. Maven generó y reempaquetó:

`backend/target/consultas-sri-ant-backend-0.0.1-SNAPSHOT.jar`

No se realizaron solicitudes a los servicios SRI reales.

## Pruebas de endpoints y adaptadores

El siguiente desglose corresponde a la corrida histórica de 81 pruebas del
2026-11-14; no es un desglose por grupo de las reejecuciones posteriores de
103, 104 o 109 pruebas.

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
- ANT no tiene una respuesta funcional confirmada. La infraestructura Redis y el fallback del caso de uso están implementados y probados con puertos/fixtures sintéticos; aún no existe una fuente ANT real que permita validar el flujo extremo a extremo con datos del proveedor.

## Verificación más reciente de backend

El **2026-11-15 a las 12:19:12 -05:00**, después de añadir el contrato normalizado y el simulador ANT local, se ejecutó desde `backend/`:

```powershell
.\mvnw.cmd -B clean verify
```

Resultado: `BUILD SUCCESS`; **159 pruebas**, 0 fallos, 0 errores y 0 omitidas. Surefire ejecutó la suite completa y Maven generó el JAR. Las pruebas de puntos usaron fixtures y puertos simulados; no se consultó ANT ni SRI.
