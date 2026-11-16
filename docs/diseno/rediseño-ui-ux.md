# Rediseño editorial de Consultas SRI · ANT

## Dirección visual

Esta iteración reemplaza la dirección azul/teal de la interfaz anterior por una identidad cálida y editorial: papel marfil, titulares serif, texto sans-serif, acentos terracota y divisores finos. La marca identifica un proyecto académico independiente; no usa escudos, logotipos ni recursos que impliquen afiliación con una entidad pública.

El diseño usa el paisaje andino como fotografía principal y deja que los formularios y los informes de resultados ocupen el centro de la experiencia. El catálogo evita una cuadrícula de tarjetas repetida y organiza cada operación como una entrada editorial con su alcance y acceso.

## Problemas que aborda

- La versión anterior parecía un portal SaaS por la repetición de tarjetas, insignias de estado y superficies anidadas.
- La paleta azul/teal y los números grandes de etapa aparecían con más peso visual que la información consultada.
- El catálogo agrupaba contribuyente y vehículo, por lo que no explicaba de forma directa las tres operaciones SRI que recorre el backend.
- La vista de simulación ANT mostraba solo respuestas locales exitosas; no diferenciaba el estado `CACHE` del contrato de resultados.

## Sistema visual

### Colores

| Token | HEX | Uso |
|---|---|---|
| Papel | `#F6F2EB` | Fondo general cálido |
| Papel profundo | `#EEE8DF` | Pie de página y superficies de apoyo |
| Superficie | `#FFFEFA` | Formularios e informes |
| Carbón | `#302C29` | Texto principal |
| Carbón suave | `#5C554E` | Texto secundario |
| Línea | `#DDD5CB` | Separadores finos |
| Terracota | `#A64E3C` | Acciones principales y énfasis |
| Rojo oscuro | `#6C332F` | Marca, foco y enlaces de acción |
| Ámbar | `#B77A3D` | Acento moderado para la caché |

Los tokens se definen en `frontend/src/index.css`. El ámbar no domina la página. El verde se reserva para indicar una consulta completada.

### Tipografía, escala y espaciado

- Titulares y nombres de secciones: Georgia, con alternativas serif locales.
- Formularios, ayudas y datos: Aptos/Segoe UI/system-ui.
- No se cargan fuentes remotas ni se añadió una dependencia tipográfica.
- Anchos de contenido centrados hasta 1240 px, con márgenes que se reducen en tablet y móvil.
- Escala de espaciado basada en múltiplos de 4 px; controles de al menos 49 px de alto y enlaces con áreas táctiles mínimas.
- Los radios se limitan a campos y pequeños controles. La mayoría de las secciones usan bordes rectos y separadores.
- Sombras únicamente en la vista aislada de demo, con intensidad baja; las secciones normales descansan sobre líneas y contraste de superficies.

## Fotografía y recursos

Se conservan los WebP locales existentes y las atribuciones documentadas en [recursos-visuales.md](recursos-visuales.md): carretera andina como protagonista del hero, documentos y computador para el contexto de contribuyentes, y automóvil en carretera para el contexto vehicular. No se descargaron imágenes nuevas ni se cargan fotos externas durante la ejecución.

El hero usa la variante móvil en pantallas estrechas y prioriza la carga de la imagen principal. Las fotos del catálogo son secundarias y usan carga diferida. No se representan edificios oficiales ni se sugieren avales institucionales.

## Estructura de la página y componentes

- `SiteHeader`: marca tipográfica, navegación por anclas y menú móvil existente.
- `HeroSection`: texto editorial, CTA a `#consulta` y fotografía andina local.
- `ServicesSection`: directorio de cuatro operaciones: existencia de RUC, detalles del contribuyente, vehículo por placa y puntos ANT.
- `AntStatusCard`: explica que la integración real ANT está temporalmente pendiente, mantiene el enlace oficial y ofrece el acceso local solo cuando la demo está habilitada.
- `ConsultationFlow` y `ConsultationProgress`: conservan únicamente las dos etapas SRI, contribuyente y vehículo. La etapa vehicular sigue condicionada a confirmar persona natural.
- `TaxpayerResult` y `VehicleResult`: conservan los campos recibidos por el backend; el formato visual se organiza como informe con separadores, sin repetir el RUC ni afirmar titularidad vehicular.
- `AntPointsDemo`: vista local separada, restringida por la condición de desarrollo y `VITE_ANT_DEMO=true`.

