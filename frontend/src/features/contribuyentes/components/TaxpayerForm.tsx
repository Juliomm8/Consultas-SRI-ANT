import { useState, type FormEvent } from 'react'
import { PortalIcon } from '../../../shared/components/PortalIcon'

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

    if (nextEmailError) {
      window.requestAnimationFrame(() => document.getElementById('taxpayer-email')?.focus())
      return
    }
    if (nextRucError) {
      window.requestAnimationFrame(() => document.getElementById('taxpayer-ruc')?.focus())
      return
    }
    onSubmit(email, ruc)
  }

  return (
    <form className="consultation-form" onSubmit={submit} noValidate aria-busy={isLoading}>
      <div className="form-grid">
        <label className="form-field" htmlFor="taxpayer-email">
          <span className="form-label"><span>Correo electrónico</span><span className="required-mark">Obligatorio</span></span>
          <span className={`input-shell${emailError ? ' input-shell--error' : ''}`}>
            <PortalIcon name="mail" />
            <input
              autoComplete="email"
              disabled={disabled || isLoading}
              id="taxpayer-email"
              maxLength={254}
              name="email"
              required
              onChange={(event) => {
                setEmail(event.target.value)
                setEmailError('')
              }}
              placeholder="nombre@ejemplo.com"
              type="email"
              value={email}
              aria-describedby={emailError ? 'taxpayer-email-error' : 'taxpayer-email-hint'}
              aria-invalid={Boolean(emailError)}
            />
          </span>
          {emailError
            ? <span className="field-error" id="taxpayer-email-error" aria-live="polite">{emailError}</span>
            : <span className="field-hint" id="taxpayer-email-hint">Se valida en esta aplicación; no se envía al SRI.</span>}
        </label>

        <label className="form-field" htmlFor="taxpayer-ruc">
          <span className="form-label"><span>RUC</span><span className="required-mark">Obligatorio</span></span>
          <span className={`input-shell${rucError ? ' input-shell--error' : ''}`}>
            <PortalIcon name="id" />
            <input
              autoComplete="off"
              disabled={disabled || isLoading}
              id="taxpayer-ruc"
              inputMode="numeric"
              maxLength={13}
              name="ruc"
              required
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
          </span>
          {rucError
            ? <span className="field-error" id="taxpayer-ruc-error" aria-live="polite">{rucError}</span>
            : <span className="field-hint" id="taxpayer-ruc-hint">Formato estructural de 13 dígitos; el SRI confirma existencia.</span>}
        </label>
      </div>
      <div className="form-footer">
        <p className="privacy-note"><PortalIcon name="shield" /> La consulta usa el servicio SRI disponible.</p>
        <button className="button button--primary" disabled={disabled || isLoading} type="submit">
          {isLoading
            ? <><span className="spinner" aria-hidden="true" /> Consultando…</>
            : <>Consultar contribuyente <PortalIcon name="arrow-right" />
              <span className="visually-hidden"> en el SRI</span></>}
        </button>
      </div>
    </form>
  )
}
