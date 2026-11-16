# Verificación final local — 16 de noviembre de 2026

Este registro reúne las verificaciones ejecutadas después de integrar los cambios de navegación de la demostración ANT. Las horas corresponden al entorno local de Ecuador (UTC−05:00). Los recorridos usaron fixtures sintéticos, un stub SRI local y el simulador ANT; no se hicieron llamadas reales a SRI o ANT.

## Resultados

| Verificación | Resultado de esta ejecución | Infraestructura y alcance |
|---|---:|---|
| `.\mvnw.cmd -B clean verify` | 165 pruebas; 0 fallos, 0 errores, 0 omitidas. BUILD SUCCESS. | Suite ordinaria Surefire. No incluye las pruebas optativas `*IT` de Redis. |
| JUnit `RedisPuntosLicenciaCacheIntegrationIT` | 2 pruebas; 0 fallos, 0 errores, 0 omitidas. BUILD SUCCESS. | Redis 7.4.12 real en Docker, puerto publicado solo en `127.0.0.1`; ejecución separada. |
| `npm test` | 33/33 pruebas aprobadas. | Vitest, React Testing Library y fixtures locales. |
| `npm run lint` | Aprobado. | Oxlint. |
| `npm run build` | Aprobado. | TypeScript y Vite. |
| `npm run test:e2e` | 3/3 pruebas aprobadas. | React → Vite → Spring Boot → stub SRI local. |
| `npm run test:e2e:ant` | 7/7 pruebas aprobadas. | React → Vite → Spring Boot → simulador local; el stub guardia SRI recibió cero solicitudes. |
| `npm run test:e2e:ant:redis` | 1/1 prueba aprobada. | Mismo recorrido con Redis 7.4.12 real en Docker por loopback: `SIMULATED` → escritura Redis → timeout simulado → `CACHE`. |
| `pwsh -NoProfile -File .\scripts\tests\validar-casos-exitosos.tests.ps1` | Aprobado. | Fixtures sintéticos y respuestas HTTP simuladas; no hace consultas reales. El archivo de pruebas preexistente no se incluye en los commits de este cierre. |
| Arranque predeterminado de Spring Boot | `GET /actuator/health` respondió `UP`. | JAR iniciado sin perfil ANT ni configuración Redis; no se consultaron proveedores. El proceso de comprobación se detuvo al terminar. |
| Producción con `VITE_ANT_DEMO=true` | Build aprobada; la interfaz de simulación no apareció en `/`, `/?demo=true` ni `/ant-demo`. | Vite preview de la build de producción. El indicador `import.meta.env.DEV` mantiene desactivado el acceso en producción. |
| Acceso de desarrollo a la demo desde Home | Aprobado. | Vite local con `VITE_ANT_DEMO=true`; se vieron las acciones de navegación del encabezado y de la tarjeta ANT, sin llamadas de API ni solicitudes externas. |

Las 165 pruebas Surefire y las 2 pruebas optativas Redis son ejecuciones diferentes y sus cantidades **no se suman** como si provinieran de una sola ejecución. El fallback probado usa el adaptador simulado, no una caída real de ANT. Redis Cloud no está configurado ni fue probado.

## Logs e informe XML

Los logs reproducen la salida de los comandos ejecutados. Se reemplazaron las rutas locales de workspace y perfil de usuario por marcadores genéricos; los resultados, nombres de pruebas y contadores se conservaron.

- [Maven `clean verify` — 165 pruebas](logs/backend-clean-verify.txt)
- [Prueba JUnit Redis — log](logs/backend-redis-junit.txt)
- [Informe JUnit Redis — XML válido, 2 pruebas](backend/redis/RedisPuntosLicenciaCacheIntegrationIT.xml)
- [Vitest](logs/frontend-vitest.txt)
- [Lint](logs/frontend-lint.txt)
- [Build de producción](logs/frontend-build.txt)
- [Playwright SRI](logs/playwright-sri.txt)
- [Playwright ANT local](logs/playwright-ant-local.txt)
- [Playwright ANT + Redis](logs/playwright-ant-redis.txt)
- [Pruebas sintéticas PowerShell](logs/powershell-diagnostic-tests.txt)
- [Build de producción al establecer la bandera de demo](logs/production-build-with-ant-demo-flag.txt)

## Capturas inspeccionadas

Las siguientes imágenes se generaron en los recorridos locales de Playwright o al inspeccionar el Home de Vite. Los resultados SRI y ANT contienen fixtures sintéticos; no son consultas reales.

| Evidencia | Qué muestra |
|---|---|
| [Home con acceso a la demostración ANT](frontend/ant/home-ant-demo-access.png) | Encabezado y tarjeta Home muestran el acceso local solo con la bandera explícita de desarrollo; también se distingue el enlace externo al trámite oficial. |
| [Portada de escritorio](frontend/sri/01-portada-escritorio.png) y [portada móvil](frontend/sri/02-portada-movil.png) | Maquetación responsive con la consulta ANT identificada como pendiente. La ejecución SRI E2E mantiene la demo desactivada. |
| [Catálogo de servicios](frontend/sri/03-catalogo-servicios.png) | Diferencia las operaciones SRI del estado ANT pendiente. |
| [Formulario de contribuyente](frontend/sri/04-formulario-contribuyente.png) y [resultado sintético](frontend/sri/05-resultado-contribuyente.png) | Campos, flujo de consulta y datos recibidos del stub local. |
| [Formulario vehicular](frontend/sri/06-formulario-vehicular.png) y [resultado sintético](frontend/sri/07-resultado-vehicular.png) | Consulta y datos del stub; la vista indica que la placa no confirma titularidad. |
| [Error seguro SRI](frontend/sri/08-error-proveedor.png) | Mensaje controlado con requestId, sin cuerpo externo ni traza. |
| [Estado ANT pendiente](frontend/sri/09-estado-ant.png) | La integración automática real no está disponible; se muestra el trámite web oficial como una opción distinta. |
| [Resultado SIMULATED de la ejecución E2E](../../evidencias-ant-redis/ejecuciones/20261116-final-ant-local/09-ant-simulado.png) | Fuente sintética, total de prueba y aviso de no oficial. |
| [Primera respuesta SIMULATED con Redis](../../evidencias-ant-redis/ejecuciones/20261116-final-ant-redis/ant-redis-simulated-success.png) | Resultado local guardado en el namespace de simulación. |
| [Recuperación CACHE con Redis](../../evidencias-ant-redis/ejecuciones/20261116-final-ant-redis/ant-redis-cache-recovery.png) | Timeout del simulador seguido de `CACHE`, origen `SIMULATED`, `stale=true`, fecha original y advertencia. |

Los request IDs visibles son referencias técnicas generadas para las pruebas. Las identificaciones, placa y valores presentados corresponden a fixtures sintéticos; no se utilizaron datos personales reales.

## Limpieza y límites

Los runners E2E detuvieron los procesos Spring Boot/Vite que iniciaron. El contenedor Redis efímero creado para la integración JUnit se detuvo y eliminó; el runner Playwright también verificó su propio contenedor. Los contenedores ajenos que ya estaban activos en Docker no se modificaron.

Esta evidencia no demuestra una integración ANT real, la disponibilidad actual de SRI, una conexión Redis Cloud ni recuperación frente a una indisponibilidad real de ANT. La prueba con Redis valida el contrato y el almacenamiento local usando el simulador.
