import { useState } from 'react'
import { AntStatusCard } from './features/ant/components/AntStatusCard'
import { TaxpayerForm } from './features/contribuyentes/components/TaxpayerForm'
import { consultarContribuyente } from './features/contribuyentes/api/contribuyentesApi'
import { ApiRequestError } from './shared/api/apiClient'
import type { ApiSuccess, ContribuyenteResponse } from './shared/api/contracts'
import { FieldList } from './shared/components/FieldList'
import './App.css'

interface VisibleError {
  message: string
  requestId?: string
}

function formatRetrievedAt(value: string): string {
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return 'Fecha no disponible'
  return new Intl.DateTimeFormat('es-EC', { dateStyle: 'medium', timeStyle: 'short' }).format(date)
}

function taxpayerClassificationError(type: ContribuyenteResponse['tipoPersona']): string {
  if (type === 'JURIDICA') return 'La consulta requiere una persona natural.'
  return 'El SRI no permitió confirmar el tipo de persona.'
}

function App() {
  const [taxpayerResult, setTaxpayerResult] = useState<ApiSuccess<ContribuyenteResponse> | null>(null)
  const [taxpayerLoading, setTaxpayerLoading] = useState(false)
  const [error, setError] = useState<VisibleError | null>(null)

  async function handleTaxpayerSubmit(email: string, ruc: string) {
    setError(null)
    setTaxpayerLoading(true)
    try {
      const result = await consultarContribuyente({ email, ruc })
      if (result.data.tipoPersona !== 'NATURAL') {
        setError({
          message: taxpayerClassificationError(result.data.tipoPersona),
          requestId: result.meta.requestId,
        })
        return
      }
      setTaxpayerResult(result)
    } catch (requestError) {
      setError(requestError instanceof ApiRequestError
        ? { message: requestError.message, requestId: requestError.requestId }
        : { message: 'No pudimos completar la consulta. Inténtalo nuevamente.' })
    } finally {
      setTaxpayerLoading(false)
    }
  }

  function startNewConsultation() {
    setTaxpayerResult(null)
    setError(null)
  }

  return (
    <main className="portal-shell">
      <header className="portal-header">
        <a className="brand" href="#inicio" aria-label="Consultas SRI ANT, inicio">
          <span className="brand-mark" aria-hidden="true">C</span>
          <span className="brand-copy">
            <span className="eyebrow">Portal de consultas</span>
            <span className="brand-name">SRI <span>·</span> ANT</span>
          </span>
        </a>
        <span className="header-caption"><span className="header-dot" /> Información consultada en línea</span>
      </header>

      <section className="hero" id="inicio" aria-labelledby="page-title">
        <div className="hero-copy">
          <span className="hero-kicker"><span className="kicker-line" /> Servicios de consulta</span>
          <h1 id="page-title">Información clara para tus <em>trámites.</em></h1>
          <p>Consulta datos de contribuyentes y vehículos directamente a través de los servicios disponibles del SRI.</p>
        </div>
        <div className="hero-note">
          <span className="hero-note__icon" aria-hidden="true">i</span>
          <p>El correo se utiliza para validar tu solicitud y <strong>no se envía al SRI.</strong></p>
        </div>
      </section>

      <nav className="stepper" aria-label="Etapas de consulta">
        <ol>
          <li className={taxpayerResult ? 'step step--complete' : 'step step--current'} aria-current={taxpayerResult ? undefined : 'step'}>
            <span className="step-number">{taxpayerResult ? '✓' : '01'}</span>
            <span><strong>Contribuyente</strong><small>Consulta SRI</small></span>
          </li>
          <li className={taxpayerResult ? 'step step--current' : 'step step--pending'} aria-current={taxpayerResult ? 'step' : undefined}>
            <span className="step-number">02</span>
            <span><strong>Vehículo</strong><small>{taxpayerResult ? 'Consulta SRI' : 'Después de validar'}</small></span>
          </li>
          <li className="step step--pending">
            <span className="step-number">03</span>
            <span><strong>Puntos ANT</strong><small>Integración pendiente</small></span>
          </li>
        </ol>
      </nav>

      <div className="content-grid">
        <div className="primary-column">
          <section className="panel" aria-labelledby="taxpayer-title">
            <div className="panel-heading">
              <div>
                <span className="section-index">ETAPA 01</span>
                <h2 id="taxpayer-title">Consulta de contribuyente</h2>
                <p>Ingresa los datos para consultar la información disponible en el SRI.</p>
              </div>
              <span className="source-chip"><span /> SRI</span>
            </div>

            {error && (
              <div className="feedback feedback--error" role="alert">
                <span className="feedback-icon" aria-hidden="true">!</span>
                <div>
                  <strong>No se pudo completar la consulta</strong>
                  <p>{error.message}</p>
                  {error.requestId && <small>Referencia de solicitud: <code>{error.requestId}</code></small>}
                </div>
              </div>
            )}

            {taxpayerResult ? (
              <div className="result-view" role="region" aria-label="Resultado de contribuyente">
                <div className="result-banner">
                  <span className="result-check" aria-hidden="true">✓</span>
                  <div className="result-banner__copy">
                    <span>Consulta completada</span>
                    <strong>Persona natural confirmada</strong>
                  </div>
                  <span className="status-pill status-pill--success">Resultado SRI</span>
                </div>
                <div className="result-identity">
                  <span>RUC consultado</span>
                  <strong>{taxpayerResult.data.ruc}</strong>
                </div>
                <FieldList fields={taxpayerResult.data.datosSRI} />
                <div className="result-meta">
                  <span>Consultado el {formatRetrievedAt(taxpayerResult.meta.retrievedAt)}</span>
                  <span>Referencia <code>{taxpayerResult.meta.requestId}</code></span>
                </div>
                <button className="button button--quiet" onClick={startNewConsultation} type="button">
                  Iniciar otra consulta
                </button>
              </div>
            ) : (
              <TaxpayerForm disabled={false} isLoading={taxpayerLoading} onSubmit={handleTaxpayerSubmit} />
            )}
          </section>

          <section className={`next-step-card${taxpayerResult ? ' next-step-card--ready' : ''}`} aria-labelledby="vehicle-preview-title">
            <span className="next-step-card__number">02</span>
            <div>
              <h2 id="vehicle-preview-title">Consulta de vehículo</h2>
              <p>{taxpayerResult
                ? 'La consulta vehicular se habilitará en el siguiente paso.'
                : 'Primero completa la consulta de contribuyente para continuar.'}</p>
            </div>
            <span className="next-step-arrow" aria-hidden="true">→</span>
          </section>
        </div>

        <aside className="side-column">
          <section className="guide-card" aria-labelledby="guide-title">
            <span className="guide-icon" aria-hidden="true">✓</span>
            <h2 id="guide-title">Un paso a la vez</h2>
            <p>La consulta comienza verificando el tipo de contribuyente. Si el SRI confirma una persona natural, podrás continuar con la búsqueda del vehículo.</p>
            <div className="guide-divider" />
            <p className="guide-footnote">La búsqueda por placa no comprueba quién es propietario del vehículo.</p>
          </section>
          <AntStatusCard />
          <p className="privacy-footnote">Tus identificaciones solo se mantienen en memoria durante esta sesión. No se guardan en el navegador.</p>
        </aside>
      </div>

      <footer className="portal-footer">
        <span>Consultas SRI ANT</span>
        <span>Proyecto académico · Diseño y Arquitectura de Software</span>
      </footer>
    </main>
  )
}

export default App
