import { createServer } from 'node:http'

export const syntheticSriFixtures = Object.freeze({
  successRuc: '1234567890123',
  providerFailureRuc: '9999999999999',
  plate: 'E2E1234',
  email: 'e2e-user@example.invalid',
  providerBodyMarker: 'SYNTHETIC_PROVIDER_BODY',
})

const operationByPath = new Map([
  ['/sri/taxpayer-existence', 'taxpayer-existence'],
  ['/sri/taxpayer-details', 'taxpayer-details'],
  ['/sri/vehicle', 'vehicle-lookup'],
])

function sendJson(response, status, value) {
  response.writeHead(status, { 'content-type': 'application/json; charset=utf-8' })
  response.end(JSON.stringify(value))
}

/**
 * Reuses the deterministic SRI fixture responses from the E2E runner.
 * The server binds to loopback and records operation metadata without query values.
 */
export function createSriStub() {
  const requests = []
  const server = createServer((request, response) => {
    const url = new URL(request.url ?? '/', 'http://127.0.0.1')
    if (request.method === 'POST' && url.pathname === '/__test/reset') {
      requests.length = 0
      response.writeHead(204)
      response.end()
      return
    }
    if (request.method === 'GET' && url.pathname === '/__test/requests') {
      sendJson(response, 200, requests)
      return
    }

    const operation = operationByPath.get(url.pathname)
    if (!operation || request.method !== 'GET') {
      sendJson(response, 404, { message: 'Synthetic route not found.' })
      return
    }

    const parameterNames = [...url.searchParams.keys()].sort()
    let status = 200
    if (operation === 'taxpayer-existence'
        && url.searchParams.get('numeroRuc') === syntheticSriFixtures.providerFailureRuc) {
      status = 503
    }
    requests.push({ operation, method: request.method, parameterNames, status })

    if (operation === 'taxpayer-existence') {
      const validNames = parameterNames.length === 1 && parameterNames[0] === 'numeroRuc'
      if (!validNames || !url.searchParams.has('numeroRuc')) {
        sendJson(response, 400, { message: 'Synthetic request shape rejected.' })
        return
      }
      if (status === 503) {
        sendJson(response, status, { privateProviderMarker: syntheticSriFixtures.providerBodyMarker })
        return
      }
      sendJson(response, 200, true)
      return
    }

    if (operation === 'taxpayer-details') {
      if (parameterNames.length !== 1 || parameterNames[0] !== 'ruc') {
        sendJson(response, 400, { message: 'Synthetic request shape rejected.' })
        return
      }
      const requestedRuc = url.searchParams.get('ruc')
      if (requestedRuc !== syntheticSriFixtures.successRuc) {
        sendJson(response, 404, { message: 'Synthetic taxpayer not found.' })
        return
      }
      sendJson(response, 200, [{
        numeroRuc: syntheticSriFixtures.successRuc,
        razonSocial: 'CONTRIBUYENTE E2E SINTÉTICO',
        estadoContribuyenteRuc: 'ACTIVO SINTÉTICO',
        actividadEconomicaPrincipal: 'ACTIVIDAD SINTÉTICA',
        tipoContribuyente: 'PERSONA NATURAL',
        regimen: 'RÉGIMEN SINTÉTICO',
        categoria: 2.5,
        obligadoLlevarContabilidad: false,
        agenteRetencion: true,
        contribuyenteEspecial: null,
        informacionFechasContribuyente: {
          fechaInicioActividades: '2025-01-02',
          fechaCese: null,
          fechaReinicioActividades: null,
          fechaActualizacion: '2026-01-03',
        },
        representantesLegales: [],
        motivoCancelacionSuspension: null,
        contribuyenteFantasma: false,
        transaccionesInexistente: [],
      }])
      return
    }

    if (parameterNames.length !== 1 || parameterNames[0] !== 'numeroPlacaCampvCpn'
        || url.searchParams.get('numeroPlacaCampvCpn') !== syntheticSriFixtures.plate) {
      sendJson(response, 400, { message: 'Synthetic request shape rejected.' })
      return
    }
    sendJson(response, 200, {
      codigoVehiculo: 123,
      numeroPlaca: syntheticSriFixtures.plate,
      numeroCamvCpn: null,
      colorVehiculo1: 'BLANCO SINTÉTICO',
      colorVehiculo2: null,
      cilindraje: 1600,
      nombreClase: 'CLASE SINTÉTICA',
      descripcionMarca: 'MARCA E2E SINTÉTICA',
      descripcionModelo: 'MODELO E2E SINTÉTICO',
      anioAuto: 2024,
      descripcionPais: 'PAÍS SINTÉTICO',
      mensajeMotivoAuto: null,
      aplicaCuota: false,
      fechaUltimaMatricula: '2025-02-03',
      fechaCaducidadMatricula: null,
      fechaCompraRegistro: '2024-02-03',
      fechaRevision: null,
      descripcionCanton: 'CANTÓN SINTÉTICO',
      descripcionServicio: 'SERVICIO SINTÉTICO',
      ultimoAnioPagado: 2025,
      prohibidoEnajenar: false,
      observacion: null,
      estadoExoneracion: null,
      verMensajeElectricos: true,
    })
  })

  return new Promise((resolve, reject) => {
    server.once('error', reject)
    server.listen(0, '127.0.0.1', () => {
      const address = server.address()
      if (!address || typeof address === 'string') {
        reject(new Error('Could not bind the local SRI fixture server.'))
        return
      }
      resolve({
        server,
        baseUrl: `http://127.0.0.1:${address.port}`,
        getRequests: () => requests.map(({ operation, method, parameterNames, status }) => ({
          operation, method, parameterNames: [...parameterNames], status,
        })),
        getRequestCount: () => requests.length,
      })
    })
  })
}
