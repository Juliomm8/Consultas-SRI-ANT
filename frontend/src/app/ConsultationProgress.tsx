import { PortalIcon } from '../shared/components/PortalIcon'

export function ConsultationProgress({
  taxpayerComplete,
  vehicleComplete,
}: {
  taxpayerComplete: boolean
  vehicleComplete: boolean
}) {
  return (
    <nav className="stepper" aria-label="Etapas de consulta">
      <ol>
        <li className={taxpayerComplete ? 'step step--complete' : 'step step--current'} aria-current={taxpayerComplete ? undefined : 'step'}>
          <span className="step__mark" aria-hidden="true">{taxpayerComplete ? <PortalIcon name="check" /> : null}</span>
          <span className="step__copy"><strong>Contribuyente</strong><small>Consulta de registro y clasificación</small></span>
        </li>
        <li className={vehicleComplete ? 'step step--complete' : taxpayerComplete ? 'step step--current' : 'step step--pending'} aria-current={taxpayerComplete && !vehicleComplete ? 'step' : undefined}>
          <span className="step__mark" aria-hidden="true">{vehicleComplete ? <PortalIcon name="check" /> : null}</span>
          <span className="step__copy"><strong>Vehículo</strong><small>{taxpayerComplete ? 'Consulta SRI' : 'Después de validar'}</small></span>
        </li>
      </ol>
    </nav>
  )
}
