# Evidencias reproducibles ANT + Redis

## Ejecución de cierre `20261115-204859`

Esta ejecución reúne logs y resultados de pruebas locales. No se consultó ANT ni
SRI reales y no se probó Redis Cloud. El recorrido ANT usa el simulador local;
el almacenamiento se probó contra Redis 7.4.12 en un contenedor Docker efímero,
publicado únicamente en `127.0.0.1` y detenido con eliminación automática.
Las copias publicadas omiten rutas locales y propiedades del entorno de la
máquina; se conservaron los contadores y casos de prueba de los reportes.

### Archivos de esta ejecución

- [Log sanitizado de JUnit con Redis real](ejecuciones/20261115-204859/redis-junit-integration.txt)
- [Informe JUnit de Redis](junit/20261115-204859/TEST-ec.edu.udla.consultas.puntos.adapter.out.redis.RedisPuntosLicenciaCacheIntegrationIT.xml)
- [Resumen TXT JUnit de Redis](junit/20261115-204859/ec.edu.udla.consultas.puntos.adapter.out.redis.RedisPuntosLicenciaCacheIntegrationIT.txt)
- [Versión y enlace local de Redis](redis/20261115-204859/redis-version.txt)
- [Limpieza del contenedor](redis/20261115-204859/redis-container-lifecycle.txt)
- [Resumen de ejecuciones](ejecuciones/20261115-204859/execution-summary.json)
- [Resultados de Vitest en JUnit XML](frontend/20261115-204859/vitest-results.xml)
- [Logs de Vitest, lint y build](frontend/20261115-204859/)
- [Reporte JSON E2E SRI](frontend/20261115-204859/playwright-e2e-results.json)
- [Reporte JSON E2E ANT](frontend/20261115-204859/playwright-ant-results.json)
- [Capturas canónicas de los escenarios ANT](ejecuciones/20261116-013654/frontend/e2e-ant/)

Las capturas de Redis que se conservaron en la raíz de esta carpeta son
históricas y se mantienen intactas en el workspace. La ejecución actual vuelve
a mostrar esos mismos estados; para reducir evidencias repetidas, las copias
publicables son las capturas de su carpeta fechada.

## Ejecución E2E ANT + Redis real `20261115-211959`

`npm run test:e2e:ant:redis` pasó: **1 prueba Playwright**, 0 inesperadas,
omitidas o flaky. Esa prueba contiene dos solicitudes encadenadas: la primera
devuelve `SIMULATED` y persiste el resultado; la segunda provoca un timeout del
simulador y recupera el resultado desde Redis como `CACHE`, con
`originSource=SIMULATED`, `stale=true`, el total y `fetchedAt` originales y el
aviso de posible desactualización. No se consultó ANT ni SRI reales.

El runner verificó Redis **7.4.12** mediante `INFO server`. Docker publicó el
puerto efímero solo en `127.0.0.1`; el contenedor se detuvo en `finally` y la
comprobación posterior no encontró un contenedor nuevo de esta ejecución.

- [Reporte JSON Playwright](ejecuciones/20261115-211959/playwright-results.json)
- [Log sanitizado de `npm run test:e2e:ant:redis`](ejecuciones/20261115-211959/npm-run-test-e2e-ant-redis.txt)
- [Resumen de escenarios](ejecuciones/20261115-211959/scenario-results.txt)
- [Revisión de privacidad de logs y capturas](ejecuciones/20261115-211959/privacy-review.txt)
- [Versión y enlace de Redis](ejecuciones/20261115-211959/redis-version.txt)
- [Verificación de limpieza del contenedor](ejecuciones/20261115-211959/redis-container-lifecycle.txt)
Los PNG históricos de esta ejecución permanecen conservados localmente. Para
evitar publicar más de una captura por cada estado, las copias canónicas
publicables de `SIMULATED` y `CACHE` se enlazan en la ejecución siguiente. El
reporte conserva el identificador opaco generado por Playwright para el caso de
prueba; su secuencia numérica es metadata técnica, no una identificación de
usuario. La ejecución no registró cédulas, credenciales ni secretos.

