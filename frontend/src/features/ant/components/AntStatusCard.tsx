import { PortalIcon } from '../../../shared/components/PortalIcon'

export function AntStatusCard() {
  return (
    <article className="service-card service-card--ant ant-card" id="estado-ant" aria-labelledby="ant-title" tabIndex={-1}>
      <div className="ant-card__art" aria-hidden="true">
        <span className="service-card__number">03</span>
        <span className="ant-card__document"><PortalIcon name="file" /><i /><i /><i /><b><PortalIcon name="shield" /></b></span>
        <span className="ant-card__art-caption">TRÁMITE OFICIAL</span>
      </div>
      <div className="service-card__body ant-card__body">
        <div className="ant-card__eyebrow">
          <span className="ant-status-dot" />
          <span>Integración automática pendiente</span>
        </div>
        <div className="ant-card__heading">
          <h3 id="ant-title">Puntos de licencia ANT</h3>
          <span className="status-pill status-pill--pending">En revisión</span>
        </div>
        <p>
          La consulta automática directa de puntos todavía no está integrada. La ANT dispone de un trámite web oficial para obtener el certificado de información del conductor.
        </p>
        <a
          className="text-link ant-card__official-link"
          href="https://www.gob.ec/ant/tramites/emision-certificado-informacion-conductor"
          target="_blank"
          rel="noopener noreferrer"
        >
          Consultar el trámite oficial <PortalIcon name="arrow-up-right" />
        </a>
        <p className="ant-card__footnote">
          El trámite web no equivale a una API. Esta aplicación no consulta la ANT ni muestra puntos simulados. La integración se habilitará cuando exista un mecanismo autorizado y verificado.
        </p>
      </div>
    </article>
  )
}
