# Rediseño UI/UX — Consultas SRI ANT

## Dirección del diseño

La aplicación conserva su propósito de servicio y su flujo existente. La nueva dirección visual combina un portal de servicios digitales con una composición editorial sobria: azul profundo para identidad y confianza, teal para acciones y estados confirmados, y dorado suave para acentos y llamadas principales. La marca identifica un proyecto académico independiente y no imita una institución pública.

## Observaciones de la interfaz anterior

- La portada y el flujo de consulta tenían una jerarquía similar, con poca diferenciación entre presentación, orientación y captura de datos.
- El hero era un bloque de color sin fotografía ni un acceso principal al formulario.
- Los servicios SRI y el estado ANT se entendían principalmente por texto dentro de paneles con tratamiento visual parecido.
- Los formularios y resultados utilizaban componentes funcionales, pero no tenían una estructura de informe ni una guía visual de progreso suficientemente marcada.
- En móvil, la portada ocupaba mucho espacio antes de llegar a la consulta y la navegación no ofrecía una forma compacta de moverse entre secciones.

## Sistema visual

- **Paleta:** azul tinta `#102A43`, teal `#146C76`, dorado suave `#E7AA65`, fondo claro `#F5F7F5`, superficies blancas y grises verdosos suaves.
- **Tipografía:** pila local `Inter, Aptos, Segoe UI, system-ui`; no se requiere una descarga de fuentes externa.
- **Jerarquía:** títulos editoriales con contraste de escala; encabezados pequeños y espaciados para identificar secciones; etiquetas cortas para procedencia y estado.
- **Espaciado:** escala CSS de 4 a 80 px; áreas de contenido centradas y controles con altura táctil cómoda.
- **Formas:** radios moderados en paneles y controles, radios mayores en secciones de portada, bordes reservados para separar grupos y estados.
- **Sombras:** solo en elementos elevados —hero, resultados y paneles principales— con intensidad baja.

Los tokens están definidos en `frontend/src/index.css` y los componentes de página en `frontend/src/App.css`. Los iconos SVG de línea son locales y decorativos para accesibilidad; los controles conservan texto visible.

## Estructura y jerarquía

1. **Header:** marca propia, etiqueta académica, navegación por anclas y CTA. En móvil, la navegación abre un menú controlable por teclado.
2. **Hero fotográfico:** carretera andina local en WebP, overlay oscuro, titular principal, CTA hacia el formulario y nota de alcance.
3. **Servicios:** dos consultas SRI con fotografías contextualizadas y el componente `AntStatusCard` como tercer elemento, destacado como pendiente.
4. **Consulta:** el flujo SRI tiene dos etapas numeradas: contribuyente y vehículo. El vehículo aparece después de confirmar una persona natural. ANT es un servicio independiente del catálogo y no es una etapa obligatoria.
5. **Ayuda contextual:** pasos siguientes, aviso de privacidad, aclaración sobre titularidad y disponibilidad externa.
6. **Resultados:** clasificación confirmada, referencia de solicitud, metadata, campos JSON conservados y tratamiento explícito de lista vehicular vacía.

La demo ANT permanece en su vista independiente existente. Solo aparece durante desarrollo cuando se habilita `VITE_ANT_DEMO=true`; siempre identifica los datos como simulados y no oficiales.

## Mejoras de UX

- El CTA del hero y el header llevan al formulario mediante el ancla `#consulta`.
- El indicador de progreso muestra únicamente las dos etapas SRI: contribuyente y vehículo. La tarjeta ANT permanece en el catálogo como integración pendiente, fuera del progreso.
- Un enlace de salto lleva al contenido principal; el enlace «Volver arriba» apunta al inicio real de la página.
- Los campos obligatorios tienen semántica HTML `required`, sin retirar la validación personalizada ni `noValidate`.
- Los errores de entrada se muestran junto al control y enfocan el primer campo que necesita corrección.
- Tras una consulta exitosa o un error de servidor, el resultado o aviso recibe foco para facilitar el seguimiento con teclado y lector de pantalla.
- Se conservan los valores escritos ante un error de red; empezar una consulta nueva es una acción explícita.
- La placa se muestra en mayúsculas mediante CSS, mientras el valor transmitido conserva exactamente lo escrito por el usuario.
- Una respuesta de vehículos `data: []` continúa indicando que no se recibió información, sin asegurar inexistencia.
- El estado ANT incluye el enlace oficial al trámite de certificado, aclara que no es una API y no solicita identificaciones ni presenta puntos ficticios.

