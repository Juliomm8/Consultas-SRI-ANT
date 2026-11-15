import type { ApiSuccess, ContribuyenteResponse, JsonValue } from '../../../shared/api/contracts'
import { FieldList } from '../../../shared/components/FieldList'
import { PortalIcon } from '../../../shared/components/PortalIcon'
import { ResultMetadata } from '../../../shared/components/ResultMetadata'

type TaxpayerResultData = ApiSuccess<ContribuyenteResponse>

const sectionDefinitions = [
  { title: 'Actividad económica', keys: ['actividadEconomicaPrincipal'] },
  { title: 'Régimen y obligaciones', keys: ['tipoContribuyente', 'regimen', 'categoria', 'obligadoLlevarContabilidad', 'agenteRetencion', 'contribuyenteEspecial'] },
  { title: 'Fechas registradas', keys: ['informacionFechasContribuyente'] },
  { title: 'Información adicional', keys: ['representantesLegales', 'motivoCancelacionSuspension', 'contribuyenteFantasma', 'transaccionesInexistente'] },
]

function hasDisplayValue(value: JsonValue): boolean {
  if (value === null) return false
  if (typeof value === 'string') return value.trim().length > 0
  if (Array.isArray(value)) return value.some(hasDisplayValue)
  if (typeof value === 'object') return Object.values(value).some(hasDisplayValue)
  return true
}

function fieldsForKeys(fields: Record<string, JsonValue>, keys: string[]): Record<string, JsonValue> {
  const selected: Record<string, JsonValue> = {}
  for (const key of keys) {
    const value = fields[key]
    if (value !== undefined && hasDisplayValue(value)) selected[key] = value
  }
  return selected
}

function ReportSection({ title, fields }: { title: string; fields: Record<string, JsonValue> }) {
  if (Object.keys(fields).length === 0) return null
  return (
    <section className="report-section" aria-label={title}>
      <h5>{title}</h5>
      <FieldList fields={fields} />
    </section>
  )
}

export function TaxpayerResult({
  result,
  onStartNew,
  focusRef,
}: {
  result: TaxpayerResultData
  onStartNew: () => void
  focusRef: (element: HTMLDivElement | null) => void
}) {
  const providerFields = result.data.datosSRI
  const businessName = providerFields.razonSocial
  const taxpayerStatus = providerFields.estadoContribuyenteRuc
  const visibleBusinessName = typeof businessName === 'string' && businessName.trim() ? businessName : 'Información del contribuyente'
  const visibleStatus = typeof taxpayerStatus === 'string' && taxpayerStatus.trim() ? taxpayerStatus : null
  const consumedKeys = new Set([
    'numeroRuc',
    'razonSocial',
    'estadoContribuyenteRuc',
    ...sectionDefinitions.flatMap((section) => section.keys),
  ])
  const remainingFields = Object.fromEntries(Object.entries(providerFields)
    .filter(([key, value]) => !consumedKeys.has(key) && hasDisplayValue(value)))

  return (
    <div className="result-view taxpayer-report" ref={focusRef} role="region" aria-label="Resultado de contribuyente" tabIndex={-1}>
      <div className="report-status">
        <span className="report-status__mark" aria-hidden="true"><PortalIcon name="check" /></span>
        <div><span>Consulta completada · {result.meta.source}</span><strong>Persona natural confirmada</strong></div>
      </div>

      <header className="taxpayer-report__identity">
        <div className="taxpayer-report__identity-main">
          <span className="report-kicker">INFORMACIÓN GENERAL</span>
          <h4>{visibleBusinessName}</h4>
          {visibleStatus && <p><span className="status-dot" /> Estado del contribuyente: <strong>{visibleStatus}</strong></p>}
        </div>
        <div className="taxpayer-report__identifier">
          <span>RUC consultado</span>
          <strong>{result.data.ruc}</strong>
        </div>
      </header>

      <div className="report-sections">
        {sectionDefinitions.map((section) => (
          <ReportSection key={section.title} title={section.title} fields={fieldsForKeys(providerFields, section.keys)} />
        ))}
        <ReportSection title="Otros datos recibidos" fields={remainingFields} />
      </div>

      <ResultMetadata meta={result.meta} />
      <button className="button button--outline" onClick={onStartNew} type="button">
        Iniciar otra consulta <PortalIcon name="arrow-right" />
      </button>
    </div>
  )
}
