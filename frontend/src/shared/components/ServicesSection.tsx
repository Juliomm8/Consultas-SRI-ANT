import { AntStatusCard } from '../../features/ant/components/AntStatusCard'
import { PortalIcon } from './PortalIcon'

export function ServicesSection() {
  return (
    <section className="service-section" id="servicios" aria-labelledby="services-title" tabIndex={-1}>
      <div className="section-heading">
        <div>
          <p className="eyebrow eyebrow--ink"><span className="eyebrow__rule" /> Servicios disponibles y en revisión</p>
          <h2 id="services-title">Información que te ayuda a avanzar.</h2>
        </div>
        <p className="section-heading__intro">Consulta los servicios conectados y conoce con transparencia qué integración sigue pendiente.</p>
      </div>

      <div className="service-grid">
        <article className="service-card service-card--taxpayer">
          <div className="service-card__media">
            <img
              src="/images/office-documents.webp"
              srcSet="/images/office-documents-720.webp 720w, /images/office-documents.webp 1400w"
              sizes="(max-width: 700px) 100vw, (max-width: 1000px) 50vw, 380px"
              alt="Documentos revisados junto a un computador portátil"
              width="1400"
              height="1050"
              loading="lazy"
            />
            <span className="service-card__number">01</span>
            <span className="service-card__media-icon"><PortalIcon name="building" /></span>
          </div>
          <div className="service-card__body">
            <div className="service-card__eyebrow"><span className="status-dot" /> Disponible · SRI</div>
            <h3>Contribuyentes</h3>
            <p>Consulta el registro disponible. El flujo continúa al confirmarse la clasificación requerida.</p>
            <a className="text-link" href="#consulta">Consultar contribuyente <PortalIcon name="arrow-right" /></a>
          </div>
        </article>

        <article className="service-card service-card--vehicle">
          <div className="service-card__media">
            <img
              src="/images/car-mountain-road.webp"
              srcSet="/images/car-mountain-road-720.webp 720w, /images/car-mountain-road.webp 1400w"
              sizes="(max-width: 700px) 100vw, (max-width: 1000px) 50vw, 380px"
              alt="Automóvil en una carretera de montaña"
              width="1400"
              height="933"
              loading="lazy"
            />
            <span className="service-card__number">02</span>
            <span className="service-card__media-icon"><PortalIcon name="car" /></span>
          </div>
          <div className="service-card__body">
            <div className="service-card__eyebrow"><span className="status-dot" /> Después de validar · SRI</div>
            <h3>Vehículos</h3>
            <p>Revisa los datos que devuelve el SRI para una placa. La consulta no confirma titularidad.</p>
            <a className="text-link" href="#consulta">Ver el flujo de consulta <PortalIcon name="arrow-right" /></a>
          </div>
        </article>

        <AntStatusCard />
      </div>
      <p className="service-disclaimer"><PortalIcon name="shield" /> Las respuestas dependen del proveedor. Este proyecto académico no representa a una entidad pública.</p>
    </section>
  )
}
