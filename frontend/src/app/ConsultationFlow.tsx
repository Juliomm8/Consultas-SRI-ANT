import type { ApiSuccess, ContribuyenteResponse, VehiculoResponse } from '../shared/api/contracts'
import { FeedbackNotice, type ConsultationIssue } from '../shared/components/FeedbackNotice'
import { PortalIcon } from '../shared/components/PortalIcon'
import { ConsultationProgress } from './ConsultationProgress'
import { TaxpayerForm } from '../features/contribuyentes/components/TaxpayerForm'
import { TaxpayerResult } from '../features/contribuyentes/components/TaxpayerResult'
import { VehicleForm } from '../features/vehiculos/components/VehicleForm'
import { VehicleResult } from '../features/vehiculos/components/VehicleResult'

export function ConsultationFlow({
  taxpayerResult,
  vehicleResult,
  taxpayerLoading,
  vehicleLoading,
  issue,
  onTaxpayerSubmit,
  onVehicleSubmit,
  onStartNew,
  onSearchAgain,
  setOutcomeRef,
}: {
  taxpayerResult: ApiSuccess<ContribuyenteResponse> | null
  vehicleResult: ApiSuccess<VehiculoResponse[]> | null
  taxpayerLoading: boolean
  vehicleLoading: boolean
  issue: ConsultationIssue | null
  onTaxpayerSubmit: (email: string, ruc: string) => void
  onVehicleSubmit: (plate: string) => void
  onStartNew: () => void
  onSearchAgain: () => void
  setOutcomeRef: (element: HTMLDivElement | null) => void
}) {
  return (
    <section className="consultation-section" id="consulta" aria-labelledby="consultation-title" tabIndex={-1}>
      <header className="consultation-intro">
        <div>
          <p className="eyebrow eyebrow--ink"><span className="eyebrow__rule" /> Consulta SRI</p>
          <h2 id="consultation-title">Un recorrido, dos etapas.</h2>
          <p>La búsqueda de vehículos se habilita después de confirmar una persona natural en el SRI.</p>
        </div>
        <p className="consultation-intro__note"><strong>01 / 02</strong><span>Contribuyente → Vehículo</span></p>
      </header>

      <ConsultationProgress taxpayerComplete={Boolean(taxpayerResult)} vehicleComplete={Boolean(vehicleResult)} />

      <div className="consultation-layout">
        <div className="consultation-main">
          <section className="workflow-panel" aria-labelledby="taxpayer-title">
            <div className="workflow-panel__heading">
              <div className="workflow-panel__title-wrap">
                <span className="workflow-panel__step" aria-hidden="true">01</span>
                <div>
                  <p className="workflow-panel__eyebrow">PRIMERA ETAPA</p>
                  <h3 id="taxpayer-title" tabIndex={-1}>Consulta de contribuyente</h3>
                  <p>Ingresa correo y RUC para consultar la información disponible.</p>
                </div>
              </div>
              <span className="source-chip"><span /> SRI</span>
            </div>

            {issue?.stage === 'taxpayer' && <FeedbackNotice issue={issue} focusRef={setOutcomeRef} />}
            {taxpayerResult
              ? <TaxpayerResult result={taxpayerResult} onStartNew={onStartNew} focusRef={setOutcomeRef} />
              : <TaxpayerForm disabled={false} isLoading={taxpayerLoading} onSubmit={onTaxpayerSubmit} />}
          </section>

          {taxpayerResult && (
            <section className="workflow-panel workflow-panel--vehicle" aria-labelledby="vehicle-title">
              <div className="workflow-panel__heading">
                <div className="workflow-panel__title-wrap">
                  <span className="workflow-panel__step" aria-hidden="true">02</span>
                  <div>
                    <p className="workflow-panel__eyebrow">SEGUNDA ETAPA</p>
                    <h3 id="vehicle-title" tabIndex={-1}>Consulta de vehículo</h3>
                    <p>Busca los datos disponibles en el SRI mediante la placa.</p>
                  </div>
                </div>
                <span className="source-chip"><span /> SRI</span>
              </div>

              {issue?.stage === 'vehicle' && <FeedbackNotice issue={issue} focusRef={setOutcomeRef} />}
              {vehicleResult
                ? <VehicleResult
                    vehicles={vehicleResult.data}
                    meta={vehicleResult.meta}
                    onSearchAgain={onSearchAgain}
                    focusRef={setOutcomeRef}
                  />
                : <VehicleForm isLoading={vehicleLoading} onSubmit={onVehicleSubmit} />}
            </section>
          )}

          {!taxpayerResult && (
            <div className="next-step-card" aria-label="Siguiente etapa bloqueada">
              <span className="next-step-card__number">02</span>
              <div><strong>Después: consulta de vehículo</strong><p>Se habilita al confirmar la clasificación requerida.</p></div>
              <span className="next-step-card__lock" aria-hidden="true"><PortalIcon name="shield" /></span>
            </div>
          )}
        </div>

        <aside className="workflow-aside" aria-label="Información de la consulta">
          <section className="guide-card" aria-labelledby="guide-title">
            <p className="eyebrow eyebrow--ink"><span className="eyebrow__rule" /> Cómo funciona</p>
            <h3 id="guide-title">Primero confirmamos. Después avanzas.</h3>
            <p>El resultado del SRI determina si puedes continuar a la consulta vehicular.</p>
            <ol className="guide-list">
              <li><span>01</span><div><strong>Contribuyente</strong><small>Correo y RUC · clasificación</small></div></li>
              <li><span>02</span><div><strong>Vehículo</strong><small>Consulta disponible por placa</small></div></li>
            </ol>
          </section>
          <div className="privacy-card">
            <PortalIcon name="shield" />
            <div><strong>Datos en memoria temporal</strong><p>Los datos se mantienen en el navegador durante la consulta y no se guardan en almacenamiento local.</p></div>
          </div>
          <p className="provider-note">Las respuestas dependen de la disponibilidad y los datos entregados por el proveedor.</p>
        </aside>
      </div>
    </section>
  )
}
