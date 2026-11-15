import { useEffect, useRef, useState } from 'react'
import { AntStatusCard } from './features/ant/components/AntStatusCard'
import { AntPointsDemo } from './features/ant/demo/AntPointsDemo'
import { ANT_DEMO_AVAILABLE } from './features/ant/demo/demoAccess'
import { consultarContribuyente } from './features/contribuyentes/api/contribuyentesApi'
import { TaxpayerForm } from './features/contribuyentes/components/TaxpayerForm'
import { consultarVehiculos } from './features/vehiculos/api/vehiculosApi'
import { VehicleForm } from './features/vehiculos/components/VehicleForm'
import { ApiRequestError } from './shared/api/apiClient'
import type { ApiSuccess, ContribuyenteResponse, VehiculoResponse } from './shared/api/contracts'
import { FieldList } from './shared/components/FieldList'
import { PortalIcon } from './shared/components/PortalIcon'
import './App.css'

interface VisibleError {
  stage: 'taxpayer' | 'vehicle'
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

function ErrorNotice({
  error,
  focusRef,
}: {
  error: VisibleError
  focusRef: (element: HTMLDivElement | null) => void
}) {
  return (
    <div className="feedback feedback--error" ref={focusRef} role="alert" tabIndex={-1}>
      <span className="feedback__icon" aria-hidden="true">!</span>
      <div>
        <strong>No se pudo completar la consulta</strong>
        <p>{error.message}</p>
        {error.requestId && <small>Referencia de solicitud: <code>{error.requestId}</code></small>}
      </div>
    </div>
  )
}

function App() {
  const antDemoAvailable = ANT_DEMO_AVAILABLE
  const [taxpayerResult, setTaxpayerResult] = useState<ApiSuccess<ContribuyenteResponse> | null>(null)
  const [vehicleResult, setVehicleResult] = useState<ApiSuccess<VehiculoResponse[]> | null>(null)
  const [taxpayerLoading, setTaxpayerLoading] = useState(false)
  const [vehicleLoading, setVehicleLoading] = useState(false)
  const [error, setError] = useState<VisibleError | null>(null)
  const [showAntDemo, setShowAntDemo] = useState(false)
  const [menuOpen, setMenuOpen] = useState(false)
  const outcomeRef = useRef<HTMLDivElement | null>(null)

  useEffect(() => {
    if (error || taxpayerResult || vehicleResult) outcomeRef.current?.focus()
  }, [error, taxpayerResult, vehicleResult])

  async function handleTaxpayerSubmit(email: string, ruc: string) {
    setError(null)
    setTaxpayerLoading(true)
    try {
      const result = await consultarContribuyente({ email, ruc })
      if (result.data.tipoPersona !== 'NATURAL') {
        setError({
          stage: 'taxpayer',
          message: taxpayerClassificationError(result.data.tipoPersona),
          requestId: result.meta.requestId,
        })
        return
      }
      setTaxpayerResult(result)
    } catch (requestError) {
      setError(requestError instanceof ApiRequestError
        ? { stage: 'taxpayer', message: requestError.message, requestId: requestError.requestId }
        : { stage: 'taxpayer', message: 'No pudimos completar la consulta. Inténtalo nuevamente.' })
    } finally {
      setTaxpayerLoading(false)
    }
  }

  async function handleVehicleSubmit(plate: string) {
    setError(null)
    setVehicleLoading(true)
    try {
      const result = await consultarVehiculos({ placa: plate })
      setVehicleResult(result)
    } catch (requestError) {
      setError(requestError instanceof ApiRequestError
        ? { stage: 'vehicle', message: requestError.message, requestId: requestError.requestId }
        : { stage: 'vehicle', message: 'No pudimos completar la consulta. Inténtalo nuevamente.' })
    } finally {
      setVehicleLoading(false)
    }
  }

  function startNewConsultation() {
    setTaxpayerResult(null)
    setVehicleResult(null)
    setError(null)
    window.requestAnimationFrame(() => document.getElementById('taxpayer-title')?.focus())
  }

  function closeNavigation() {
    setMenuOpen(false)
  }

  if (antDemoAvailable && showAntDemo) {
    return <AntPointsDemo onBack={() => setShowAntDemo(false)} />
  }

  return (
    <div className="site-frame">
      <a className="skip-link" href="#main-content">Saltar al contenido principal</a>
      <header className="site-header">
        <div className="site-header__inner">
          <a className="brand" href="#inicio" aria-label="Consultas SRI ANT, inicio" onClick={closeNavigation}>
            <span className="brand-mark" aria-hidden="true">
              <svg viewBox="0 0 38 38" fill="none"><path d="M25.7 10.6a11.2 11.2 0 1 0 0 16.8" /><path d="M12.1 19h13.4M19 12.3v13.4" /></svg>
            </span>
            <span className="brand-copy">
              <span className="brand-name">Consultas <b>SRI · ANT</b></span>
              <span className="brand-caption">Plataforma académica independiente</span>
            </span>
          </a>

          <button
            className="menu-toggle"
            type="button"
            aria-controls="primary-navigation"
            aria-expanded={menuOpen}
            onClick={() => setMenuOpen((open) => !open)}
          >
            <PortalIcon name="menu" />
            <span>{menuOpen ? 'Cerrar' : 'Menú'}</span>
          </button>

          <nav
            className={`site-nav${menuOpen ? ' site-nav--open' : ''}`}
            id="primary-navigation"
            aria-label="Navegación principal"
            onKeyDown={(event) => {
              if (event.key === 'Escape') {
                setMenuOpen(false)
                document.querySelector<HTMLButtonElement>('.menu-toggle')?.focus()
              }
            }}
          >
            <a href="#servicios" onClick={closeNavigation}>Servicios</a>
            <a href="#consulta" onClick={closeNavigation}>Cómo consultar</a>
            <a href="#estado-ant" onClick={closeNavigation}>Estado ANT</a>
            {antDemoAvailable && (
              <button className="nav-demo-link" type="button" onClick={() => { setShowAntDemo(true); closeNavigation() }}>
                Demo local
              </button>
            )}
            <a className="site-nav__cta" href="#consulta" onClick={closeNavigation}>
              Iniciar consulta <PortalIcon name="arrow-right" />
            </a>
          </nav>
        </div>
      </header>

      <main id="main-content" tabIndex={-1}>
        <div className="page-shell">
          <section className="hero" id="inicio" aria-labelledby="page-title" tabIndex={-1}>
            <figure className="hero__visual">
              <picture>
                <source media="(max-width: 640px)" srcSet="/images/ecuador-mountain-road-mobile.webp" />
                <img
                  src="/images/ecuador-mountain-road.webp"
                  alt="Vista aérea de una carretera andina que atraviesa un paisaje de montaña en Ecuador"
                  width="2200"
                  height="1000"
                  fetchPriority="high"
                  loading="eager"
                />
              </picture>
              <span className="hero__shade" aria-hidden="true" />
              <figcaption className="hero__location"><span /> Ecuador · Paisaje andino</figcaption>
              <div className="hero__service-note" role="note" aria-label="Servicios SRI para contribuyentes y vehículos">
                <span className="hero__service-note-icon"><PortalIcon name="spark" /></span>
                <span><strong>Dos consultas SRI</strong><small>Contribuyentes y vehículos</small></span>
              </div>
            </figure>

            <div className="hero__content">
              <p className="eyebrow"><span className="eyebrow__rule" /> Consultas en un solo lugar</p>
              <h1 id="page-title">Tu información, <span className="hero__line">más clara <em>y a tu alcance.</em></span></h1>
              <p className="hero__description">
                Consulta información de contribuyentes y vehículos a través de los servicios disponibles del SRI, en una experiencia sencilla y organizada.
              </p>
              <a className="button button--gold" href="#consulta">
                Comenzar consulta <PortalIcon name="arrow-right" />
              </a>
              <p className="hero__privacy"><PortalIcon name="shield" /> El correo se valida aquí y no se envía al SRI.</p>
            </div>
          </section>

          <section className="service-section" id="servicios" aria-labelledby="services-title" tabIndex={-1}>
            <div className="section-heading">
              <div>
                <p className="eyebrow eyebrow--ink"><span className="eyebrow__rule" /> Lo que puedes consultar</p>
                <h2 id="services-title">Servicios para avanzar<br className="desktop-break" /> con más claridad.</h2>
              </div>
              <p className="section-heading__intro">Una experiencia organizada para consultar la información disponible, entendiendo qué está integrado y qué sigue en revisión.</p>
            </div>

            <div className="service-grid">
              <article className="service-card service-card--taxpayer">
                <div className="service-card__media">
                  <img
                    src="/images/office-documents.webp"
                    srcSet="/images/office-documents-720.webp 720w, /images/office-documents.webp 1400w"
                    sizes="(max-width: 700px) 100vw, (max-width: 1000px) 50vw, 380px"
                    alt="Manos organizan documentos junto a un computador portátil en un escritorio"
                    width="1400"
                    height="1050"
                    loading="lazy"
                  />
                  <span className="service-card__number">01</span>
                  <span className="service-card__media-icon"><PortalIcon name="building" /></span>
                </div>
                <div className="service-card__body">
                  <div className="service-card__eyebrow"><span className="status-dot" /> Integrado · SRI</div>
                  <h3>Información de contribuyentes</h3>
                  <p>Consulta el registro disponible del SRI. El flujo continúa cuando se confirma una persona natural.</p>
                  <a className="text-link" href="#consulta">Consultar contribuyente <PortalIcon name="arrow-right" /></a>
                </div>
              </article>

              <article className="service-card service-card--vehicle">
                <div className="service-card__media">
                  <img
                    src="/images/car-mountain-road.webp"
                    srcSet="/images/car-mountain-road-720.webp 720w, /images/car-mountain-road.webp 1400w"
                    sizes="(max-width: 700px) 100vw, (max-width: 1000px) 50vw, 380px"
                    alt="Automóvil que recorre una vía entre montañas"
                    width="1400"
                    height="933"
                    loading="lazy"
                  />
                  <span className="service-card__number">02</span>
                  <span className="service-card__media-icon"><PortalIcon name="car" /></span>
                </div>
                <div className="service-card__body">
                  <div className="service-card__eyebrow"><span className="status-dot" /> Después de validar · SRI</div>
                  <h3>Consulta de vehículos</h3>
                  <p>Revisa los datos que devuelve el SRI para una placa. La consulta no confirma la titularidad.</p>
                  <a className="text-link" href="#consulta">Ver el flujo de consulta <PortalIcon name="arrow-right" /></a>
                </div>
              </article>

              <AntStatusCard />
            </div>
            <p className="service-disclaimer"><PortalIcon name="shield" /> Las respuestas dependen de los servicios del proveedor. Esta plataforma académica no representa a una entidad pública.</p>
          </section>

          <section className="consultation-section" id="consulta" aria-labelledby="consultation-title" tabIndex={-1}>
            <div className="consultation-intro">
              <div>
                <p className="eyebrow eyebrow--ink"><span className="eyebrow__rule" /> Tu recorrido</p>
                <h2 id="consultation-title">Una consulta a la vez.</h2>
                <p>Comienza con los datos del contribuyente. La búsqueda vehicular aparece después de confirmar la condición requerida.</p>
              </div>
              <span className="consultation-intro__note"><span>01—02</span> Un flujo claro, sin pasos innecesarios</span>
            </div>

            <nav className="stepper" aria-label="Etapas de consulta">
              <ol>
                <li className={taxpayerResult ? 'step step--complete' : 'step step--current'} aria-current={taxpayerResult ? undefined : 'step'}>
                  <span className="step__number">{taxpayerResult ? <PortalIcon name="check" /> : '01'}</span>
                  <span className="step__copy"><strong>Contribuyente</strong><small>Consulta SRI</small></span>
                </li>
                <li className={vehicleResult ? 'step step--complete' : taxpayerResult ? 'step step--current' : 'step step--pending'} aria-current={taxpayerResult && !vehicleResult ? 'step' : undefined}>
                  <span className="step__number">{vehicleResult ? <PortalIcon name="check" /> : '02'}</span>
                  <span className="step__copy"><strong>Vehículo</strong><small>{taxpayerResult ? 'Consulta SRI' : 'Después de validar'}</small></span>
                </li>
              </ol>
            </nav>

            <div className="consultation-layout">
              <div className="consultation-main">
                <section className="workflow-panel" aria-labelledby="taxpayer-title">
                  <div className="workflow-panel__heading">
                    <div className="workflow-panel__title-wrap">
                      <span className="workflow-panel__step">ETAPA 01</span>
                      <div>
                        <h3 id="taxpayer-title" tabIndex={-1}>Consulta de contribuyente</h3>
                        <p>Ingresa los datos para consultar la información disponible en el SRI.</p>
                      </div>
                    </div>
                    <span className="source-chip"><span /> SRI</span>
                  </div>

                  {error?.stage === 'taxpayer' && <ErrorNotice error={error} focusRef={(element) => { outcomeRef.current = element }} />}

                  {taxpayerResult ? (
                    <div className="result-view" ref={outcomeRef} role="region" aria-label="Resultado de contribuyente" tabIndex={-1}>
                      <div className="result-banner">
                        <span className="result-check" aria-hidden="true"><PortalIcon name="check" /></span>
                        <div className="result-banner__copy">
                          <span>Consulta completada</span>
                          <strong>Persona natural confirmada</strong>
                        </div>
                        <span className="status-pill status-pill--success">Resultado {taxpayerResult.meta.source}</span>
                      </div>

                      <div className="result-overview">
                        <div className="result-overview__classification">
                          <span>Clasificación</span>
                          <strong><span className="result-overview__check"><PortalIcon name="check" /></span> Persona natural</strong>
                        </div>
                        <div className="result-overview__identifier">
                          <span>RUC consultado</span>
                          <strong>{taxpayerResult.data.ruc}</strong>
                        </div>
                      </div>

                      <div className="field-section">
                        <div className="field-section__heading">
                          <div><span className="section-index">INFORMACIÓN RECIBIDA</span><h4>Datos del contribuyente</h4></div>
                          <PortalIcon name="file" />
                        </div>
                        <FieldList fields={taxpayerResult.data.datosSRI} />
                      </div>
                      <div className="result-meta">
                        <span>Recibido el {formatRetrievedAt(taxpayerResult.meta.retrievedAt)}</span>
                        <span>Referencia <code>{taxpayerResult.meta.requestId}</code></span>
                      </div>
                      <button className="button button--outline" onClick={startNewConsultation} type="button">
                        Iniciar otra consulta <PortalIcon name="arrow-right" />
                      </button>
                    </div>
                  ) : (
                    <TaxpayerForm disabled={false} isLoading={taxpayerLoading} onSubmit={handleTaxpayerSubmit} />
                  )}
                </section>

                {taxpayerResult && (
                  <section className="workflow-panel workflow-panel--vehicle" aria-labelledby="vehicle-title">
                    <div className="workflow-panel__heading">
                      <div className="workflow-panel__title-wrap">
                        <span className="workflow-panel__step">ETAPA 02</span>
                        <div>
                          <h3 id="vehicle-title" tabIndex={-1}>Consulta de vehículo</h3>
                          <p>Busca información disponible en el SRI mediante la placa.</p>
                        </div>
                      </div>
                      <span className="source-chip"><span /> SRI</span>
                    </div>

                    {error?.stage === 'vehicle' && <ErrorNotice error={error} focusRef={(element) => { outcomeRef.current = element }} />}

                    {vehicleResult ? (
                      <div className="vehicle-results" ref={outcomeRef} role="region" aria-label="Resultado de vehículos" tabIndex={-1}>
                        {vehicleResult.data.length === 0 ? (
                          <div className="empty-state" role="status">
                            <span className="empty-state__icon"><PortalIcon name="car" /></span>
                            <div>
                              <strong>No se recibió información vehicular.</strong>
                              <p>Esta respuesta no confirma por sí sola que el vehículo no exista. Puedes revisar la placa o intentar otra consulta.</p>
                            </div>
                          </div>
                        ) : (
                          <>
                            <div className="vehicle-results__summary">
                              <span className="result-check" aria-hidden="true"><PortalIcon name="check" /></span>
                              <div><span>Información recibida</span><strong>{vehicleResult.data.length} {vehicleResult.data.length === 1 ? 'resultado' : 'resultados'}</strong></div>
                              <span className="status-pill status-pill--success">{vehicleResult.meta.source}</span>
                            </div>
                            {vehicleResult.data.map((vehicle, index) => (
                              <article className="vehicle-result-card" key={`${vehicleResult.meta.requestId}-${index}`}>
                                <div className="vehicle-result-card__heading">
                                  <span className="vehicle-result-card__icon"><PortalIcon name="car" /></span>
                                  <div>
                                    <span>{vehicleResult.data.length > 1 ? `RESULTADO ${index + 1} DE ${vehicleResult.data.length}` : 'INFORMACIÓN DEL SRI'}</span>
                                    <h4>{typeof vehicle.camposSRI.numeroPlaca === 'string' && vehicle.camposSRI.numeroPlaca
                                      ? `Vehículo ${vehicle.camposSRI.numeroPlaca}`
                                      : `Vehículo ${index + 1}`}</h4>
                                  </div>
                                </div>
                                <FieldList fields={vehicle.camposSRI} />
                              </article>
                            ))}
                          </>
                        )}
                        <div className="result-meta">
                          <span>Recibido el {formatRetrievedAt(vehicleResult.meta.retrievedAt)}</span>
                          <span>Referencia <code>{vehicleResult.meta.requestId}</code></span>
                        </div>
                        <button className="button button--outline" onClick={() => {
                          setVehicleResult(null)
                          setError(null)
                          window.requestAnimationFrame(() => document.getElementById('vehicle-title')?.focus())
                        }} type="button">
                          Consultar otra placa <PortalIcon name="arrow-right" />
                        </button>
                      </div>
                    ) : (
                      <VehicleForm isLoading={vehicleLoading} onSubmit={handleVehicleSubmit} />
                    )}
                  </section>
                )}

                {!taxpayerResult && (
                  <div className="next-step-card" aria-label="Siguiente etapa bloqueada">
                    <span className="next-step-card__number">02</span>
                    <div><strong>Luego, consulta de vehículo</strong><p>Esta etapa se habilita después de validar la consulta de contribuyente.</p></div>
                    <span className="next-step-card__lock"><PortalIcon name="shield" /></span>
                  </div>
                )}
              </div>

              <aside className="workflow-aside" aria-label="Información de la consulta">
                <section className="guide-card" aria-labelledby="guide-title">
                  <div className="guide-card__icon"><PortalIcon name="spark" /></div>
                  <p className="eyebrow eyebrow--ink">UN FLUJO GUIADO</p>
                  <h3 id="guide-title">Primero confirmamos, después avanzas.</h3>
                  <p>La consulta de vehículo aparece cuando el SRI confirma la clasificación requerida en esta etapa.</p>
                  <ol className="guide-list">
                    <li><span>1</span><div><strong>Datos de consulta</strong><small>Correo y RUC</small></div></li>
                    <li><span>2</span><div><strong>Validación SRI</strong><small>Tipo de persona</small></div></li>
                    <li><span>3</span><div><strong>Vehículo</strong><small>Consulta por placa</small></div></li>
                  </ol>
                </section>
                <div className="privacy-card">
                  <PortalIcon name="shield" />
                  <div><strong>Privacidad en el navegador</strong><p>Los datos ingresados se mantienen en memoria durante la consulta. No se guardan en almacenamiento local.</p></div>
                </div>
                <p className="provider-note">Las respuestas dependen de la disponibilidad del servicio externo y de los datos que este entregue.</p>
              </aside>
            </div>
          </section>
        </div>
      </main>

      <footer className="site-footer">
        <div className="site-footer__inner">
          <a className="brand brand--footer" href="#inicio" aria-label="Volver al inicio">
            <span className="brand-mark" aria-hidden="true"><svg viewBox="0 0 38 38" fill="none"><path d="M25.7 10.6a11.2 11.2 0 1 0 0 16.8" /><path d="M12.1 19h13.4M19 12.3v13.4" /></svg></span>
            <span className="brand-copy"><span className="brand-name">Consultas <b>SRI · ANT</b></span><span className="brand-caption">Proyecto académico independiente</span></span>
          </a>
          <div className="site-footer__disclaimer"><span>Una experiencia académica · No es un portal institucional</span><a href="#inicio">Volver arriba <PortalIcon name="arrow-up-right" /></a></div>
        </div>
      </footer>
    </div>
  )
}

export default App
