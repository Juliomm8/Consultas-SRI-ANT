import { postJson } from '../../../shared/api/apiClient'
import type { ApiSuccess } from '../../../shared/api/contracts'

export interface ConsultarPuntosRequest {
  cedula: string
}

export interface PuntosDemoData {
  totalPuntos: number
}

export interface PuntosDemoMeta {
  requestId: string
  source: string
  retrievedAt: string
  fetchedAt: string
  stale: boolean
  warning: string | null
}

const safePointsErrorMessages: Readonly<Record<string, string>> = {
  VALIDATION_ERROR: 'La cédula debe contener exactamente 10 dígitos ASCII.',
  MALFORMED_JSON: 'No se pudo procesar la solicitud de demostración.',
  NOT_FOUND: 'El escenario simulado no contiene información de puntos.',
  PROVIDER_BAD_RESPONSE: 'El servicio de puntos de licencia devolvió una respuesta no utilizable.',
  PROVIDER_UNAVAILABLE: 'El servicio de puntos de licencia no está disponible.',
  PROVIDER_TIMEOUT: 'El servicio de puntos de licencia excedió el tiempo de espera.',
  INTERNAL_ERROR: 'Ocurrió un error interno en la demostración.',
  INVALID_API_RESPONSE: 'El backend local devolvió una respuesta incompatible con la demostración.',
}

export function consultarPuntosSimulados(cedula: string): Promise<ApiSuccess<PuntosDemoData>> {
  return postJson<ConsultarPuntosRequest, PuntosDemoData>(
    '/licencias/puntos/consultas',
    { cedula },
    {
      baseUrl: '/api/v1',
      safeMessages: safePointsErrorMessages,
    },
  )
}
