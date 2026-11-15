import { PortalIcon } from './PortalIcon'

export function HeroSection() {
  return (
    <section className="hero" id="inicio" aria-labelledby="page-title" tabIndex={-1}>
      <div className="hero__content">
        <p className="eyebrow"><span className="eyebrow__rule" /> Información pública, en contexto</p>
        <h1 id="page-title">Consulta con <em>claridad.</em></h1>
        <p className="hero__description">
          Reúne la información disponible de contribuyentes y vehículos en una experiencia sencilla, ordenada y transparente sobre sus alcances.
        </p>
        <a className="button button--gold" href="#consulta">
          Comenzar consulta <PortalIcon name="arrow-right" />
        </a>
        <p className="hero__privacy"><PortalIcon name="shield" /> El correo se valida aquí; no se envía al SRI.</p>
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
        <figcaption className="hero__location"><span /> Sierra ecuatoriana</figcaption>
        <div className="hero__service-note" role="note" aria-label="Dos consultas SRI disponibles">
          <span className="hero__service-note-icon"><PortalIcon name="spark" /></span>
          <span><strong>Dos servicios SRI</strong><small>Contribuyentes y vehículos</small></span>
        </div>
      </figure>
    </section>
  )
}
