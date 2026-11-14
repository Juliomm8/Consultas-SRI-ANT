# Backend — Consultas SRI ANT

## Requisitos

- JDK 17.
- Maven Wrapper incluido; no se requiere Maven global.

## Ejecutar localmente

Desde `backend/`:

```powershell
.\mvnw.cmd spring-boot:run
```

En Linux o macOS:

```bash
./mvnw spring-boot:run
```

La aplicación escucha en el puerto `8080`. Actuator expone únicamente
`/actuator/health` para comprobar el estado.

## Verificar

```powershell
.\mvnw.cmd -B clean verify
```

El backend inicia sin credenciales ni servicios externos.

## Integraciones externas

Los adaptadores salientes de contribuyentes y vehículos SRI están implementados detrás de sus puertos. Utilizan HTTP `RestClient` con timeout de conexión de 3 segundos y lectura de 5 segundos. No hay todavía controladores funcionales de consulta.

La respuesta SRI de contribuyentes se conserva con tipo de persona `NO_DETERMINADA` hasta confirmar los valores de `tipoContribuyente`; por eso el caso de uso actual no acepta esa clasificación como persona natural. El adaptador vehicular solo mapea los campos observados y no prueba titularidad.

La configuración Resilience4j `antPoints` está preparada, pero aún no protege una llamada ANT. No existe un resultado ANT funcional confirmado, parser, caché Redis ni fallback implementado.
