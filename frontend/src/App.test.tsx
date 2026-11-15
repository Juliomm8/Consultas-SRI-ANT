import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import App from './App'
import { postJson } from './shared/api/apiClient'

const requestId = '550e8400-e29b-41d4-a716-446655440000'
const syntheticEmail = 'usuario@ejemplo.com'
const syntheticRuc = '0000000000000'
const syntheticPlate = 'ABC1234'
const fetchMock = vi.fn<typeof fetch>()

function jsonResponse(body: unknown, status = 200, headers: Record<string, string> = {}): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json', ...headers },
  })
}

function taxpayerEnvelope(tipoPersona: string = 'NATURAL') {
  return {
    data: {
      ruc: syntheticRuc,
      tipoPersona,
      datosSRI: {
        numeroRuc: syntheticRuc,
        estadoContribuyenteRuc: 'ACTIVO',
        informacionFechasContribuyente: {
          fechaInicioActividades: '2020-01-01',
          fechaCese: null,
        },
      },
    },
    meta: { requestId, source: 'SRI', retrievedAt: '2026-11-14T20:00:00Z' },
  }
}

async function submitTaxpayer(user: ReturnType<typeof userEvent.setup>) {
  await user.type(screen.getByLabelText(/correo electrónico/i), syntheticEmail)
  await user.type(screen.getByLabelText(/^RUC/i), syntheticRuc)
  await user.click(screen.getByRole('button', { name: /consultar contribuyente/i }))
}

async function openVehicleStep(user: ReturnType<typeof userEvent.setup>) {
  fetchMock.mockResolvedValueOnce(jsonResponse(taxpayerEnvelope()))
  await submitTaxpayer(user)
  await screen.findByRole('region', { name: 'Resultado de contribuyente' })
}

beforeEach(() => {
  fetchMock.mockReset()
  vi.stubGlobal('fetch', fetchMock)
})

afterEach(() => {
  vi.unstubAllGlobals()
})

