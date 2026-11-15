# Consultas SRI ANT

Sistema web académico desarrollado para la asignatura
Diseño y Arquitectura de Software.

## Objetivo

Permitir consultas de contribuyentes y vehículos del SRI
y puntos de licencia de la ANT mediante una aplicación web.

## Tecnologías

- React + TypeScript + Vite
- Java 17 + Spring Boot 3.5.16
- Maven Wrapper
- Resilience4j (configuración ANT preparada, aún no conectada a una llamada real)
- Redis Cloud (previsto; todavía no implementado)
- Git y GitHub
- IcePanel para los diagramas C4

## Arquitectura

Cliente-servidor con un backend Java modular y arquitectura
hexagonal ligera basada en puertos y adaptadores.

El backend es una sola aplicación modular Spring Boot con arquitectura
hexagonal ligera. Expone consultas REST de contribuyentes y vehículos del SRI;
el frontend React se comunica únicamente con esas rutas propias.

## Estado del proyecto

Las consultas de contribuyentes y vehículos SRI están implementadas en el
backend y conectadas desde el frontend. La consulta ANT continúa pendiente:
durante las pruebas el portal oficial respondió HTTP 502 Bad Gateway y no se
obtuvo una respuesta funcional que permita definir un adaptador seguro. La
configuración `antPoints` de Resilience4j aún no protege una llamada real;
Redis y el fallback siguen pendientes. No se presentan puntos simulados ni se
interpreta una caída del proveedor como cero puntos.

El diagnóstico técnico, la cronología de pruebas y sus limitaciones están en
[Estado de la integración ANT](docs/integraciones/diagnostico-endpoints.md#estado-de-la-integración-ant).

## Ejecución local

Consulta los requisitos, comandos de inicio y pruebas en los README de
[frontend](frontend/README.md) y [backend](backend/README.md). El desarrollo
local usa el proxy de Vite hacia el backend en `http://localhost:8080`.

## Estructura

- frontend/
- backend/
- docs/
