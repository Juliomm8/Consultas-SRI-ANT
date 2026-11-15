import { postJson } from '../../../shared/api/apiClient'
import type { ApiSuccess, VehiculoResponse } from '../../../shared/api/contracts'

export interface ConsultarVehiculoRequest {
  placa: string
}

export function consultarVehiculos(
  request: ConsultarVehiculoRequest,
): Promise<ApiSuccess<VehiculoResponse[]>> {
  return postJson('/vehiculos/consultas', request)
}
