# Rediseño UI/UX — Consultas SRI ANT

## Dirección del diseño

La aplicación conserva su propósito y su flujo existente. La dirección visual es editorial e institucional contemporánea: una portada asimétrica con fotografía andina, tipografía de sistema, reglas finas y superficies planas. El azul tinta organiza la identidad, el teal marca acciones y estados y el ámbar se limita a acentos. La marca identifica un proyecto académico independiente y no imita una institución pública.

## Observaciones de la interfaz anterior

- La portada, el catálogo y el área de consulta competían por atención y el hero era una superficie amplia de color con poco vínculo visual con movilidad.
- Los servicios tenían una presentación uniforme aunque su disponibilidad y su papel en el flujo son distintos.
- La información del contribuyente se mostraba como una lista extensa; la placa y varios datos vehiculares no tenían una jerarquía clara.
- El layout anterior usaba paneles anidados para pasos y resultados, y no separaba suficientemente la orientación de la captura de datos.
- En móvil, el recorrido hasta el formulario era largo y los resultados conservaban el mismo formato denso que en escritorio.

## Sistema visual

- **Paleta:** azul tinta `#102A43`, teal `#146C76`, dorado suave `#E7AA65`, fondo claro `#F5F7F5`, superficies blancas y grises verdosos suaves.
- **Tipografía:** pila local `Inter, Aptos, Segoe UI, system-ui`; no se requiere una descarga de fuentes externa.
- **Jerarquía:** titulares editoriales con contraste de escala; subtítulos de sección; procedencia y estado se mantienen cerca del contenido al que describen.
- **Espaciado:** escala CSS de 4 a 80 px; áreas de contenido centradas y controles con altura táctil cómoda.
- **Formas:** radios moderados en paneles y controles, radios mayores en secciones de portada, bordes reservados para separar grupos y estados.
- **Sombras:** solo en elementos elevados —hero, resultados y paneles principales— con intensidad baja.

Los tokens están definidos en `frontend/src/index.css`. Los estilos de sitio, consulta, resultados y responsive están separados en `frontend/src/styles/` y se importan desde `frontend/src/App.css`. Los iconos SVG de línea son locales; los controles conservan texto visible y los iconos decorativos se ocultan a tecnologías asistivas.

## Estructura y jerarquía

1. **Header:** `SiteHeader` mantiene marca propia, identificación académica, anclas funcionales y CTA; el menú móvil responde a Escape y restaura el foco.
2. **Hero fotográfico:** `HeroSection` combina una carretera andina WebP, un titular editorial, CTA al formulario y una nota sobre el correo.
3. **Servicios:** `ServicesSection` presenta contribuyentes y vehículos con fotografías locales, y conserva `AntStatusCard` como tercer servicio, claramente pendiente.
4. **Consulta:** `ConsultationFlow` mantiene dos etapas numeradas: contribuyente y vehículo. El formulario vehicular aparece después de confirmar persona natural. ANT es independiente del recorrido.
5. **Ayuda contextual:** guía de dos pasos, aviso sobre manejo temporal de los datos, titularidad y disponibilidad del proveedor.
6. **Resultados:** `TaxpayerResult` agrupa campos realmente recibidos y muestra el RUC una sola vez; `VehicleResult` prioriza placa y atributos vehiculares y aclara que la consulta no confirma titularidad.
7. **Cierre:** `SiteFooter` reúne enlaces, el carácter académico del producto y una nota de disponibilidad que no promete que el proveedor siempre responda.

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

## Evidencia visual

Las capturas `before-*` corresponden a la versión previa. Playwright guarda las evidencias del rediseño en `evidencias-ui/rediseño-final/` y comprueba que las fotografías locales carguen antes de capturar.

| Captura | Contenido |
|---|---|
| [01-portada-escritorio.png](evidencias-ui/rediseño-final/01-portada-escritorio.png) | Portada y página completa a 1440 px |
| [02-catalogo-servicios.png](evidencias-ui/rediseño-final/02-catalogo-servicios.png) | Las tres tarjetas de servicio |
| [03-formulario-contribuyente.png](evidencias-ui/rediseño-final/03-formulario-contribuyente.png) | Primera etapa del formulario |
| [04-resultado-contribuyente.png](evidencias-ui/rediseño-final/04-resultado-contribuyente.png) | Informe sintético de contribuyente |
| [05-formulario-vehicular.png](evidencias-ui/rediseño-final/05-formulario-vehicular.png) | Segunda etapa del formulario |
| [06-resultado-vehicular.png](evidencias-ui/rediseño-final/06-resultado-vehicular.png) | Informe sintético de vehículo |
| [07-error-proveedor.png](evidencias-ui/rediseño-final/07-error-proveedor.png) | Mensaje seguro ante respuesta del proveedor no utilizable |
| [08-estado-ant.png](evidencias-ui/rediseño-final/08-estado-ant.png) | Estado informativo pendiente de ANT |
| [09-portada-movil.png](evidencias-ui/rediseño-final/09-portada-movil.png) | Página completa a 390 px |

Las capturas con resultados usan exclusivamente fixtures sintéticos de la suite E2E; sus valores no son consultas reales ni datos entregados por el SRI o ANT. Los requestId y la hora de respuesta se generan durante cada ejecución y pueden variar. Las capturas anteriores de responsive y de la demo ANT se mantienen en el directorio histórico [`evidencias-ui/`](evidencias-ui/).

## Limitaciones

- El contenido que aparece en resultados depende de los servicios reales del SRI y puede cambiar con la respuesta del proveedor.
- No se implementó una consulta automática ANT. El componente sigue informativo y el enlace dirige al trámite web oficial.
- La vista opt-in ANT es local y simulada; no forma parte de la build de producción.
- No se cambiaron los contratos REST ni se añadieron campos de negocio.
