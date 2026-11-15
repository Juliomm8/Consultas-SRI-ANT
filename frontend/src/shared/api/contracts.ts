export type JsonValue =
  | string
  | number
  | boolean
  | null
  | JsonValue[]
  | { [key: string]: JsonValue }

export interface ApiMeta {
  requestId: string
  source: string
  retrievedAt: string
}

export interface ApiSuccess<T> {
  data: T
  meta: ApiMeta
}

export interface ApiErrorDetail {
  code: string
  message: string
  requestId: string
}

export interface ApiErrorEnvelope {
  error: ApiErrorDetail
}

export interface ContribuyenteResponse {
  ruc: string
  tipoPersona: 'NATURAL' | 'JURIDICA' | 'NO_DETERMINADA'
  datosSRI: Record<string, JsonValue>
}

export interface VehiculoResponse {
  camposSRI: Record<string, JsonValue>
}
