import type { JsonValue } from '../api/contracts'

const labels: Record<string, string> = {
  numeroRuc: 'Número de RUC',
  razonSocial: 'Razón social',
  estadoContribuyenteRuc: 'Estado del contribuyente',
  actividadEconomicaPrincipal: 'Actividad económica principal',
  tipoContribuyente: 'Tipo de contribuyente',
  regimen: 'Régimen',
  categoria: 'Categoría',
  obligadoLlevarContabilidad: 'Obligado a llevar contabilidad',
  agenteRetencion: 'Agente de retención',
  contribuyenteEspecial: 'Contribuyente especial',
  informacionFechasContribuyente: 'Fechas del contribuyente',
  fechaInicioActividades: 'Inicio de actividades',
  fechaCese: 'Fecha de cese',
  fechaReinicioActividades: 'Reinicio de actividades',
  fechaActualizacion: 'Última actualización',
  representantesLegales: 'Representantes legales',
  motivoCancelacionSuspension: 'Motivo de cancelación o suspensión',
  contribuyenteFantasma: 'Contribuyente fantasma',
  transaccionesInexistente: 'Transacciones inexistentes',
  codigoVehiculo: 'Código del vehículo',
  numeroPlaca: 'Placa',
  numeroCamvCpn: 'CAMV / CPN',
  colorVehiculo1: 'Color principal',
  colorVehiculo2: 'Color secundario',
  cilindraje: 'Cilindraje',
  nombreClase: 'Clase',
  descripcionMarca: 'Marca',
  descripcionModelo: 'Modelo',
  anioAuto: 'Año',
  descripcionPais: 'País',
  mensajeMotivoAuto: 'Observación del vehículo',
  aplicaCuota: 'Aplica cuota',
  fechaUltimaMatricula: 'Última matrícula',
  fechaCaducidadMatricula: 'Caducidad de matrícula',
  fechaCompraRegistro: 'Fecha de registro',
  fechaRevision: 'Fecha de revisión',
  descripcionCanton: 'Cantón',
  descripcionServicio: 'Servicio',
  ultimoAnioPagado: 'Último año pagado',
  prohibidoEnajenar: 'Prohibido enajenar',
  observacion: 'Observación',
  estadoExoneracion: 'Estado de exoneración',
  verMensajeElectricos: 'Mensaje de vehículos eléctricos',
}

function isObject(value: JsonValue): value is { [key: string]: JsonValue } {
  return typeof value === 'object' && value !== null && !Array.isArray(value)
}

function hasDisplayValue(value: JsonValue): boolean {
  if (value === null) return false
  if (typeof value === 'string') return value.trim().length > 0
  if (Array.isArray(value)) return value.some(hasDisplayValue)
  if (isObject(value)) return Object.values(value).some(hasDisplayValue)
  return true
}

function fieldLabel(name: string): string {
  return labels[name] ?? name
    .replace(/([a-z0-9])([A-Z])/g, '$1 $2')
    .replace(/^./, (first) => first.toUpperCase())
}

function ValueView({ value }: { value: JsonValue }) {
  if (value === null) return null
  if (typeof value === 'boolean') return <>{value ? 'Sí' : 'No'}</>
  if (typeof value === 'string' || typeof value === 'number') return <>{String(value)}</>
  if (Array.isArray(value)) {
    return (
      <ul className="nested-list">
        {value.filter(hasDisplayValue).map((item, index) => (
          <li key={index}>
            {isObject(item) ? <FieldList fields={item} nested /> : <ValueView value={item} />}
          </li>
        ))}
      </ul>
    )
  }
  return <FieldList fields={value} nested />
}

export function FieldList({
  fields,
  nested = false,
}: {
  fields: Record<string, JsonValue>
  nested?: boolean
}) {
  const entries = Object.entries(fields).filter(([, value]) => hasDisplayValue(value))

  if (entries.length === 0) return <p className="empty-fields">Sin campos disponibles.</p>

  return (
    <dl className={nested ? 'field-list field-list--nested' : 'field-list'}>
      {entries.map(([name, value]) => (
        <div className="field-row" key={name}>
          <dt>{fieldLabel(name)}</dt>
          <dd><ValueView value={value} /></dd>
        </div>
      ))}
    </dl>
  )
}
