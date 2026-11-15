import { useState, type FormEvent } from 'react'
import { ApiRequestError } from '../../../shared/api/apiClient'
import type { ApiSuccess } from '../../../shared/api/contracts'
import { consultarPuntosSimulados, type PuntosDemoData, type PuntosDemoMeta } from './antPointsApi'
import './antPointsDemo.css'

type DemoResult = ApiSuccess<PuntosDemoData> & { meta: PuntosDemoMeta }

type DemoState =
  | { kind: 'idle' }
  | { kind: 'loading' }
  | { kind: 'success'; result: DemoResult }
  | { kind: 'empty'; requestId?: string }
  | { kind: 'timeout'; message: string; requestId?: string }
  | { kind: 'unavailable'; message: string; requestId?: string }
  | { kind: 'invalid'; message: string; requestId?: string }
  | { kind: 'error'; message: string; requestId?: string }

const REQUEST_ID_PATTERN = /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i
const SYNTHETIC_CEDULA = '0000000000'

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value)
}

function isValidSimulatedResult(result: ApiSuccess<PuntosDemoData>): result is DemoResult {
  const meta: unknown = result.meta
  const data: unknown = result.data
  if (!isRecord(meta) || !isRecord(data)) return false
  if (meta.source !== 'SIMULATED' || meta.stale !== false) return false
  if (typeof meta.requestId !== 'string' || !REQUEST_ID_PATTERN.test(meta.requestId)) return false
  if (typeof meta.fetchedAt !== 'string' || Number.isNaN(Date.parse(meta.fetchedAt))) return false
  if (typeof meta.retrievedAt !== 'string' || Number.isNaN(Date.parse(meta.retrievedAt))) return false
  if (!Number.isInteger(data.totalPuntos) || typeof data.totalPuntos !== 'number' || data.totalPuntos < 0) return false
  return meta.warning === null || typeof meta.warning === 'string'
}

function formatDate(value: string): string {
  return new Intl.DateTimeFormat('es-EC', { dateStyle: 'medium', timeStyle: 'short' }).format(new Date(value))
}

function RequestReference({ requestId }: { requestId?: string }) {
  return requestId && REQUEST_ID_PATTERN.test(requestId)
    ? <small>Referencia de solicitud: <code>{requestId}</code></small>
    : null
}

function StatusMessage({ state }: { state: Exclude<DemoState, { kind: 'idle' | 'loading' | 'success' }> }) {
  if (state.kind === 'empty') {
    return (
      <div className="ant-demo-message ant-demo-message--info" role="status">
        <strong>Sin información en este escenario</strong>
        <p>No se recibió un saldo de puntos. Esto no equivale a cero puntos.</p>
        <RequestReference requestId={state.requestId} />
      </div>
    )
  }

  const title = state.kind === 'timeout'
    ? 'Tiempo de espera agotado'
    : state.kind === 'unavailable'
      ? 'Servicio simulado no disponible'
      : state.kind === 'invalid'
        ? 'Respuesta simulada no válida'
        : 'No se pudo completar la demostración'

  return (
    <div className="ant-demo-message ant-demo-message--error" role="alert">
      <strong>{title}</strong>
      <p>{state.message}</p>
      <RequestReference requestId={state.requestId} />
    </div>
  )
}

