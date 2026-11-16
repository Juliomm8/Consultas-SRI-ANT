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
  const [emailTouched, setEmailTouched] = useState(false)
  const [rucTouched, setRucTouched] = useState(false)

  const emailError = emailTouched ? validateEmail(email) : ''
  const rucError = rucTouched ? validateRuc(ruc) : ''
  const emailValid = emailTouched && !emailError
  const rucValid = rucTouched && !rucError
  const formValid = !validateEmail(email) && !validateRuc(ruc)

  function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setEmailTouched(true)
    setRucTouched(true)

    if (validateEmail(email)) {
      window.requestAnimationFrame(() => document.getElementById('taxpayer-email')?.focus())
      return
    }
    if (validateRuc(ruc)) {
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
          <span className={`input-shell${emailError ? ' input-shell--error' : emailValid ? ' input-shell--valid' : ''}`} data-validation-state={emailTouched ? emailValid ? 'valid' : 'invalid' : undefined}>
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
                setEmailTouched(true)
              }}
              onBlur={() => setEmailTouched(true)}
              placeholder="nombre@ejemplo.com"
              type="email"
              value={email}
              aria-describedby={emailError ? 'taxpayer-email-error' : 'taxpayer-email-hint'}
              aria-invalid={Boolean(emailError)}
            />
            {emailTouched && <span className={`input-shell__status${emailValid ? ' input-shell__status--valid' : ' input-shell__status--invalid'}`} data-validation-icon={emailValid ? 'check-circle' : 'x-circle'}>
              <PortalIcon name={emailValid ? 'check-circle' : 'x-circle'} />
            </span>}
          </span>
          {emailError
            ? <span className="field-error" id="taxpayer-email-error">{emailError}</span>
            : <span className="field-hint" id="taxpayer-email-hint">Se valida en esta aplicación; no se envía al SRI.</span>}
        </label>

        <label className="form-field" htmlFor="taxpayer-ruc">
          <span className="form-label"><span>RUC</span><span className="required-mark">Obligatorio</span></span>
          <span className={`input-shell${rucError ? ' input-shell--error' : rucValid ? ' input-shell--valid' : ''}`} data-validation-state={rucTouched ? rucValid ? 'valid' : 'invalid' : undefined}>
            <PortalIcon name="id" />
            <input
              autoComplete="off"
              disabled={disabled || isLoading}
              id="taxpayer-ruc"
              inputMode="numeric"
              name="ruc"
              required
              onChange={(event) => {
                setRuc(event.target.value)
                setRucTouched(true)
              }}
              onBlur={() => setRucTouched(true)}
              pattern="[0-9]{13}"
              placeholder="13 dígitos"
              type="text"
              value={ruc}
              aria-describedby={rucError ? 'taxpayer-ruc-error' : 'taxpayer-ruc-hint'}
              aria-invalid={Boolean(rucError)}
            />
            {rucTouched && <span className={`input-shell__status${rucValid ? ' input-shell__status--valid' : ' input-shell__status--invalid'}`} data-validation-icon={rucValid ? 'check-circle' : 'x-circle'}>
              <PortalIcon name={rucValid ? 'check-circle' : 'x-circle'} />
            </span>}
          </span>
          {rucError
            ? <span className="field-error" id="taxpayer-ruc-error">{rucError}</span>
            : <span className="field-hint" id="taxpayer-ruc-hint">Formato estructural de 13 dígitos; el SRI confirma existencia.</span>}
        </label>
      </div>
      <div className="form-footer">
        <p className="privacy-note"><PortalIcon name="shield" /> La consulta usa el servicio SRI disponible.</p>
        <button className="button button--primary" disabled={disabled || isLoading || !formValid} type="submit">
          {isLoading
            ? <><span className="spinner" aria-hidden="true" /> Consultando…</>
            : <>Consultar contribuyente <PortalIcon name="arrow-right" />
              <span className="visually-hidden"> en el SRI</span></>}
        </button>
      </div>
    </form>
  )
}

function validateEmail(value: string): string {
  if (!value) return 'El correo electrónico es obligatorio.'
  if (value.length > 254 || !/^[^\s@]+@[^\s@]+$/.test(value)) {
    return 'Ingresa un correo electrónico válido, por ejemplo nombre@dominio (máximo 254 caracteres).'
  }
  return ''
}

function validateRuc(value: string): string {
  return /^[0-9]{13}$/.test(value)
    ? ''
    : 'El RUC debe contener exactamente 13 dígitos ASCII, sin letras ni espacios.'
}
