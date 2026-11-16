import { PortalIcon } from './PortalIcon'

export function HeroSection() {
  return (
    <section className="hero" id="inicio" aria-labelledby="page-title" tabIndex={-1}>
      <div className="hero__content">
        <p className="eyebrow"><span className="eyebrow__rule" /> Consultas SRI · ANT</p>
        <h1 id="page-title">Información útil,<br /><em>sin rodeos.</em></h1>
        <p className="hero__description">
          Consulta la información disponible de contribuyentes y vehículos con un recorrido claro y alcances explicados desde el inicio.
        </p>
        <a className="button button--gold" href="#consulta">
          Iniciar consulta <PortalIcon name="arrow-right" />
        </a>
        <p className="hero__privacy"><PortalIcon name="shield" /> Proyecto académico independiente · no es un portal oficial.</p>
      </div>
      <figure className="hero__visual">
        <picture>
          <source media="(max-width: 640px)" srcSet="/images/ecuador-mountain-road-mobile.webp" />
          <img
            src="/images/ecuador-mountain-road.webp"
            alt="Vista de una carretera andina entre montañas en Ecuador"
            width="2200"
            height="1000"
            fetchPriority="high"
            loading="eager"
          />
        </picture>
        <span className="hero__shade" aria-hidden="true" />
        <figcaption className="hero__location">Paisaje andino · Ecuador</figcaption>
        <div className="hero__service-note" role="note">
          <span className="hero__service-note-icon"><PortalIcon name="file" /></span>
          <span><strong>Tres operaciones SRI</strong><small>Registro, contribuyente y vehículo</small></span>
        </div>
      </figure>
    </section>
  )
}