## Ejecución reproducible `20261116-013654`

El **16 de noviembre de 2026** se ejecutaron nuevamente las pruebas del
backend, frontend y Playwright. Las pruebas utilizaron fixtures sintéticos,
stubs SRI locales, el simulador ANT local y Redis 7.4.12 en Docker por loopback.
No se consultó SRI ni ANT reales, ni se conectó Redis Cloud.

### Backend

- `mvnw -B clean verify`: **165 pruebas**, 0 fallos, 0 errores y 0 omitidas;
  BUILD SUCCESS y JAR ejecutable generado. Los 23 XML de Surefire pertenecen a
  esta ejecución ordinaria.
- `RedisPuntosLicenciaCacheIntegrationIT`: **2 pruebas**, 0 fallos, 0 errores
  y 0 omitidas en una invocación optativa separada contra Redis 7.4.12 real.
  Estas dos pruebas no se suman al total ordinario de Surefire.
- Hubo un primer intento de invocar Maven que terminó antes de ejecutar pruebas
  porque PowerShell separó los argumentos `-D`; se conserva el log de ese error
  de invocación. La segunda invocación pasó los argumentos como elementos
  explícitos y ejecutó las dos pruebas Redis correctamente.

### Frontend y navegador

- `npm test`: **33/33** pruebas aprobadas.
- `npm run test`: **33/33** pruebas aprobadas en una segunda invocación de la
  misma suite; no son 66 casos distintos.
- `npm run lint` y `npm run build`: aprobados. El escaneo del bundle normal no
  encontró los marcadores revisados de la demo ANT.
- `npm run test:e2e`: **3/3** pruebas Playwright con backend y stub SRI local.
- `npm run test:e2e:ant`: **7/7** pruebas Playwright con la simulación aislada,
  sin Redis.
- `npm run test:e2e:ant:redis`: **1/1** prueba Playwright; la primera consulta
  recibió `SIMULATED` y la segunda obtuvo `CACHE` con `originSource=SIMULATED`,
  `stale=true`, el total y `fetchedAt` originales. El runner verificó Redis
  7.4.12 y no dejó un contenedor de su ejecución.

### Informes y registros

- [Resumen Surefire (165 pruebas)](ejecuciones/20261116-013654/backend/surefire/surefire-summary.txt)
- [XML Surefire de esta ejecución (23 informes)](ejecuciones/20261116-013654/backend/surefire/surefire-reports/)
- [Log sanitizado de `clean verify`](ejecuciones/20261116-013654/maven-clean-verify.txt)
- [Informe JUnit Redis real, ejecución separada](ejecuciones/20261116-013654/backend/redis-real/RedisPuntosLicenciaCacheIntegrationIT.xml)
- [Log sanitizado de la prueba Redis](ejecuciones/20261116-013654/backend/redis-real/maven-redis-opt-in.txt)
- [Primer intento de invocación, sin pruebas ejecutadas](ejecuciones/20261116-013654/backend/redis-real/maven-redis-opt-in-attempt-1.txt)
- [Versión y condiciones de Redis](ejecuciones/20261116-013654/backend/redis-real/redis-version.txt)
- [Verificación de cierre del contenedor Redis](ejecuciones/20261116-013654/backend/redis-real/redis-container-lifecycle.txt)
- [Verificación del contenedor E2E Redis](ejecuciones/20261116-013654/frontend/e2e-ant-redis/container-lifecycle-verification.txt)
- [Resumen de pruebas frontend](ejecuciones/20261116-013654/frontend/execution-summary.txt)
- [Logs frontend sanitizados](ejecuciones/20261116-013654/frontend/)
- [Reportes Playwright E2E SRI](ejecuciones/20261116-013654/frontend/e2e-sri/)
- [Reportes y capturas Playwright ANT simulado](ejecuciones/20261116-013654/frontend/e2e-ant/)
- [Reporte y capturas Playwright ANT + Redis](ejecuciones/20261116-013654/frontend/e2e-ant-redis/)
- [Manifiesto de grupos PNG duplicados y copias canónicas](image-duplicates.txt)