### Interpretación de errores HTTP

- Un `404` con el sobre contractual y código `NOT_FOUND` representa información no encontrada.
- Un `404` sin un código contractual reconocido se presenta como un endpoint no disponible; no se interpreta como ausencia del contribuyente.
- Los `400` se presentan como errores de validación y los `422` como una condición de negocio no satisfecha.
- Los `502` y `503` indican que el proveedor no pudo atender la consulta; `504` y el timeout local muestran un mensaje específico de espera agotada.
- Los errores de conexión y las respuestas inválidas del servidor tienen mensajes seguros. No se muestran cuerpos técnicos, URLs internas ni trazas; el `requestId` se conserva cuando está disponible.

## Fotografía y rendimiento

Las imágenes se descargaron de sus páginas de fuente con licencia de uso compatible y se guardan dentro de `frontend/public/images/` en WebP. El hero tiene una variante panorámica y otra para pantallas estrechas; las tarjetas declaran `srcSet`, `sizes` y `loading="lazy"`. La imagen principal usa `loading="eager"` y `fetchPriority="high"`. La atribución y las páginas de licencia están en [recursos-visuales.md](recursos-visuales.md).

No hay dependencias de imágenes remotas durante la ejecución. Si una imagen secundaria no carga, el fondo neutro de su área mantiene el contraste y la estructura; el texto alternativo sigue disponible para tecnologías asistivas.

## Accesibilidad y responsive

- Navegación, secciones, pasos, formularios, listas de definición, alertas y estados usan semántica HTML.
- Etiquetas permanecen asociadas a los inputs; errores usan `aria-invalid`, `aria-describedby` y anuncios vivos.
- Se ofrece foco visible, cierre de menú con Escape, estados `aria-expanded`/`aria-controls` y focos de resultado tras la consulta.
- La jerarquía de color usa texto además de color para comunicar estados.
- Los tonos de texto secundarios usados en superficies claras mantienen al menos 4.5:1 en las combinaciones base medidas (`--ink-500` sobre canvas: 4.57:1; `--ink-600` sobre canvas: 5.15:1). El anillo de foco combina un borde claro con un anillo teal oscuro para conservar contraste en fondos claros y oscuros.
- Las transiciones se desactivan con `prefers-reduced-motion`.
- Se verifican anchos de 1440, 1280, 768, 390 y 320 px, incluyendo overflow horizontal y menú móvil.

Estas comprobaciones son pruebas del proyecto, no una certificación de conformidad WCAG. La revisión manual con lector de pantalla queda fuera del alcance automatizado.

## Evidencia comparativa

Las capturas `before-*` se tomaron de la versión previa a este rediseño. `home-320.png`, `home-390.png`, `home-768.png`, `home-1280.png` y `home-1440.png` muestran la portada en los anchos objetivo. `taxpayer-success.png`, `vehicle-success.png` y `provider-error.png` son capturas completas de desktop; sus variantes `*-390.png` muestran el resultado en una ventana móvil. `ant-demo-success.png` documenta la vista local simulada. Todas las nuevas capturas corresponden a escenarios Playwright con fixtures sintéticos; no representan consultas reales al SRI ni a la ANT.

Directorio de capturas: [`evidencias-ui/`](evidencias-ui/).

## Limitaciones

- El contenido que aparece en resultados depende de los servicios reales del SRI y puede cambiar con la respuesta del proveedor.
- No se implementó una consulta automática ANT. El componente sigue informativo y el enlace dirige al trámite web oficial.
- La vista opt-in ANT es local y simulada; no forma parte de la build de producción.
- No se cambiaron los contratos REST ni se añadieron campos de negocio.
