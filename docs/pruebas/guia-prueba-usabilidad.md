# Guía para la prueba de usabilidad

## Propósito

Observar si una persona puede localizar y comprender el recorrido de consultas sin recibir instrucciones sobre dónde hacer clic. La sesión debe evaluar la interfaz, no pedirle al participante que use identificaciones reales ni presentar resultados sintéticos como información institucional.

## Preparación

- Realizar la sesión en una copia local del proyecto, con datos de prueba y servicios stub/simulados. No ejecutar tareas contra proveedores externos.
- Activar la demostración ANT únicamente en Vite de desarrollo mediante `VITE_ANT_DEMO=true`; el backend simulado debe usar el perfil local documentado.
- Verificar previamente que los fixtures sean sintéticos. Si el entorno no está aislado de SRI/ANT reales, detener la sesión antes de enviar formularios.
- Para recorrer **toda la aplicación** con las consultas SRI aisladas, preparar el JAR y usar `npm run usability:session` desde `frontend/`. Ese runner inicia el stub SRI, Spring Boot con los perfiles `e2e,local-simulation` y Vite con la demo habilitada; no ejecuta Playwright automáticamente. No usar para una sesión completa las instrucciones ANT de dos terminales de `demo-ant-local.md`, pues ese procedimiento aislado no configura el guard SRI.
- Abrir únicamente la URL loopback que imprime el runner. En el modo aislado `VITE_E2E`, la interfaz desactiva el enlace al trámite oficial externo; el flujo evaluado debe permanecer dentro de la aplicación local.
- Explicar que la participación es voluntaria, pedir autorización para tomar notas y no grabar pantalla o voz sin consentimiento explícito.
- No registrar nombres, correos, cédulas, placas, direcciones ni otros datos personales. Asignar un código anónimo solo al iniciar la sesión.

### Iniciar el entorno interactivo aislado

Si el JAR o las dependencias aún no existen, prepararlos antes de convocar al participante:

```powershell
Set-Location .\backend
.\mvnw.cmd -B -DskipTests package
Set-Location ..\frontend
npm install
```

En una terminal PowerShell dedicada, iniciar el runner y mantener esa terminal abierta durante la sesión:

```powershell
Set-Location .\frontend
npm run usability:session
```

El runner asigna puertos loopback disponibles e imprime la URL exacta, el estado del backend y los cuatro fixtures sintéticos. El participante puede manejar el navegador manualmente; no se lanza Playwright. Antes de arrancar Spring Boot, el runner copia el JAR ejecutable a un directorio temporal propio para evitar conflictos si una suite Maven reconstruye `backend/target` durante la sesión. Para terminar, presionar `Ctrl+C` en esa misma terminal. El runner cierra solamente el stub, los procesos Spring Boot/Vite que inició y la copia temporal que creó.

Fixtures permitidos para esta sesión: correo `e2e-user@example.invalid`, RUC `1234567890123`, placa `E2E1234` y cédula ANT sintética `0000000000`. No ingresar datos personales.

## Perfil general del participante

Registrar solo información necesaria para interpretar la prueba, sin identificar a la persona:

- Código anónimo: ____________________
- Familiaridad general con trámites web: ☐ Baja ☐ Media ☐ Alta ☐ Prefiere no responder
- Uso habitual de computadora o móvil: ☐ Computadora ☐ Móvil ☐ Ambos ☐ Prefiere no responder
- Dispositivo y tamaño de pantalla usados: ____________________
- Fecha de la sesión: ____________________
- Facilitador/a: código o iniciales acordadas: ____________________
- Consentimiento para participar y tomar notas: ☐ Sí ☐ No (si marca No, no iniciar)

## Instrucciones para quien facilita

1. Leer la tarea sin explicar la navegación ni el significado de controles.
2. Iniciar el cronómetro cuando termine de leerla y detenerlo cuando el participante diga que terminó o abandone la tarea.
3. No sugerir acciones. Si la persona pide ayuda, anotar qué necesitó y permitir que continúe; marcar la tarea como asistida.
4. Registrar errores observables, dudas y comentarios con sus palabras, anonimizados.
5. Al finalizar cada tarea, preguntar: “¿Qué esperabas que ocurriera?” y “¿Qué parte te resultó menos clara?”.
6. No ingresar datos personales. Usar exclusivamente los fixtures indicados por el entorno de pruebas.

## Tareas

| N.º | Tarea para leer al participante | Tiempo máximo sugerido |
|---:|---|---:|
| 1 | “Desde la página principal, encuentra dónde se inicia la consulta de un contribuyente.” | 2 min |
| 2 | “Completa el formulario de contribuyente usando los datos sintéticos que te entrega la persona facilitadora y envíalo.” | 3 min |
| 3 | “Revisa el resultado y explica con tus palabras qué información confirma y qué no confirma.” | 3 min |
| 4 | “Continúa hasta la consulta vehicular, encuentra el campo para la placa y revisa el resultado de prueba.” | 3 min |
| 5 | “Encuentra la sección ANT, indica si la consulta real está disponible y abre la demostración local.” | 3 min |

Si se alcanza el tiempo máximo, detener el cronómetro y registrar la tarea como incompleta. El resultado esperado de la quinta tarea es que la persona distinga el trámite oficial del simulador y reconozca que `SIMULATED` no es información oficial.

## Registro de cada tarea

Duplicar esta ficha para las tareas 1–5. No completar campos con suposiciones.

**Tarea n.º:** ________  **Inicio:** ________  **Fin:** ________  **Duración:** ________

- Resultado: ☐ Completada sin ayuda ☐ Completada con ayuda ☐ Parcial ☐ No completada
- Errores o pasos repetidos observados: __________________________________________
- Dudas o pausas observadas: __________________________________________________
- Ayuda solicitada o entregada: _________________________________________________
- Comentario textual anonimizado del participante: ________________________________
- Nota del facilitador: _________________________________________________________

## Cierre de la sesión

- ¿Qué parte le resultó más clara?: ______________________________________________
- ¿Qué parte le resultó más difícil?: _____________________________________________
- ¿Qué cambiaría primero?: _____________________________________________________
- Mejoras propuestas por el equipo, basadas en observaciones: ______________________
- Observaciones adicionales: ___________________________________________________

## Resumen de resultados

Completar únicamente después de realizar una sesión real. Mientras no se ejecute, el estado de esta prueba es **pendiente**.

| Métrica | Resultado observado |
|---|---|
| Participantes reales | Pendiente |
| Tareas completadas sin ayuda | Pendiente |
| Tareas completadas con ayuda | Pendiente |
| Tareas parciales o no completadas | Pendiente |
| Dificultad recurrente | Pendiente |
| Mejora prioritaria | Pendiente |

## Evidencia y privacidad

Guardar este documento después de una sesión solo si las notas están anonimizadas. No adjuntar grabaciones, capturas o datos que identifiquen al participante. La guía no contiene resultados de participantes ni tiempos observados.
