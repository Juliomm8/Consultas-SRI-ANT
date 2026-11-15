# Consultas SRI ANT

Sistema web académico desarrollado para la asignatura
Diseño y Arquitectura de Software.

## Objetivo

Permitir consultas de contribuyentes y vehículos del SRI
y puntos de licencia de la ANT mediante una aplicación web.

## Tecnologías

- React + TypeScript + Vite
- Java + Spring Boot
- Redis Cloud
- Resilience4j
- Maven
- Git y GitHub
- IcePanel para los diagramas C4

## Arquitectura

Cliente-servidor con un backend Java modular y arquitectura
hexagonal ligera basada en puertos y adaptadores.

El backend expone consultas REST de contribuyentes y vehículos del SRI. El
frontend React se comunica únicamente con ese backend.

## Estado del proyecto

Las consultas de contribuyentes y vehículos SRI están implementadas. La
integración de puntos de licencia ANT continúa pendiente: durante las pruebas
el portal oficial respondió HTTP 502 Bad Gateway y no se obtuvo una respuesta
funcional que permita definir un adaptador seguro. Resilience4j tiene una
configuración preparada, pero aún no protege una llamada ANT real; Redis y el
fallback también siguen pendientes. No se presentan puntos simulados ni se
interpreta una caída del proveedor como cero puntos.

El diagnóstico técnico, la cronología de pruebas y sus limitaciones están en
[Estado de la integración ANT](docs/integraciones/diagnostico-endpoints.md#estado-de-la-integración-ant).

## Estructura prevista

- frontend/
- backend/
- docs/