export function AntPointsDemo({ onBack }: { onBack: () => void }) {
  const [state, setState] = useState<DemoState>({ kind: 'idle' })

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const form = event.currentTarget
    const input = new FormData(form).get('cedula')
    if (typeof input !== 'string' || input !== SYNTHETIC_CEDULA) {
      form.reset()
      setState({ kind: 'invalid', message: 'Usa únicamente el fixture sintético 0000000000; no ingreses una identificación real.' })
      return
    }

    form.reset()
    setState({ kind: 'loading' })
    try {
      const result = await consultarPuntosSimulados(input)
      if (!isValidSimulatedResult(result)) {
        setState({ kind: 'invalid', message: 'La respuesta no coincide con el contrato local de simulación.' })
        return
      }
      setState({ kind: 'success', result })
    } catch (error) {
      if (!(error instanceof ApiRequestError)) {
        setState({ kind: 'error', message: 'No se pudo conectar con el backend local de demostración.' })
        return
      }

      if (error.status === 404 && error.code === 'NOT_FOUND') {
        setState({ kind: 'empty', requestId: error.requestId })
      } else if (error.status === 504 || error.code === 'REQUEST_TIMEOUT') {
        setState({ kind: 'timeout', message: error.message, requestId: error.requestId })
      } else if (error.code === 'NETWORK_ERROR') {
        setState({ kind: 'error', message: 'No se pudo conectar con el backend local de demostración.' })
      } else if (error.status === 503) {
        setState({ kind: 'unavailable', message: error.message, requestId: error.requestId })
      } else if (error.status === 502 || error.code === 'INVALID_API_RESPONSE') {
        setState({ kind: 'invalid', message: error.message, requestId: error.requestId })
      } else {
        setState({ kind: 'error', message: error.message, requestId: error.requestId })
      }
    }
  }

  return (
    <main className="ant-demo-page" aria-labelledby="ant-demo-title">
      <button className="button button--quiet ant-demo-back" type="button" onClick={onBack}>
        ← Volver al portal
      </button>
      <section className="ant-demo-panel">
        <div className="ant-demo-banner" role="note" aria-label="Aviso permanente de simulación">
          MODO DEMOSTRACIÓN — DATOS SIMULADOS — NO OFICIALES
        </div>
        <div className="ant-demo-heading">
          <span className="section-index">PRUEBA LOCAL</span>
          <h1 id="ant-demo-title">Demostración de puntos de licencia</h1>
          <p>Esta vista recorre el endpoint local del backend. No consulta ANT ni representa un saldo real.</p>
        </div>

        <form className="ant-demo-form" onSubmit={handleSubmit} noValidate>
          <label htmlFor="ant-demo-cedula">Identificación sintética</label>
          <p id="ant-demo-help">Usa únicamente el valor sintético fijo 0000000000. No ingreses identificaciones reales; el campo se limpia al enviar y no se guarda en el navegador.</p>
          <div className="ant-demo-form__row">
            <input
              id="ant-demo-cedula"
              name="cedula"
              type="text"
              inputMode="numeric"
              autoComplete="off"
              maxLength={10}
              pattern="[0-9]{10}"
              placeholder="Fixture sintético"
              aria-describedby="ant-demo-help"
              required
            />
            <button className="button button--primary" type="submit" disabled={state.kind === 'loading'}>
              {state.kind === 'loading' ? 'Consultando…' : 'Consultar escenario local'}
            </button>
          </div>
        </form>

        {state.kind === 'loading' && <p className="ant-demo-loading" role="status">Esperando al backend local…</p>}
        {state.kind === 'success' && (
          <section className="ant-demo-result" role="status" aria-label="Resultado simulado">
            <div className="ant-demo-result__header">
              <span className="status-pill status-pill--simulated">SIMULATED</span>
              <span>Resultado local · No oficial</span>
            </div>
            <p className="ant-demo-result__label">Total de puntos simulado</p>
            <strong className="ant-demo-result__total">{state.result.data.totalPuntos}</strong>
            {state.result.data.totalPuntos === 0 && (
              <p className="ant-demo-zero">Cero es un resultado válido de este escenario simulado.</p>
            )}
            <p className="ant-demo-result__date">Generado: {formatDate(state.result.meta.fetchedAt)}</p>
            <p className="ant-demo-warning">Este dato es de prueba; no proviene de la ANT ni debe usarse para trámites.</p>
            <small>Referencia de solicitud: <code>{state.result.meta.requestId}</code></small>
          </section>
        )}
        {state.kind !== 'idle' && state.kind !== 'loading' && state.kind !== 'success' && (
          <StatusMessage state={state} />
        )}
      </section>
    </main>
  )
}