describe('Portal de consultas', () => {
  it('muestra el estado ANT pendiente y el enlace oficial sin consultar servicios', () => {
    render(<App />)

    expect(screen.getByRole('heading', { name: /tu información, más clara y a tu alcance/i })).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: /puntos de licencia ant/i })).toBeInTheDocument()
    expect(screen.getByText(/consulta automática directa de puntos todavía no está integrada/i)).toBeInTheDocument()
    expect(screen.getByText(/no consulta la ant ni muestra puntos simulados/i)).toBeInTheDocument()
    const officialLink = screen.getByRole('link', { name: /consultar el trámite oficial/i })
    expect(officialLink).toHaveAttribute('href', 'https://www.gob.ec/ant/tramites/emision-certificado-informacion-conductor')
    expect(officialLink).toHaveAttribute('target', '_blank')
    expect(officialLink).toHaveAttribute('rel', 'noopener noreferrer')
    const progress = screen.getByRole('navigation', { name: 'Etapas de consulta' })
    expect(within(progress).getAllByRole('listitem')).toHaveLength(2)
    expect(screen.getByRole('link', { name: /comenzar consulta/i })).toHaveAttribute('href', '#consulta')
    expect(screen.getByRole('navigation', { name: 'Navegación principal' })).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Saltar al contenido principal' })).toHaveAttribute('href', '#main-content')
    expect(screen.getByRole('main')).toHaveAttribute('id', 'main-content')
    expect(screen.getByRole('link', { name: /volver arriba/i })).toHaveAttribute('href', '#inicio')
    expect(screen.queryByLabelText(/cédula|pasaporte|identificación/i)).not.toBeInTheDocument()
    expect(screen.queryByText(/resultado de puntos/i)).not.toBeInTheDocument()
    expect(fetchMock).not.toHaveBeenCalled()
    expect(screen.queryByRole('button', { name: 'Abrir demostración ANT' })).not.toBeInTheDocument()
  })

  it('valida email y RUC antes de enviar', async () => {
    const user = userEvent.setup()
    render(<App />)

    expect(screen.getByLabelText(/correo electrónico/i)).toBeRequired()
    expect(screen.getByLabelText(/^RUC/i)).toBeRequired()
    await user.type(screen.getByLabelText(/correo electrónico/i), 'correo-inválido')
    await user.type(screen.getByLabelText(/^RUC/i), '12345')
    await user.click(screen.getByRole('button', { name: /consultar contribuyente/i }))

    expect(await screen.findByText(/ingresa un correo electrónico válido/i)).toBeInTheDocument()
    expect(screen.getByText(/exactamente 13 dígitos/i)).toBeInTheDocument()
    expect(fetchMock).not.toHaveBeenCalled()
  })

  it('envía el contrato real y presenta solo la clasificación natural confirmada', async () => {
    const user = userEvent.setup()
    fetchMock.mockResolvedValueOnce(jsonResponse(taxpayerEnvelope()))
    render(<App />)

    await submitTaxpayer(user)

    expect(await screen.findByText('Persona natural confirmada')).toBeInTheDocument()
    expect(screen.getByText('ACTIVO')).toBeInTheDocument()
    expect(screen.getByText('2020-01-01')).toBeInTheDocument()
    expect(screen.queryByText('fechaCese')).not.toBeInTheDocument()
    const [url, options] = fetchMock.mock.calls[0] ?? []
    expect(url).toBe('/api/v1/contribuyentes/consultas')
    expect(options?.method).toBe('POST')
    expect(options?.credentials).toBe('omit')
    expect(JSON.parse(String(options?.body))).toEqual({ email: syntheticEmail, ruc: syntheticRuc })
    expect(screen.getByText(requestId)).toBeInTheDocument()
  })

  it('muestra el estado de carga mientras espera al backend', async () => {
    const user = userEvent.setup()
    let resolveResponse: ((response: Response) => void) | undefined
    fetchMock.mockImplementationOnce(() => new Promise<Response>((resolve) => { resolveResponse = resolve }))
    render(<App />)

    await user.type(screen.getByLabelText(/correo electrónico/i), syntheticEmail)
    await user.type(screen.getByLabelText(/^RUC/i), syntheticRuc)
    await user.click(screen.getByRole('button', { name: /consultar contribuyente/i }))

    expect(screen.getByRole('button', { name: /consultando/i })).toBeDisabled()
    resolveResponse?.(jsonResponse(taxpayerEnvelope()))
    expect(await screen.findByText('Persona natural confirmada')).toBeInTheDocument()
  })

  it('no presenta una clasificación indeterminada como persona natural', async () => {
    const user = userEvent.setup()
    fetchMock.mockResolvedValueOnce(jsonResponse(taxpayerEnvelope('NO_DETERMINADA')))
    render(<App />)

    await submitTaxpayer(user)

    expect(await screen.findByText('El SRI no permitió confirmar el tipo de persona.')).toBeInTheDocument()
    expect(screen.queryByText('Persona natural confirmada')).not.toBeInTheDocument()
    expect(screen.queryByLabelText(/^Placa/i)).not.toBeInTheDocument()
  })

  it('presenta errores 422 con mensaje seguro y requestId', async () => {
    const user = userEvent.setup()
    fetchMock.mockResolvedValueOnce(jsonResponse({
      error: {
        code: 'TAXPAYER_TYPE_UNDETERMINED',
        message: `Detalle externo con ${syntheticEmail} y ${syntheticRuc}`,
        requestId,
      },
    }, 422))
    render(<App />)

    await submitTaxpayer(user)

    const alert = await screen.findByRole('alert')
    expect(alert).toHaveTextContent('El SRI no permitió confirmar el tipo de persona.')
    expect(alert).toHaveTextContent(requestId)
    expect(alert).not.toHaveTextContent(syntheticEmail)
    expect(alert).not.toHaveTextContent(syntheticRuc)
    expect(alert).not.toHaveTextContent('Detalle externo')
  })

  it('traduce HTTP 502 y descarta el cuerpo externo', async () => {
    const user = userEvent.setup()
    fetchMock.mockResolvedValueOnce(jsonResponse({ privateData: syntheticRuc, body: 'Stack trace externo' }, 502))
    render(<App />)

    await submitTaxpayer(user)

    const alert = await screen.findByRole('alert')
    expect(alert).toHaveTextContent('El SRI devolvió una respuesta que no pudimos procesar.')
    expect(alert).not.toHaveTextContent(syntheticRuc)
    expect(alert).not.toHaveTextContent('Stack trace externo')
  })

  it('traduce la falla de conexión sin imprimir detalles ni guardar datos', async () => {
    const user = userEvent.setup()
    const consoleError = vi.spyOn(console, 'error').mockImplementation(() => undefined)
    fetchMock.mockRejectedValueOnce(new Error(`network ${syntheticEmail} ${syntheticRuc}`))
    render(<App />)

    await submitTaxpayer(user)

    const alert = await screen.findByRole('alert')
    expect(alert).toHaveTextContent(/no pudimos conectar con el backend/i)
    expect(alert).not.toHaveTextContent(syntheticEmail)
    expect(alert).not.toHaveTextContent(syntheticRuc)
    expect(consoleError).not.toHaveBeenCalled()
    expect(localStorage.length).toBe(0)
  })

  it('valida la placa antes de llamar al endpoint vehicular', async () => {
    const user = userEvent.setup()
    render(<App />)
    await openVehicleStep(user)
    expect(screen.getByLabelText(/^Placa/i)).toBeRequired()
    await user.type(screen.getByLabelText(/^Placa/i), 'ABC-1234')
    await user.click(screen.getByRole('button', { name: /consultar vehículo/i }))

    expect(await screen.findByText(/solo puede contener letras y números/i)).toBeInTheDocument()
    expect(fetchMock).toHaveBeenCalledTimes(1)
  })

  it('presenta todos los vehículos y omite campos nulos', async () => {
    const user = userEvent.setup()
    render(<App />)
    await openVehicleStep(user)
    fetchMock.mockResolvedValueOnce(jsonResponse({
      data: [
        { camposSRI: { codigoVehiculo: 100, numeroPlaca: syntheticPlate, descripcionMarca: 'Marca sintética', aplicaCuota: true, colorVehiculo1: null } },
        { camposSRI: { codigoVehiculo: 200, numeroPlaca: 'XYZ9876', anioAuto: 2020, aplicaCuota: false } },
      ],
      meta: { requestId, source: 'SRI', retrievedAt: '2026-11-14T20:00:00Z' },
    }))
    await user.type(screen.getByLabelText(/^Placa/i), syntheticPlate)
    await user.click(screen.getByRole('button', { name: /consultar vehículo/i }))

    expect(await screen.findByRole('region', { name: 'Resultado de vehículos' })).toBeInTheDocument()
    expect(screen.getByText(`Vehículo ${syntheticPlate}`)).toBeInTheDocument()
    expect(screen.getByText('Vehículo XYZ9876')).toBeInTheDocument()
    expect(screen.getByText('Marca sintética')).toBeInTheDocument()
    expect(screen.getByText('2020')).toBeInTheDocument()
    expect(screen.getAllByText('Sí')).toHaveLength(1)
    expect(screen.getAllByText('No')).toHaveLength(1)
    expect(screen.queryByText('Color principal')).not.toBeInTheDocument()
    const [url, options] = fetchMock.mock.calls[1] ?? []
    expect(url).toBe('/api/v1/vehiculos/consultas')
    expect(JSON.parse(String(options?.body))).toEqual({ placa: syntheticPlate })
    expect(fetchMock.mock.calls.every(([requestUrl]) => !String(requestUrl).includes('ant.gob.ec'))).toBe(true)
  })

  it('describe una lista vehicular vacía sin afirmar que el vehículo no existe', async () => {
    const user = userEvent.setup()
    render(<App />)
    await openVehicleStep(user)
    fetchMock.mockResolvedValueOnce(jsonResponse({
      data: [],
      meta: { requestId, source: 'SRI', retrievedAt: '2026-11-14T20:00:00Z' },
    }))
    await user.type(screen.getByLabelText(/^Placa/i), syntheticPlate)
    await user.click(screen.getByRole('button', { name: /consultar vehículo/i }))

    const status = await screen.findByRole('status')
    expect(status).toHaveTextContent('No se recibió información vehicular.')
    expect(status).toHaveTextContent('no confirma por sí sola que el vehículo no exista')
    expect(status).not.toHaveTextContent('no existe')
  })

  it('maneja errores vehiculares 503 sin exponer la placa', async () => {
    const user = userEvent.setup()
    render(<App />)
    await openVehicleStep(user)
    fetchMock.mockResolvedValueOnce(jsonResponse({
      error: { code: 'PROVIDER_UNAVAILABLE', message: syntheticPlate, requestId },
    }, 503))
    await user.type(screen.getByLabelText(/^Placa/i), syntheticPlate)
    await user.click(screen.getByRole('button', { name: /consultar vehículo/i }))

    const alert = await screen.findByRole('alert')
    expect(alert).toHaveTextContent('El servicio SRI no está disponible en este momento.')
    expect(alert).toHaveTextContent(requestId)
    expect(alert).not.toHaveTextContent(syntheticPlate)
  })

  it.each([
    [400, 'VALIDATION_ERROR', 'Revisa los datos ingresados y vuelve a intentarlo.'],
    [404, 'NOT_FOUND', 'No encontramos información para la identificación consultada.'],
    [422, 'IDENTIFICATION_NOT_NATURAL_PERSON', 'La consulta requiere una persona natural.'],
    [504, 'PROVIDER_TIMEOUT', 'La consulta al SRI excedió el tiempo de espera.'],
    [500, 'INTERNAL_ERROR', 'Ocurrió un error interno. Inténtalo más tarde.'],
  ])('mapea HTTP %i con mensajes controlados', async (status, code, message) => {
    const user = userEvent.setup()
    fetchMock.mockResolvedValueOnce(jsonResponse({
      error: { code, message: `${syntheticEmail} ${syntheticRuc} detalle interno`, requestId },
    }, status))
    render(<App />)

    await submitTaxpayer(user)

    const alert = await screen.findByRole('alert')
    expect(alert).toHaveTextContent(String(message))
    expect(alert).not.toHaveTextContent(syntheticEmail)
    expect(alert).not.toHaveTextContent(syntheticRuc)
    expect(alert).not.toHaveTextContent('detalle interno')
  })

  it('distingue un HTTP 404 sin código contractual de NOT_FOUND', async () => {
    const user = userEvent.setup()
    fetchMock.mockResolvedValueOnce(jsonResponse({
      error: {
        code: 'UNRECOGNIZED_ROUTE_ERROR',
        message: `Detalle técnico con ${syntheticEmail} y ${syntheticRuc}`,
        requestId,
      },
    }, 404))
    render(<App />)

    await submitTaxpayer(user)

    const alert = await screen.findByRole('alert')
    expect(alert).toHaveTextContent('No se pudo acceder al endpoint solicitado. Inténtalo más tarde.')
    expect(alert).toHaveTextContent(requestId)
    expect(alert).not.toHaveTextContent('No encontramos información para la identificación consultada.')
    expect(alert).not.toHaveTextContent(syntheticEmail)
    expect(alert).not.toHaveTextContent(syntheticRuc)
    expect(alert).not.toHaveTextContent('Detalle técnico')
  })

  it('usa el encabezado X-Request-Id como referencia segura cuando el cuerpo no tiene uno válido', async () => {
    const user = userEvent.setup()
    fetchMock.mockResolvedValueOnce(jsonResponse({
      error: { code: 'PROVIDER_UNAVAILABLE', message: 'mensaje omitido', requestId: syntheticRuc },
    }, 503, { 'X-Request-Id': requestId }))
    render(<App />)

    await submitTaxpayer(user)

    const alert = await screen.findByRole('alert')
    expect(alert).toHaveTextContent(requestId)
    expect(alert).not.toHaveTextContent(syntheticRuc)
  })

  it('cancela una solicitud que supera el timeout del cliente', async () => {
    vi.useFakeTimers()
    fetchMock.mockImplementation((_input, init) => new Promise<Response>((_resolve, reject) => {
      init?.signal?.addEventListener('abort', () => reject(new DOMException('Aborted', 'AbortError')))
    }))
    const pending = postJson('/contribuyentes/consultas', { email: syntheticEmail, ruc: syntheticRuc })
    const assertion = expect(pending).rejects.toMatchObject({ code: 'REQUEST_TIMEOUT' })

    await vi.advanceTimersByTimeAsync(15_000)
    await assertion
    vi.useRealTimers()
  })

  it('usa solo las rutas del backend y nunca solicita identificación para ANT', async () => {
    render(<App />)

    expect(screen.getByText(/consulta automática directa de puntos todavía no está integrada/i)).toBeInTheDocument()
    expect(screen.queryByLabelText(/cédula/i)).not.toBeInTheDocument()
    expect(fetchMock).not.toHaveBeenCalled()
  })
})
