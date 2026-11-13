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

El backend incluye:
- API REST y validación
- Casos de uso
- Adaptadores SRI
- Adaptador ANT
- Servicio de puntos resiliente
- Adaptador Redis

## Estado del proyecto

En desarrollo.

Las integraciones externas todavía requieren verificación
técnica y pruebas funcionales.

## Estructura prevista

- frontend/
- backend/
- docs/
