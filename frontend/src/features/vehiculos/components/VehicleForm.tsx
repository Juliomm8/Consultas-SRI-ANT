import { useState, type FormEvent } from 'react'

interface VehicleFormProps {
  isLoading: boolean
  onSubmit: (plate: string) => void
}

export function VehicleForm({ isLoading, onSubmit }: VehicleFormProps) {
  const [plate, setPlate] = useState('')
  const [plateError, setPlateError] = useState('')

  function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const nextError = /^[A-Za-z0-9]+$/.test(plate)
      ? ''
      : 'La placa es obligatoria y solo puede contener letras y números.'
    setPlateError(nextError)
    if (!nextError) onSubmit(plate)
  }

  return (
    <form className="consultation-form" onSubmit={submit} noValidate>
      <div className="form-grid form-grid--single">
        <label className="form-field" htmlFor="vehicle-plate">
          <span>Placa <span className="required-mark">*</span></span>
          <input
            autoComplete="off"
            disabled={isLoading}
            id="vehicle-plate"
            name="placa"
            onChange={(event) => {
              setPlate(event.target.value)
              setPlateError('')
            }}
            placeholder="Ej. ABC1234"
            spellCheck={false}
            type="text"
            value={plate}
            aria-describedby={plateError ? 'vehicle-plate-error' : 'vehicle-plate-hint'}
            aria-invalid={Boolean(plateError)}
          />
          {plateError
            ? <span className="field-error" id="vehicle-plate-error">{plateError}</span>
            : <span className="field-hint" id="vehicle-plate-hint">Ingresa únicamente letras y números, como acepta el contrato.</span>}
        </label>
      </div>
      <div className="form-footer">
        <p className="privacy-note">La consulta por placa no confirma titularidad.</p>
        <button className="button button--primary" disabled={isLoading} type="submit">
          {isLoading ? <><span className="spinner" aria-hidden="true" /> Consultando…</> : <>Consultar vehículo <span aria-hidden="true">→</span></>}
        </button>
      </div>
    </form>
  )
}
