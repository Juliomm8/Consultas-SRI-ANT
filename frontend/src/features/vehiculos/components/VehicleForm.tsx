import { useState, type FormEvent } from 'react'
import { PortalIcon } from '../../../shared/components/PortalIcon'

interface VehicleFormProps {
  isLoading: boolean
  onSubmit: (plate: string) => void
}

export function VehicleForm({ isLoading, onSubmit }: VehicleFormProps) {
  const [plate, setPlate] = useState('')
  const [plateTouched, setPlateTouched] = useState(false)
  const plateError = plateTouched ? validatePlate(plate) : ''
  const plateValid = plateTouched && !plateError

  function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setPlateTouched(true)
    if (validatePlate(plate)) {
      window.requestAnimationFrame(() => document.getElementById('vehicle-plate')?.focus())
      return
    }
    onSubmit(plate)
  }

  return (
    <form className="consultation-form" onSubmit={submit} noValidate aria-busy={isLoading}>
      <div className="form-grid form-grid--single">
        <label className="form-field" htmlFor="vehicle-plate">
          <span className="form-label"><span>Placa</span><span className="required-mark">Obligatorio</span></span>
          <span className={`input-shell${plateError ? ' input-shell--error' : plateValid ? ' input-shell--valid' : ''}`} data-validation-state={plateTouched ? plateValid ? 'valid' : 'invalid' : undefined}>
            <PortalIcon name="car" />
            <input
              autoComplete="off"
              disabled={isLoading}
              id="vehicle-plate"
              name="placa"
              required
              onChange={(event) => {
                setPlate(event.target.value)
                setPlateTouched(true)
              }}
              onBlur={() => setPlateTouched(true)}
              placeholder="Ej. ABC1234"
              spellCheck={false}
              type="text"
              value={plate}
              aria-describedby={plateError ? 'vehicle-plate-error' : 'vehicle-plate-hint'}
              aria-invalid={Boolean(plateError)}
            />
            {plateTouched && <span className={`input-shell__status${plateValid ? ' input-shell__status--valid' : ' input-shell__status--invalid'}`} data-validation-icon={plateValid ? 'check-circle' : 'x-circle'}>
              <PortalIcon name={plateValid ? 'check-circle' : 'x-circle'} />
            </span>}
          </span>
          {plateError
            ? <span className="field-error" id="vehicle-plate-error">{plateError}</span>
            : <span className="field-hint" id="vehicle-plate-hint">Formato estructural; el servicio consulta la información disponible para esta placa.</span>}
        </label>
      </div>
      <div className="form-footer">
        <p className="privacy-note"><PortalIcon name="shield" /> La consulta no confirma titularidad.</p>
        <button className="button button--primary" disabled={isLoading || !plateValid} type="submit">
          {isLoading
            ? <><span className="spinner" aria-hidden="true" /> Consultando…</>
            : <>Consultar vehículo <PortalIcon name="arrow-right" /></>}
        </button>
      </div>
    </form>
  )
}

function validatePlate(value: string): string {
  return /^[A-Za-z0-9]+$/.test(value)
    ? ''
    : 'La placa es obligatoria y solo puede contener letras y números.'
}
