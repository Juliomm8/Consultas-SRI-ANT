import { PortalIcon } from './PortalIcon'
import { SiteBrand } from './SiteHeader'

export function SiteFooter() {
  return (
    <footer className="site-footer">
      <div className="site-footer__inner">
        <div className="site-footer__brand-column">
          <SiteBrand footer />
          <p>Proyecto académico independiente para organizar consultas de información disponible en servicios públicos.</p>
        </div>
        <nav className="site-footer__links" aria-label="Enlaces del pie de página">
          <strong>Explora</strong>
          <a href="#servicios">Servicios</a>
          <a href="#consulta">Iniciar una consulta</a>
          <a href="#estado-ant">Estado de ANT</a>
        </nav>
        <div className="site-footer__availability">
          <strong>Disponibilidad</strong>
          <p><span className="status-dot" /> Integración SRI disponible; respuesta sujeta al proveedor</p>
          <p><span className="status-dot status-dot--pending" /> Integración automática ANT pendiente</p>
        </div>
        <div className="site-footer__bottom">
          <span>Experiencia académica · No es un portal institucional</span>
          <a href="#inicio">Volver arriba <PortalIcon name="arrow-up-right" /></a>
        </div>
      </div>
    </footer>
  )
}