La verificación de existencia y la obtención de detalles son operaciones del flujo de contribuyente; el catálogo las explica por separado sin introducir nuevas llamadas desde el navegador. Los tres endpoints SRI y los contratos REST no cambian.

## Estados de resultado y ANT

La demo acepta el contrato normalizado `data.totalPuntos` y metadata de fuente. `SIMULATED` se presenta como resultado local, con aviso permanente de no oficial. Un saldo cero sigue siendo un resultado y se diferencia de `404 NOT_FOUND`, que se presenta como ausencia de información. Errores 503, 504 y 502 mantienen estados separados y nunca se transforman en cero.

La vista puede representar `source=CACHE` solamente si la metadata también informa `stale=true`, `fetchedAt` y un aviso. En ese estado muestra el resultado más reciente y la fecha original. Un error 503 informa que no se recuperó un resultado previo desde caché; no lo transforma en saldo cero. La captura `CACHE` de la suite `test:e2e:ant` usa un fixture de interfaz y solo verifica la presentación. Por separado, `test:e2e:ant:redis` recorre la simulación y Redis local real, sin consultar ANT.

La consulta ANT real continúa pendiente. El enlace al trámite de certificado de conductor corresponde a un trámite web y no se presenta como API. Ningún resultado sintético se representa como información oficial.

## Accesibilidad, interacción y responsive

- Se conservan el enlace para saltar al contenido, los anclajes principales, las etiquetas de campo, la navegación de teclado, Escape en el menú y la devolución del foco.
- El progreso mantiene dos etapas y usa un punto de estado/check en vez de numerales decorativos.
- Los mensajes de error y resultados conservan roles accesibles y el `requestId` seguro.
- El foco visible usa terracota oscuro y se respeta `prefers-reduced-motion`.
- El menú móvil se abre y cierra con transición discreta.
- Se comprueba overflow horizontal y el flujo en 320, 390, 768, 1280 y 1440 px mediante Playwright.

Las pruebas automatizadas no sustituyen una revisión manual con lectores de pantalla ni constituyen una certificación WCAG.

## Evidencias visuales

Las capturas enlazadas se generaron el 16 de noviembre de 2026 con el stub SRI local, el simulador local o respuestas sintéticas interceptadas en Playwright. No se realizaron consultas externas. Se escogieron como copias canónicas las imágenes de la ejecución fechada; los PNG idénticos que existían en carpetas anteriores se conservaron localmente y no se publican duplicados. El [manifiesto de hashes](../pruebas/evidencias-ant-redis/image-duplicates.txt) registra las coincidencias.

