import { postJson } from '../../../shared/api/apiClient'
import type { ApiSuccess, ContribuyenteResponse } from '../../../shared/api/contracts'

export interface ConsultarContribuyenteRequest {
  email: string
  ruc: string
}

export function consultarContribuyente(
  request: ConsultarContribuyenteRequest,
): Promise<ApiSuccess<ContribuyenteResponse>> {
  return postJson('/contribuyentes/consultas', request)
}
