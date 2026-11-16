import { AntStatusCard } from '../../features/ant/components/AntStatusCard'
import { PortalIcon } from './PortalIcon'

export function ServicesSection({
  antDemoAvailable,
  onOpenAntDemo,
}: {
  antDemoAvailable: boolean
  onOpenAntDemo: () => void
}) {
  return (
    <section className="service-section" id="servicios" aria-labelledby="services-title" tabIndex={-1}>
      <div className="section-heading">
        <div>
          <p className="eyebrow eyebrow--ink"><span className="eyebrow__rule" /> Servicios de consulta</p>
          <h2 id="services-title">Cada consulta, con su alcance.</h2>
        </div>
        <p className="section-heading__intro">Tres operaciones SRI participan en el recorrido. La consulta ANT se presenta aparte mientras su integración real sigue pendiente.</p>
      </div>

      <div className="service-directory">
        <div className="service-directory__list" aria-label="Operaciones disponibles o previstas">
          <article className="service-entry service-entry--simple">
            <span className="service-entry__icon" aria-hidden="true"><PortalIcon name="id" /></span>
            <div className="service-entry__copy">
              <p className="service-entry__source">SRI · Registro de contribuyentes</p>
              <h3>Verificación de existencia del RUC</h3>
              <p>Comprueba si el registro aparece en el servicio del SRI antes de solicitar sus detalles.</p>
              <a className="text-link" href="#consulta">Ir a la consulta <PortalIcon name="arrow-right" /></a>
            </div>
          </article>

          <article className="service-entry service-entry--photo service-entry--taxpayer">
            <img
              src="/images/office-documents-720.webp"
              srcSet="/images/office-documents-720.webp 720w, /images/office-documents.webp 1400w"
              sizes="(max-width: 760px) 100vw, 160px"
              alt="Documentos y computador portátil en un espacio de trabajo"
              width="720"
              height="540"
              loading="lazy"
            />
            <div className="service-entry__copy">
              <p className="service-entry__source">SRI · Contribuyentes</p>
              <h3>Información del contribuyente</h3>
              <p>Organiza los campos recibidos y confirma la clasificación necesaria para continuar.</p>
              <a className="text-link" href="#consulta">Consultar contribuyente <PortalIcon name="arrow-right" /></a>
            </div>
          </article>

          <article className="service-entry service-entry--photo service-entry--vehicle">
            <img
              src="/images/car-mountain-road-720.webp"
              srcSet="/images/car-mountain-road-720.webp 720w, /images/car-mountain-road.webp 1400w"
              sizes="(max-width: 760px) 100vw, 160px"
              alt="Automóvil en una carretera de montaña"
              width="720"
              height="480"
              loading="lazy"
            />
            <div className="service-entry__copy">
              <p className="service-entry__source">SRI · Vehículos</p>
              <h3>Información del vehículo</h3>
              <p>Consulta los datos disponibles mediante una placa. La respuesta no confirma titularidad.</p>
              <a className="text-link" href="#consulta">Ver etapa vehicular <PortalIcon name="arrow-right" /></a>
            </div>
          </article>

          <AntStatusCard antDemoAvailable={antDemoAvailable} onOpenAntDemo={onOpenAntDemo} />
        </div>
        <aside className="service-directory__note">
          <p className="eyebrow eyebrow--ink"><span className="eyebrow__rule" /> Transparencia del servicio</p>
          <p>El correo se valida en esta aplicación y no se envía al SRI. Las consultas usan los servicios disponibles y pueden depender de su respuesta.</p>
        </aside>
      </div>
      <p className="service-disclaimer"><PortalIcon name="shield" /> Proyecto académico independiente; no representa al SRI, a la ANT ni a una entidad pública.</p>
    </section>
  )
}