| Archivo | Contenido | Fuente de la evidencia |
|---|---|---|
| [01-portada-escritorio.png](../pruebas/evidencias-ant-redis/ejecuciones/20261116-013654/frontend/e2e-sri/01-portada-escritorio.png) | Portada editorial en escritorio | Frontend y recursos locales |
| [02-portada-movil.png](../pruebas/evidencias-ant-redis/ejecuciones/20261116-013654/frontend/e2e-sri/02-portada-movil.png) | Portada en 390 px | Frontend y recursos locales |
| [03-catalogo-servicios.png](../pruebas/evidencias-ant-redis/ejecuciones/20261116-013654/frontend/e2e-sri/03-catalogo-servicios.png) | Catálogo y estado pendiente de ANT | Frontend y recursos locales |
| [04-formulario-contribuyente.png](../pruebas/evidencias-ant-redis/ejecuciones/20261116-013654/frontend/e2e-sri/04-formulario-contribuyente.png) | Formulario sintético de correo y RUC | Stub SRI aislado |
| [05-resultado-contribuyente.png](../pruebas/evidencias-ant-redis/ejecuciones/20261116-013654/frontend/e2e-sri/05-resultado-contribuyente.png) | Informe sintético de contribuyente | Stub SRI aislado |
| [06-formulario-vehicular.png](../pruebas/evidencias-ant-redis/ejecuciones/20261116-013654/frontend/e2e-sri/06-formulario-vehicular.png) | Formulario por placa tras validar contribuyente | Stub SRI aislado |
| [07-resultado-vehicular.png](../pruebas/evidencias-ant-redis/ejecuciones/20261116-013654/frontend/e2e-sri/07-resultado-vehicular.png) | Informe sintético de vehículo | Stub SRI aislado |
| [08-error-proveedor.png](../pruebas/evidencias-ant-redis/ejecuciones/20261116-013654/frontend/e2e-sri/08-error-proveedor.png) | Error SRI con mensaje seguro | Stub SRI aislado |
| [09-estado-ant.png](../pruebas/evidencias-ant-redis/ejecuciones/20261116-013654/frontend/e2e-sri/09-estado-ant.png) | Integración ANT pendiente y trámite oficial | Frontend |
| [09-ant-simulado.png](../pruebas/evidencias-ant-redis/ejecuciones/20261116-013654/frontend/e2e-ant/09-ant-simulado.png) | Resultado `SIMULATED` local | Backend de simulación en loopback |
| [10-ant-cero-puntos.png](../pruebas/evidencias-ant-redis/ejecuciones/20261116-013654/frontend/e2e-ant/10-ant-cero-puntos.png) | Cero como resultado simulado | Fixture de interfaz |
| [11-ant-cache-fixture-interfaz.png](../pruebas/evidencias-ant-redis/ejecuciones/20261116-013654/frontend/e2e-ant/11-ant-cache-fixture-interfaz.png) | `CACHE`, fecha original y warning | Fixture de interfaz; no prueba Redis |
| [12-ant-sin-informacion.png](../pruebas/evidencias-ant-redis/ejecuciones/20261116-013654/frontend/e2e-ant/12-ant-sin-informacion.png) | Ausencia sin saldo | Fixture de interfaz |
| [13-ant-servicio-indisponible-fixture.png](../pruebas/evidencias-ant-redis/ejecuciones/20261116-013654/frontend/e2e-ant/13-ant-servicio-indisponible-fixture.png) | Error 503 controlado | Fixture de interfaz |
| [14-ant-timeout-fixture.png](../pruebas/evidencias-ant-redis/ejecuciones/20261116-013654/frontend/e2e-ant/14-ant-timeout-fixture.png) | Error 504 controlado | Fixture de interfaz |
| [15-ant-respuesta-invalida-fixture.png](../pruebas/evidencias-ant-redis/ejecuciones/20261116-013654/frontend/e2e-ant/15-ant-respuesta-invalida-fixture.png) | Error 502 controlado | Fixture de interfaz |
| [ant-redis-simulated-success.png](../pruebas/evidencias-ant-redis/ejecuciones/20261116-013654/frontend/e2e-ant-redis/ant-redis-simulated-success.png) | Primera consulta y escritura simulada | Playwright con Redis 7.4.12 local real; simulador como proveedor |
| [ant-redis-cache-recovery.png](../pruebas/evidencias-ant-redis/ejecuciones/20261116-013654/frontend/e2e-ant-redis/ant-redis-cache-recovery.png) | Resultado recuperado con `CACHE`, origen `SIMULATED` y stale | Playwright con Redis 7.4.12 local real; no verifica fallo ANT real |

Las capturas responsive adicionales de 320, 768, 1280 y 1440 px están en el
[subdirectorio de tamaños](../pruebas/evidencias-ant-redis/ejecuciones/20261116-013654/frontend/e2e-sri/flujo-sri/). La portada a 390 px usa el enlace canónico de arriba; no se publica una segunda copia idéntica.

Las capturas históricas de `evidencias-ui/rediseño-final/` se conservan como registro de la iteración anterior.

## Límites y pruebas

- La disponibilidad y los datos de contribuyentes/vehículos dependen del proveedor SRI; los fixtures E2E verifican el flujo, no la disponibilidad real del SRI.
- No existe una consulta automática ANT real ni una respuesta funcional verificada.
- La simulación ANT solo está disponible en desarrollo con configuración explícita.
- El fixture `CACHE` comprueba la presentación frontend; por separado, la captura E2E ant + Redis demuestra la recuperación web usando Redis local real y el simulador. Ninguna de las dos verifica disponibilidad ni recuperación ante ANT real.
- Ningún endpoint, contrato API ni regla de negocio cambió en este rediseño.
