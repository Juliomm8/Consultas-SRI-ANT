import { PortalIcon } from './PortalIcon'

export interface ConsultationIssue {
  stage: 'taxpayer' | 'vehicle'
  message: string
  requestId?: string
  code?: string
  status?: number
  title?: string
}

function titleForIssue(issue: ConsultationIssue): string {
  if (issue.title) return issue.title
  if (issue.code === 'NOT_FOUND') return 'No encontramos información'
  if (issue.status === 404) return 'Servicio de consulta no disponible'
  if (issue.status === 400 || issue.code === 'VALIDATION_ERROR') return 'Revisa los datos ingresados'
  if (issue.status === 422) return 'No se cumple una condición de consulta'
  if (issue.status === 504 || issue.code === 'REQUEST_TIMEOUT') return 'La consulta superó el tiempo de espera'
  if (issue.code === 'NETWORK_ERROR') return 'No hay conexión con el backend'
  if (issue.code === 'INVALID_API_RESPONSE' || issue.status === 502) return 'Respuesta del servicio no utilizable'
  if (issue.status === 503) return 'Servicio externo no disponible'
  return 'No se pudo completar la consulta'
}

export function FeedbackNotice({
  issue,
  focusRef,
}: {
  issue: ConsultationIssue
  focusRef: (element: HTMLDivElement | null) => void
}) {
  return (
    <div className={`feedback feedback--${issue.status === 404 && issue.code === 'NOT_FOUND' ? 'empty' : 'error'}`} ref={focusRef} role="alert" tabIndex={-1}>
      <span className="feedback__icon" aria-hidden="true"><PortalIcon name={issue.code === 'NOT_FOUND' ? 'file' : 'shield'} /></span>
      <div>
        <strong>{titleForIssue(issue)}</strong>
        <p>{issue.message}</p>
        {issue.requestId && <small>Referencia de solicitud: <code>{issue.requestId}</code></small>}
      </div>
    </div>
  )
}
