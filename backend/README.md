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
