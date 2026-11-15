import type { ApiMeta, JsonValue, VehiculoResponse } from '../../../shared/api/contracts'
import { FieldList } from '../../../shared/components/FieldList'
import { PortalIcon } from '../../../shared/components/PortalIcon'
import { ResultMetadata } from '../../../shared/components/ResultMetadata'

const priorityKeys = ['anioAuto', 'descripcionPais', 'codigoVehiculo', 'numeroCamvCpn', 'estadoExoneracion', 'ultimoAnioPagado']
const technicalKeys = ['cilindraje', 'nombreClase', 'descripcionServicio', 'colorVehiculo1', 'colorVehiculo2', 'verMensajeElectricos']
const registrationKeys = ['fechaUltimaMatricula', 'fechaCaducidadMatricula', 'fechaCompraRegistro', 'fechaRevision', 'descripcionCanton', 'aplicaCuota', 'prohibidoEnajenar', 'observacion', 'mensajeMotivoAuto']
const identityKeys = ['numeroPlaca', 'descripcionMarca', 'descripcionModelo', ...priorityKeys]
const knownKeys = new Set([...identityKeys, ...technicalKeys, ...registrationKeys])

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

function valueAsText(value: JsonValue | undefined): string | null {
  return typeof value === 'string' && value.trim() ? value : null
}

function VehicleSection({ title, fields }: { title: string; fields: Record<string, JsonValue> }) {
  if (Object.keys(fields).length === 0) return null
  return <section className="report-section" aria-label={title}><h5>{title}</h5><FieldList fields={fields} /></section>
}

function VehicleRecord({ vehicle, index, count }: { vehicle: VehiculoResponse; index: number; count: number }) {
  const fields = vehicle.camposSRI
  const plate = valueAsText(fields.numeroPlaca)
  const brand = valueAsText(fields.descripcionMarca)
  const model = valueAsText(fields.descripcionModelo)
  const title = [brand, model].filter(Boolean).join(' · ')
  const remainingFields = Object.fromEntries(Object.entries(fields)
    .filter(([key, value]) => !knownKeys.has(key) && hasDisplayValue(value)))

  return (
    <article className="vehicle-record" aria-label={count > 1 ? `Vehículo ${index + 1} de ${count}` : 'Registro vehicular'}>
      <header className="vehicle-record__heading">
        <div>
          <span className="report-kicker">{count > 1 ? `RESULTADO ${index + 1} DE ${count}` : 'INFORMACIÓN RECIBIDA DEL SRI'}</span>
          <h4>{title || 'Información del vehículo'}</h4>
        </div>
        {plate && <div className="vehicle-record__plate"><span>Placa consultada</span><strong>{plate}</strong></div>}
      </header>

      <VehicleSection title="Datos principales" fields={fieldsForKeys(fields, priorityKeys)} />
      <VehicleSection title="Características" fields={fieldsForKeys(fields, technicalKeys)} />
      <VehicleSection title="Matrícula y estado" fields={fieldsForKeys(fields, registrationKeys)} />
      <VehicleSection title="Otros datos recibidos" fields={remainingFields} />
      <p className="vehicle-record__ownership"><PortalIcon name="shield" /> La consulta por placa no confirma titularidad.</p>
    </article>
  )
}

export function VehicleResult({
  vehicles,
  meta,
  onSearchAgain,
  focusRef,
}: {
  vehicles: VehiculoResponse[]
  meta: ApiMeta
  onSearchAgain: () => void
  focusRef: (element: HTMLDivElement | null) => void
}) {
  return (
    <div className="vehicle-results" ref={focusRef} role="region" aria-label="Resultado de vehículos" tabIndex={-1}>
      {vehicles.length === 0 ? (
        <div className="result-empty" role="status">
          <span aria-hidden="true"><PortalIcon name="car" /></span>
          <div>
            <strong>No se recibió información vehicular.</strong>
            <p>Esta respuesta no confirma por sí sola que el vehículo no exista. Puedes revisar la placa o intentar otra consulta.</p>
          </div>
        </div>
      ) : (
        <>
          <div className="report-status">
            <span className="report-status__mark" aria-hidden="true"><PortalIcon name="check" /></span>
            <div><span>Consulta completada · {meta.source}</span><strong>{vehicles.length} {vehicles.length === 1 ? 'resultado' : 'resultados'}</strong></div>
          </div>
          {vehicles.map((vehicle, index) => <VehicleRecord key={`${meta.requestId}-${index}`} vehicle={vehicle} index={index} count={vehicles.length} />)}
        </>
      )}
      <ResultMetadata meta={meta} />
      <button className="button button--outline" onClick={onSearchAgain} type="button">
        Consultar otra placa <PortalIcon name="arrow-right" />
      </button>
    </div>
  )
}