Las copias canónicas de capturas repetidas están bajo la ejecución fechada
`20261116-013654`; los originales duplicados se conservaron localmente y no se
incluyen como copias idénticas adicionales. Las capturas fixture de UI siguen
siendo fixtures. Las dos capturas de `e2e-ant-redis` corresponden al recorrido
de Playwright contra Redis local real y el simulador, nunca a ANT real. Los
reporte JSON anteriores contienen una secuencia numérica dentro del ID opaco de
Playwright; no corresponde a una identificación de usuario.

## Qué demuestra cada prueba

| Prueba | Infraestructura y datos | Resultado | Alcance |
|---|---|---|---|
| `RedisPuntosLicenciaCacheIntegrationIT` — `writesAndReadsJsonWithExpiryAndOriginalTimestamp` | Redis 7.4.12 real, Lettuce y fixture sintético; no usa mocks para Redis. | Aprobada: escribe y lee JSON, mantiene `fetchedAt`, fuente y TTL positivo. | Comprueba el adaptador Redis local; no consulta ANT. |
| `RedisPuntosLicenciaCacheIntegrationIT` — `realRedisSupportsUseCaseSuccessThenTimeoutAndSimulationCacheFallback` | Redis 7.4.12 real y proveedor `SUCCESS_THEN_TIMEOUT` simulado. El proveedor no usa red. | Aprobada: `SIMULATED` se guarda y, tras timeout simulado, vuelve como `CACHE`, con `originSource=SIMULATED`, fecha y total originales. | Comprueba el caso de uso con almacenamiento Redis real, no una falla de ANT real. |
| Pruebas unitarias Redis/Spring del backend | `RedisPuntosLicenciaCacheAdapterTests` usa Mockito para Redis; pruebas de servicio/configuración usan fakes o contextos Spring de prueba. | No son las dos pruebas de Redis real de esta ejecución. | Verifican aislamiento, errores, validación de datos y configuración sin un Redis externo. |
| `npm test` y `npm run test` con reporter JUnit | Vitest, jsdom, React Testing Library y fixtures sintéticos. | 33 aprobadas en cada invocación; 0 fallos. | No llaman a servicios externos ni Redis. |
| `npm run lint` / `npm run build` | Herramientas locales del frontend. | Ambos aprobados. El bundle normal no contiene los marcadores revisados de la demo ANT. | La build no demuestra disponibilidad de un proveedor. |
| `npm run test:e2e` | Playwright, React/Vite, Spring Boot y stub SRI local. | 3 aprobadas. | Verifica el flujo E2E SRI con fixture; no consulta SRI real. |
| `npm run test:e2e:ant` | Playwright, React/Vite, Spring Boot y simulador local en loopback; el guard SRI recibió cero solicitudes. | 7 aprobadas; las capturas canónicas más recientes están en `ejecuciones/20261116-013654/frontend/e2e-ant/`. | No usa Redis en esta ejecución y no consulta ANT. |

## Seguridad de la evidencia

Los logs y reportes publicados se sanitizaron para retirar rutas absolutas del workspace y del perfil local; se preservaron los resultados, contadores, nombres de prueba y estados. Las copias originales se conservaron fuera del repositorio.
El reporte JSON de Playwright incluye una secuencia numérica dentro de su ID opaco
generado automáticamente. Se revisó su contexto: es metadata del caso de prueba,
no una cédula ni información personal del usuario, por lo que se conserva intacta.
Los fixtures HMAC son sintéticos. No se encontraron credenciales operativas, tokens,
claves HMAC reales ni identificaciones personales en los archivos publicables.

## Limitaciones

- No existe una integración ANT real ni una respuesta funcional del proveedor
  verificada.
- El timeout de este recorrido lo genera el simulador, sin espera ni red.
- Redis Cloud no se configuró ni probó.
- El resultado `SIMULATED` y su recuperación desde Redis no son datos oficiales.
