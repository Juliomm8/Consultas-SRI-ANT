import { PortalIcon } from '../../../shared/components/PortalIcon'

export function AntStatusCard({
  antDemoAvailable = false,
  onOpenAntDemo,
}: {
  antDemoAvailable?: boolean
  onOpenAntDemo?: () => void
}) {
  return (
    <article className="service-entry ant-card" id="estado-ant" aria-labelledby="ant-title" tabIndex={-1}>
      <span className="service-entry__icon service-entry__icon--ant" aria-hidden="true"><PortalIcon name="file" /></span>
      <div className="service-entry__copy ant-card__body">
        <p className="service-entry__source">ANT · Puntos de licencia</p>
        <div className="ant-card__heading">
          <h3 id="ant-title">Consulta de puntos de licencia ANT</h3>
          <span className="ant-card__status">Integración real temporalmente pendiente</span>
        </div>
        <p>
          La consulta automática directa de puntos todavía no está integrada porque el portal oficial no ha proporcionado una respuesta funcional verificable.
          La demostración local, cuando está habilitada, usa datos sintéticos y no representa información oficial.
        </p>
        <div className="ant-card__actions">
          <a className="text-link" href="https://www.gob.ec/ant/tramites/emision-certificado-informacion-conductor" target="_blank" rel="noopener noreferrer">
            Consultar el trámite oficial del certificado <PortalIcon name="arrow-up-right" />
          </a>
          {antDemoAvailable && onOpenAntDemo && (
            <button className="text-link ant-demo-open" type="button" onClick={onOpenAntDemo}>
              Abrir simulación académica <PortalIcon name="arrow-right" />
            </button>
          )}
        </div>
        <p className="ant-card__footnote">El trámite web y una API de consulta automática son mecanismos distintos. Esta aplicación no obtiene puntos reales de la ANT.</p>
      </div>
    </article>
  )
}
