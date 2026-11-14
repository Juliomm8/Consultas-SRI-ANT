# Mapeo de componentes C4 a paquetes Java

Este mapeo corresponde a los componentes C4 descritos para Consultas SRI ANT. Los paquetes marcados como futuros son destinos lógicos; no representan funcionalidades ya implementadas. El diseño de IcePanel no se modifica.

| Componente C4 Nivel 3 | Ubicación Java prevista | Estado en este bloque |
|---|---|---|
| Aplicación backend Spring Boot, desplegable como una unidad | `ec.edu.udla.consultas` y `ConsultasApplication` | Base creada en el Bloque 1. |
| Adaptador REST de entrada de contribuyentes | `ec.edu.udla.consultas.contribuyentes.adapter.in.rest` | Futuro; no hay controlador funcional. |
| DTO de entrada de contribuyentes | `ec.edu.udla.consultas.contribuyentes.adapter.in.rest.dto` | Se crea en este bloque. |
| Adaptador REST de entrada de vehículos | `ec.edu.udla.consultas.vehiculos.adapter.in.rest` | Futuro; no hay controlador funcional. |
| DTO de entrada de vehículos | `ec.edu.udla.consultas.vehiculos.adapter.in.rest.dto` | Se crea en este bloque. |
| Adaptador REST de entrada de puntos de licencia | `ec.edu.udla.consultas.puntos.adapter.in.rest` | Futuro; no hay controlador funcional. |
| DTO de entrada de puntos de licencia | `ec.edu.udla.consultas.puntos.adapter.in.rest.dto` | Se crea en este bloque. |
| Manejo transversal de errores y request IDs | `ec.edu.udla.consultas.shared.api` | Implementado en este bloque. |
| Casos de uso de contribuyentes | `ec.edu.udla.consultas.contribuyentes.application` | Futuro; no se implementa en este bloque. |
| Puertos de contribuyentes | `ec.edu.udla.consultas.contribuyentes.application.port.in` y `ec.edu.udla.consultas.contribuyentes.application.port.out` | Futuro; no se implementan en este bloque. |
| Casos de uso de vehículos | `ec.edu.udla.consultas.vehiculos.application` | Futuro; no se implementa en este bloque. |
| Puertos de vehículos | `ec.edu.udla.consultas.vehiculos.application.port.in` y `ec.edu.udla.consultas.vehiculos.application.port.out` | Futuro; no se implementan en este bloque. |
| Caso de uso de puntos | `ec.edu.udla.consultas.puntos.application` | Futuro; no se implementa en este bloque. |
| Puertos de puntos | `ec.edu.udla.consultas.puntos.application.port.in` y `ec.edu.udla.consultas.puntos.application.port.out` | Futuro; no se implementan en este bloque. |
| Adaptador saliente SRI de contribuyentes | `ec.edu.udla.consultas.contribuyentes.adapter.out.sri` | Futuro; la integración SRI no está implementada. |
| Adaptador saliente SRI de vehículos | `ec.edu.udla.consultas.vehiculos.adapter.out.sri` | Futuro; la integración SRI no está implementada. |
| Adaptador saliente ANT y protección de resiliencia | `ec.edu.udla.consultas.puntos.adapter.out.ant` | Futuro; ANT y Resilience4j no están implementados. |
| Adaptador Redis para caché ANT | `ec.edu.udla.consultas.puntos.adapter.out.cache.redis` | Futuro; Redis no está configurado. |
| Frontend React | `frontend/` | Cliente separado; no pertenece al paquete Java ni llama directamente a SRI, ANT o Redis. |

Los nombres `application`, `adapter.in` y `adapter.out` expresan la separación hexagonal ligera: los casos de uso dependerán de puertos y los adaptadores externos los implementarán. Los destinos pueden detallarse cuando se implementen esos componentes, manteniendo el backend como una sola aplicación desplegable y los módulos como límites lógicos.
