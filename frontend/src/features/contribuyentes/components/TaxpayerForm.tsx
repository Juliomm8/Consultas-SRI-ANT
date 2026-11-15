import { useState, type FormEvent } from 'react'

interface TaxpayerFormProps {
  disabled: boolean
  isLoading: boolean
  onSubmit: (email: string, ruc: string) => void
}

export function TaxpayerForm({ disabled, isLoading, onSubmit }: TaxpayerFormProps) {
  const [email, setEmail] = useState('')
  const [ruc, setRuc] = useState('')
  const [emailError, setEmailError] = useState('')
  const [rucError, setRucError] = useState('')

  function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const nextEmailError = !email
      ? 'El correo electrónico es obligatorio.'
      : email.length > 254 || !/^[^\s@]+@[^\s@]+$/.test(email)
        ? 'Ingresa un correo electrónico válido (máximo 254 caracteres).'
        : ''
    const nextRucError = /^[0-9]{13}$/.test(ruc)
      ? ''
      : 'El RUC debe contener exactamente 13 dígitos.'
    setEmailError(nextEmailError)
    setRucError(nextRucError)

    if (!nextEmailError && !nextRucError) onSubmit(email, ruc)
  }

  return (
    <form className="consultation-form" onSubmit={submit} noValidate>
      <div className="form-grid">
        <label className="form-field" htmlFor="taxpayer-email">
          <span>Correo electrónico <span className="required-mark">*</span></span>
          <input
            autoComplete="email"
            disabled={disabled || isLoading}
            id="taxpayer-email"
            maxLength={254}
            name="email"
            onChange={(event) => {
              setEmail(event.target.value)
              setEmailError('')
            }}
            placeholder="nombre@ejemplo.com"
            type="email"
            value={email}
            aria-describedby={emailError ? 'taxpayer-email-error' : undefined}
            aria-invalid={Boolean(emailError)}
          />
          {emailError && <span className="field-error" id="taxpayer-email-error">{emailError}</span>}
        </label>

        <label className="form-field" htmlFor="taxpayer-ruc">
          <span>RUC <span className="required-mark">*</span></span>
          <input
            autoComplete="off"
            disabled={disabled || isLoading}
            id="taxpayer-ruc"
            inputMode="numeric"
            maxLength={13}
            name="ruc"
            onChange={(event) => {
              setRuc(event.target.value)
              setRucError('')
            }}
            pattern="[0-9]{13}"
            placeholder="13 dígitos"
            type="text"
            value={ruc}
            aria-describedby={rucError ? 'taxpayer-ruc-error' : 'taxpayer-ruc-hint'}
            aria-invalid={Boolean(rucError)}
          />
          {rucError
            ? <span className="field-error" id="taxpayer-ruc-error">{rucError}</span>
            : <span className="field-hint" id="taxpayer-ruc-hint">Validación de formato; el SRI confirma la existencia.</span>}
        </label>
      </div>
      <div className="form-footer">
        <p className="privacy-note">El correo se valida en esta aplicación y no se envía al SRI.</p>
        <button className="button button--primary" disabled={disabled || isLoading} type="submit">
          {isLoading ? <><span className="spinner" aria-hidden="true" /> Consultando…</> : <>Consultar contribuyente <span aria-hidden="true">→</span></>}
        </button>
      </div>
    </form>
  )
}
