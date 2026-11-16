import { useEffect, useRef, useState } from 'react'
import { ConsultationFlow } from './app/ConsultationFlow'
import { ANT_DEMO_AVAILABLE } from './features/ant/demo/demoAccess'
import { AntPointsDemo } from './features/ant/demo/AntPointsDemo'
import { consultarContribuyente } from './features/contribuyentes/api/contribuyentesApi'
import { consultarVehiculos } from './features/vehiculos/api/vehiculosApi'
import { SiteFooter } from './shared/components/SiteFooter'
import { HeroSection } from './shared/components/HeroSection'
import { SiteHeader } from './shared/components/SiteHeader'
import { ServicesSection } from './shared/components/ServicesSection'
import { ApiRequestError } from './shared/api/apiClient'
import type { ApiSuccess, ContribuyenteResponse, VehiculoResponse } from './shared/api/contracts'
import type { ConsultationIssue } from './shared/components/FeedbackNotice'
import './App.css'

function App() {
  const [taxpayerResult, setTaxpayerResult] = useState<ApiSuccess<ContribuyenteResponse> | null>(null)
  const [vehicleResult, setVehicleResult] = useState<ApiSuccess<VehiculoResponse[]> | null>(null)
  const [taxpayerLoading, setTaxpayerLoading] = useState(false)
  const [vehicleLoading, setVehicleLoading] = useState(false)
  const [issue, setIssue] = useState<ConsultationIssue | null>(null)
  const [showAntDemo, setShowAntDemo] = useState(false)
  const outcomeRef = useRef<HTMLDivElement | null>(null)

  useEffect(() => {
    if (issue || taxpayerResult || vehicleResult) outcomeRef.current?.focus()
  }, [issue, taxpayerResult, vehicleResult])

  async function handleTaxpayerSubmit(email: string, ruc: string) {
    setIssue(null)
    setTaxpayerLoading(true)
    try {
      const result = await consultarContribuyente({ email, ruc })
      if (result.data.tipoPersona !== 'NATURAL') {
        setIssue({
          stage: 'taxpayer',
          title: result.data.tipoPersona === 'JURIDICA' ? 'La consulta requiere una persona natural' : 'No se pudo confirmar la clasificación',
          message: result.data.tipoPersona === 'JURIDICA'
            ? 'La identificación consultada no corresponde a una persona natural.'
            : 'El SRI no permitió confirmar el tipo de persona.',
          requestId: result.meta.requestId,
        })
        return
      }
      setTaxpayerResult(result)
    } catch (requestError) {
      setIssue(requestError instanceof ApiRequestError
        ? {
            stage: 'taxpayer',
            message: requestError.message,
            requestId: requestError.requestId,
            code: requestError.code,
            status: requestError.status,
          }
        : { stage: 'taxpayer', message: 'No pudimos completar la consulta. Inténtalo nuevamente.' })
    } finally {
      setTaxpayerLoading(false)
    }
  }

  async function handleVehicleSubmit(plate: string) {
    setIssue(null)
    setVehicleLoading(true)
    try {
      setVehicleResult(await consultarVehiculos({ placa: plate }))
    } catch (requestError) {
      setIssue(requestError instanceof ApiRequestError
        ? {
            stage: 'vehicle',
            message: requestError.message,
            requestId: requestError.requestId,
            code: requestError.code,
            status: requestError.status,
          }
        : { stage: 'vehicle', message: 'No pudimos completar la consulta. Inténtalo nuevamente.' })
    } finally {
      setVehicleLoading(false)
    }
  }

  function startNewConsultation() {
    setTaxpayerResult(null)
    setVehicleResult(null)
    setIssue(null)
    window.requestAnimationFrame(() => document.getElementById('taxpayer-title')?.focus())
  }

  function searchAnotherVehicle() {
    setVehicleResult(null)
    setIssue(null)
    window.requestAnimationFrame(() => document.getElementById('vehicle-title')?.focus())
  }

  if (ANT_DEMO_AVAILABLE && showAntDemo) {
    return <AntPointsDemo onBack={() => setShowAntDemo(false)} />
  }

  return (
    <div className="site-frame">
      <a className="skip-link" href="#main-content">Saltar al contenido principal</a>
      <SiteHeader antDemoAvailable={ANT_DEMO_AVAILABLE} onOpenAntDemo={() => setShowAntDemo(true)} />
      <main id="main-content" tabIndex={-1}>
        <div className="page-shell">
          <HeroSection />
          <ServicesSection antDemoAvailable={ANT_DEMO_AVAILABLE} onOpenAntDemo={() => setShowAntDemo(true)} />
          <ConsultationFlow
            taxpayerResult={taxpayerResult}
            vehicleResult={vehicleResult}
            taxpayerLoading={taxpayerLoading}
            vehicleLoading={vehicleLoading}
            issue={issue}
            onTaxpayerSubmit={handleTaxpayerSubmit}
            onVehicleSubmit={handleVehicleSubmit}
            onStartNew={startNewConsultation}
            onSearchAgain={searchAnotherVehicle}
            setOutcomeRef={(element) => { outcomeRef.current = element }}
          />
        </div>
      </main>
      <SiteFooter />
    </div>
  )
}

export default App
