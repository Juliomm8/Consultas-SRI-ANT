import type { ApiMeta } from '../api/contracts'

function formatRetrievedAt(value: string): string {
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return 'Fecha no disponible'
  return new Intl.DateTimeFormat('es-EC', { dateStyle: 'medium', timeStyle: 'short' }).format(date)
}

export function ResultMetadata({ meta }: { meta: ApiMeta }) {
  return (
    <div className="result-meta">
      <span>Información recibida el {formatRetrievedAt(meta.retrievedAt)}</span>
      <span>Referencia <code>{meta.requestId}</code></span>
    </div>
  )
}
