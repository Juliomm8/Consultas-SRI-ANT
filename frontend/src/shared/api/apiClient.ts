import type { ApiSuccess } from './contracts'

const DEFAULT_API_BASE_URL = '/api/v1'
const REQUEST_TIMEOUT_MS = 15_000
const REQUEST_ID_PATTERN = /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i

const safeMessages = {
  VALIDATION_ERROR: 'Revisa los datos ingresados y vuelve a intentarlo.',
  MALFORMED_JSON: 'No pudimos procesar la solicitud. Inténtalo nuevamente.',
  NOT_FOUND: 'No encontramos información para la identificación consultada.',
  IDENTIFICATION_NOT_NATURAL_PERSON: 'La consulta requiere una persona natural.',
  TAXPAYER_TYPE_UNDETERMINED: 'El SRI no permitió confirmar el tipo de persona.',
  PROVIDER_BAD_RESPONSE: 'El SRI devolvió una respuesta que no pudimos procesar.',
  PROVIDER_UNAVAILABLE: 'El servicio SRI no está disponible en este momento.',
  PROVIDER_TIMEOUT: 'La consulta al SRI excedió el tiempo de espera.',
  INTERNAL_ERROR: 'Ocurrió un error interno. Inténtalo más tarde.',
} as const satisfies Record<string, string>

export class ApiRequestError extends Error {
  readonly status?: number
  readonly code?: string
  readonly requestId?: string

  constructor(
    message: string,
    status?: number,
    code?: string,
    requestId?: string,
  ) {
    super(message)
    this.name = 'ApiRequestError'
    this.status = status
    this.code = code
    this.requestId = requestId
  }
}

export interface PostJsonOptions {
  /** Use a same-origin path for features that must remain behind a local proxy. */
  baseUrl?: string
  /** Safe, feature-specific messages for the API's stable error codes. */
  safeMessages?: Readonly<Record<string, string>>
}

function apiBaseUrl(): string {
  return (import.meta.env.VITE_API_BASE_URL || DEFAULT_API_BASE_URL).replace(/\/+$/, '')
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value)
}

function safeRequestId(value: unknown): string | undefined {
  return typeof value === 'string' && REQUEST_ID_PATTERN.test(value) ? value : undefined
}

function fallbackMessage(status: number): string {
  if (status === 400) return safeMessages.VALIDATION_ERROR
  if (status === 404) return safeMessages.NOT_FOUND
  if (status === 422) return 'La solicitud no cumple una condición necesaria para continuar.'
  if (status === 502) return safeMessages.PROVIDER_BAD_RESPONSE
  if (status === 503) return safeMessages.PROVIDER_UNAVAILABLE
  if (status === 504) return safeMessages.PROVIDER_TIMEOUT
  return safeMessages.INTERNAL_ERROR
}

function messageForError(
  code: string | undefined,
  status: number,
  safeOverrides?: Readonly<Record<string, string>>,
): string {
  if (code && safeOverrides && Object.prototype.hasOwnProperty.call(safeOverrides, code)) {
    const override = safeOverrides[code]
    if (typeof override === 'string') return override
  }
  if (code && Object.prototype.hasOwnProperty.call(safeMessages, code)) {
    return safeMessages[code as keyof typeof safeMessages]
  }
  return fallbackMessage(status)
}

async function readJson(response: Response): Promise<unknown> {
  try {
    return await response.json()
  } catch {
    return undefined
  }
}

export async function postJson<TRequest, TData>(
  path: string,
  request: TRequest,
  options: PostJsonOptions = {},
): Promise<ApiSuccess<TData>> {
  const controller = new AbortController()
  const timeoutId = window.setTimeout(() => controller.abort(), REQUEST_TIMEOUT_MS)

  try {
    const baseUrl = (options.baseUrl ?? apiBaseUrl()).replace(/\/+$/, '')
    const response = await fetch(`${baseUrl}${path}`, {
      method: 'POST',
      headers: { Accept: 'application/json', 'Content-Type': 'application/json' },
      body: JSON.stringify(request),
      credentials: 'omit',
      cache: 'no-store',
      signal: controller.signal,
    })
    const requestIdFromHeader = safeRequestId(response.headers.get('X-Request-Id'))
    const payload = await readJson(response)

    if (!response.ok) {
      const detail = isRecord(payload) && isRecord(payload.error) ? payload.error : undefined
      const code = typeof detail?.code === 'string' ? detail.code : undefined
      const requestId = safeRequestId(detail?.requestId) ?? requestIdFromHeader
      const message = messageForError(code, response.status, options.safeMessages)
      throw new ApiRequestError(message, response.status, code, requestId)
    }

    if (!isRecord(payload) || !('data' in payload) || !isRecord(payload.meta)) {
      throw new ApiRequestError('El backend devolvió una respuesta no válida.', 502, 'INVALID_API_RESPONSE')
    }

    const meta = payload.meta
    if (
      typeof meta.requestId !== 'string' ||
      typeof meta.source !== 'string' ||
      typeof meta.retrievedAt !== 'string'
    ) {
      throw new ApiRequestError('El backend devolvió una respuesta no válida.', 502, 'INVALID_API_RESPONSE')
    }

    return payload as unknown as ApiSuccess<TData>
  } catch (error) {
    if (error instanceof ApiRequestError) throw error
    if (controller.signal.aborted) {
      throw new ApiRequestError('La solicitud tardó demasiado. Inténtalo nuevamente.', 0, 'REQUEST_TIMEOUT')
    }
    throw new ApiRequestError(
      'No pudimos conectar con el backend. Comprueba que esté ejecutándose e inténtalo de nuevo.',
      0,
      'NETWORK_ERROR',
    )
  } finally {
    window.clearTimeout(timeoutId)
  }
}
