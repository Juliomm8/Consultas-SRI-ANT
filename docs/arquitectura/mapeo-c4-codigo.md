# Mapeo de componentes C4 a paquetes Java

Este mapeo corresponde a los componentes C4 descritos para Consultas SRI ANT. Los paquetes marcados como futuros son destinos lógicos; no representan funcionalidades ya implementadas. El diseño de IcePanel no se modifica.

| Componente C4 Nivel 3 | Ubicación Java prevista | Estado en este bloque |
|---|---|---|
| Aplicación backend Spring Boot, desplegable como una unidad | `ec.edu.udla.consultas` y `ConsultasApplication` | Base creada en el Bloque 1. |
| Adaptador REST de entrada de contribuyentes | `ec.edu.udla.consultas.contribuyentes.adapter.in.rest.ContribuyenteRestController` | Implementado: `POST /api/v1/contribuyentes/consultas`; email solo se valida. |
| DTOs de entrada y salida de contribuyentes | `ec.edu.udla.consultas.contribuyentes.adapter.in.rest.dto` | Implementados; la respuesta mapea el dominio y no devuelve el email. |
| Adaptador REST de entrada de vehículos | `ec.edu.udla.consultas.vehiculos.adapter.in.rest.VehiculoRestController` | Implementado: `POST /api/v1/vehiculos/consultas`; devuelve también listas vacías como HTTP 200. |
| DTOs de entrada y salida de vehículos | `ec.edu.udla.consultas.vehiculos.adapter.in.rest.dto` | Implementados; conversión de dominio a tipos JSON en el límite HTTP. |
| Adaptador REST de entrada de puntos de licencia | `ec.edu.udla.consultas.puntos.adapter.in.rest` | Futuro; el contrato `POST /api/v1/licencias/puntos/consultas` sigue como propuesta y no hay controlador funcional. |
| DTO de entrada de puntos de licencia | `ec.edu.udla.consultas.puntos.adapter.in.rest.dto` | Implementado como contrato de entrada, sin operación REST activa. |
| Respuestas comunes y metadata API | `ec.edu.udla.consultas.shared.api.ApiResponse` y `ApiMeta` | Implementadas para respuestas exitosas SRI, con `requestId`, `source` y `retrievedAt`. |
| Manejo transversal de errores y request IDs | `ec.edu.udla.consultas.shared.api` | Implementado: validación/JSON, ausencia confirmada, clasificación no natural o indeterminada, fallos SRI y error interno. |
| Dominio de contribuyentes | `ec.edu.udla.consultas.contribuyentes.domain.Contribuyente`, `TipoPersona` y `ValorCampoContribuyente` | Implementado: conserva propiedades SRI permitidas, tipos JSON y estructuras anidadas de forma inmutable; `toString()` redacta los datos. |
| Objeto de valor RUC | `ec.edu.udla.consultas.contribuyentes.domain.Ruc` | Implementado con validación estructural ASCII; sin dígito verificador. |
| Caso de uso de contribuyentes | `ec.edu.udla.consultas.contribuyentes.application.port.in.ConsultarContribuyenteUseCase` y `ec.edu.udla.consultas.contribuyentes.application.service.ConsultarContribuyenteService` | Implementado; consulta mediante el puerto de salida. |
| Puerto de entrada de contribuyentes | `ec.edu.udla.consultas.contribuyentes.application.port.in` | Implementado. |
| Puerto de salida de contribuyentes | `ec.edu.udla.consultas.contribuyentes.application.port.out.ContribuyenteConsultaPort` | Implementado; `Optional.empty()` representa ausencia confirmada por el proveedor. |
| Excepciones de aplicación de contribuyentes | `ec.edu.udla.consultas.contribuyentes.application.exception` | Implementadas; no están acopladas a HTTP. |
| Excepciones de dominio de contribuyentes | `ec.edu.udla.consultas.contribuyentes.domain.exception` | Implementadas; no están acopladas a HTTP. |
| Adaptador saliente SRI de contribuyentes | `ec.edu.udla.consultas.contribuyentes.adapter.out.sri.SriContribuyenteHttpAdapter` | Implementado; verifica existencia antes de pedir detalles, confirma que el RUC de respuesta coincida, mapea el valor confirmado `PERSONA NATURAL` a `NATURAL` y preserva los campos SRI observados permitidos. Los valores desconocidos, ausentes, nulos o no textuales quedan `NO_DETERMINADA`; no se infiere clasificación jurídica. |
| HTTP compartido para integraciones SRI | `ec.edu.udla.consultas.shared.integration.sri` | Implementado; `RestClient`, timeouts y excepciones técnicas sanitizadas. |
| Dominio de vehículos | `ec.edu.udla.consultas.vehiculos.domain` | Implementado; solo conserva campos SRI observados y tipos JSON escalares, sin datos de titularidad inferidos. |
| Caso de uso de vehículos | `ec.edu.udla.consultas.vehiculos.application.service.ConsultarVehiculoService` | Implementado; delega al puerto de salida y no agrega reglas de titularidad. |
| Puertos de vehículos | `ec.edu.udla.consultas.vehiculos.application.port.in` y `ec.edu.udla.consultas.vehiculos.application.port.out` | Implementados para consulta por identificador vehicular. |
| Adaptador saliente SRI de vehículos | `ec.edu.udla.consultas.vehiculos.adapter.out.sri.SriVehiculoHttpAdapter` | Implementado; mapea el objeto vehicular observado directamente en la raíz a una lista de un elemento. Conserva el envoltorio `data` como forma compatible; `data: []` fue observado en el caso negativo centinela y su semántica general no está confirmada. Las pruebas sintéticas cubren el objeto raíz, campos opcionales nulos, arreglo y respuestas inválidas. |
| Caso de uso de puntos | `ec.edu.udla.consultas.puntos.application` | Futuro; no se implementa en este bloque. |
| Puertos de puntos | `ec.edu.udla.consultas.puntos.application.port.in` y `ec.edu.udla.consultas.puntos.application.port.out` | Futuro; no se implementan en este bloque. |
| Protección ANT con circuit breaker | Configuración `resilience4j.circuitbreaker.instances.antPoints` en `application.yml` | Configuración instalada, aún no conectada a un adaptador o caso de uso ANT; las rutas oficiales probadas siguen devolviendo HTTP 502. |
| Adaptador saliente ANT y protección de resiliencia | `ec.edu.udla.consultas.puntos.adapter.out.ant` | Futuro; `-SoloANT` inspecciona las páginas de referencia, pero no se implementa el parser ni una llamada de producción porque no se observó formulario ni respuesta funcional. |
| Adaptador Redis para caché ANT | `ec.edu.udla.consultas.puntos.adapter.out.cache.redis` | Futuro; Redis no está configurado. |
| Frontend React | `frontend/` | Cliente separado; no pertenece al paquete Java ni llama directamente a SRI, ANT o Redis. |

Las rutas de consulta de contribuyentes y vehículos están implementadas en el adaptador REST. Sus pruebas HTTP son sintéticas; los adaptadores salientes se prueban con `MockRestServiceServer`, sin llamadas reales durante las pruebas automatizadas. El script `scripts/validar-casos-exitosos.ps1 -SoloANT` es una herramienta de diagnóstico, no un adaptador de la aplicación. La ruta de puntos ANT, su parser, el circuit breaker conectado a una llamada real y el respaldo Redis continúan futuros y no se consideran implementados.

Los nombres `application`, `adapter.in` y `adapter.out` expresan la separación hexagonal ligera: los casos de uso dependen de puertos y los adaptadores externos los implementan. Los adaptadores SRI no crean endpoints REST de entrada. El módulo de puntos y su caché continúan como futuros, manteniendo el backend como una sola aplicación desplegable y los módulos como límites lógicos.
