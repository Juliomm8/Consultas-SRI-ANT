# Consultas SRI ANT

Sistema web académico desarrollado para la asignatura
Diseño y Arquitectura de Software.

## Objetivo

Permitir consultas de contribuyentes y vehículos del SRI mediante una
aplicación web y orientar al usuario al trámite oficial de información del
conductor de la ANT. La consulta automática de puntos ANT todavía no está
integrada.

## Tecnologías

- React + TypeScript + Vite
- Java 17 + Spring Boot 3.5.16
- Maven Wrapper
- Resilience4j (el Circuit Breaker protege el proveedor simulado solo en perfil local; no hay llamada ANT real)
- Redis Cloud mediante Spring Data Redis (caché tipada configurable y fallback preparado en el caso de uso; desactivado por defecto y sin datos ANT reales)
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
backend y conectadas desde el frontend. La consulta automática ANT continúa
pendiente: no se localizó documentación oficial pública de una API de consulta
automática y el portal respondió HTTP 502 durante las pruebas, sin que se pueda
determinar qué componente de la ruta generó el error. La ANT dispone de un
[trámite oficial para emitir el certificado de información del conductor](https://www.gob.ec/ant/tramites/emision-certificado-informacion-conductor),
que es un flujo web para usuarios y no equivale por sí mismo a una API que esta
aplicación pueda consumir. El backend ahora define un contrato normalizado para
el total de puntos y permite probarlo con un simulador exclusivo del perfil
`local-simulation`, enlazado a `127.0.0.1`. Su fuente se identifica como
`SIMULATED`; no representa una consulta oficial. La interfaz normal conserva
su tarjeta informativa; existe además una vista de demostración opt-in solo en
Vite de desarrollo, protegida por `VITE_ANT_DEMO=true` y conectada al backend
local. El circuit breaker protege únicamente el límite simulado. La consulta
ANT real sigue pendiente. Redis conserva solo resultados de fuente `ANT`; el caso de uso
ya contempla el fallback a un resultado cacheado, pero no hay una integración
ANT real que pueda producir o validar esos resultados en esta versión. Ver
[modo de simulación y configuración Redis del backend](backend/README.md#simulación-local-de-puntos-ant).

La vista, los escenarios y las evidencias reproducibles de la demo local están
documentados en [Demo local de puntos ANT](docs/pruebas/demo-ant-local.md).

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
