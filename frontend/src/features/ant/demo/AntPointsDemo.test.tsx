import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { AntPointsDemo } from './AntPointsDemo'

const requestId = '550e8400-e29b-41d4-a716-446655440000'
const syntheticCedula = '0000000000'
const fetchMock = vi.fn<typeof fetch>()

function jsonResponse(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  })
}

function successEnvelope(totalPuntos = 18, source = 'SIMULATED') {
  return {
    data: { totalPuntos },
    meta: {
      requestId,
      source,
      retrievedAt: '2026-11-15T16:00:00Z',
      fetchedAt: '2026-11-15T16:00:00Z',
      stale: false,
      warning: 'SIMULATED local test fixture; not official ANT information.',
    },
  }
}

async function submitSyntheticCedula(user: ReturnType<typeof userEvent.setup>) {
  const input = screen.getByLabelText('Identificación sintética')
  await user.clear(input)
  await user.type(input, syntheticCedula)
  expect(input).toHaveValue(syntheticCedula)
  const button = screen.getByRole('button', { name: /consultar escenario local/i })
  await waitFor(() => expect(button).toBeEnabled())
  await user.click(button)
}

beforeEach(() => {
  fetchMock.mockReset()
  vi.stubGlobal('fetch', fetchMock)
})

afterEach(() => {
  vi.unstubAllGlobals()
  vi.restoreAllMocks()
})

describe('demostración local de puntos ANT', () => {
  it('mantiene visible el aviso de simulación y no consulta al cargar', () => {
    render(<AntPointsDemo onBack={() => undefined} />)

    expect(screen.getByRole('note', { name: 'Aviso permanente de simulación' }))
      .toHaveTextContent('MODO DEMOSTRACIÓN — DATOS SIMULADOS — NO OFICIALES')
    expect(fetchMock).not.toHaveBeenCalled()
  })

  it('muestra el resultado simulado, la fecha y no persiste la identificación', async () => {
    const user = userEvent.setup()
    const localSetItem = vi.spyOn(localStorage, 'setItem')
    const sessionSetItem = vi.spyOn(sessionStorage, 'setItem')
    const consoleMethods = [
      vi.spyOn(console, 'log'),
      vi.spyOn(console, 'info'),
      vi.spyOn(console, 'warn'),
      vi.spyOn(console, 'error'),
    ]
    consoleMethods.forEach(method => method.mockImplementation(() => undefined))
    fetchMock.mockResolvedValueOnce(jsonResponse(successEnvelope()))
    render(<AntPointsDemo onBack={() => undefined} />)

    await submitSyntheticCedula(user)

    const result = await screen.findByRole('status', { name: 'Resultado simulado' })
    expect(result).toHaveTextContent('18')
    expect(result).toHaveTextContent('SIMULATED')
    expect(result).toHaveTextContent('no proviene de la ANT')
    expect(result).toHaveTextContent(requestId)
    expect(result).not.toHaveTextContent(syntheticCedula)
    expect(screen.getByLabelText('Identificación sintética')).toHaveValue('')
    const [url, options] = fetchMock.mock.calls[0] ?? []
    expect(url).toBe('/api/v1/licencias/puntos/consultas')
    expect(options?.method).toBe('POST')
    expect(options?.credentials).toBe('omit')
    expect(options?.cache).toBe('no-store')
    expect(JSON.parse(String(options?.body))).toEqual({ cedula: syntheticCedula })
    expect(localSetItem).not.toHaveBeenCalled()
    expect(sessionSetItem).not.toHaveBeenCalled()
    consoleMethods.forEach(method => expect(method).not.toHaveBeenCalled())
  })

  it('muestra el estado de carga mientras espera al backend local', async () => {
    const user = userEvent.setup()
    let resolveResponse: ((response: Response) => void) | undefined
    fetchMock.mockImplementationOnce(() => new Promise<Response>(resolve => { resolveResponse = resolve }))
    render(<AntPointsDemo onBack={() => undefined} />)

    await submitSyntheticCedula(user)

    expect(screen.getByRole('button', { name: /consultando/i })).toBeDisabled()
    expect(screen.getByRole('status')).toHaveTextContent('Esperando al backend local')
    resolveResponse?.(jsonResponse(successEnvelope()))
    expect(await screen.findByRole('status', { name: 'Resultado simulado' })).toBeInTheDocument()
  })

  it('distingue un saldo cero válido de una respuesta sin información', async () => {
    const user = userEvent.setup()
    fetchMock.mockResolvedValueOnce(jsonResponse(successEnvelope(0)))
    render(<AntPointsDemo onBack={() => undefined} />)
    await submitSyntheticCedula(user)

    const result = await screen.findByRole('status', { name: 'Resultado simulado' })
    expect(result).toHaveTextContent('Total de puntos simulado')
    expect(within(result).getByText('0', { exact: true })).toBeInTheDocument()
    expect(result).toHaveTextContent('Cero es un resultado válido')
  })

  it('muestra ausencia de información sin inventar un saldo', async () => {
    const user = userEvent.setup()
    fetchMock.mockResolvedValueOnce(jsonResponse({
      error: { code: 'NOT_FOUND', message: 'No se encontró información de puntos de licencia.', requestId },
    }, 404))
    render(<AntPointsDemo onBack={() => undefined} />)
    await submitSyntheticCedula(user)

    const status = await screen.findByRole('status')
    expect(status).toHaveTextContent('Sin información en este escenario')
    expect(status).toHaveTextContent('no equivale a cero puntos')
    expect(status).toHaveTextContent(requestId)
    expect(status).not.toHaveTextContent('Total de puntos')
  })

  it('muestra indisponibilidad controlada y requestId sin contenido del proveedor', async () => {
    const user = userEvent.setup()
    fetchMock.mockResolvedValueOnce(jsonResponse({
      error: { code: 'PROVIDER_UNAVAILABLE', message: `detalle privado ${syntheticCedula}`, requestId },
    }, 503))
    render(<AntPointsDemo onBack={() => undefined} />)
    await submitSyntheticCedula(user)

    const alert = await screen.findByRole('alert')
    expect(alert).toHaveTextContent('Servicio simulado no disponible')
    expect(alert).toHaveTextContent('El servicio de puntos de licencia no está disponible.')
    expect(alert).toHaveTextContent(requestId)
    expect(alert).not.toHaveTextContent(syntheticCedula)
    expect(alert).not.toHaveTextContent('detalle privado')
  })

  it('distingue timeout del proveedor y timeout del cliente', async () => {
    const user = userEvent.setup()
    fetchMock.mockResolvedValueOnce(jsonResponse({
      error: { code: 'PROVIDER_TIMEOUT', message: 'safe backend message', requestId },
    }, 504))
    render(<AntPointsDemo onBack={() => undefined} />)
    await submitSyntheticCedula(user)

    let alert = await screen.findByRole('alert')
    expect(alert).toHaveTextContent('Tiempo de espera agotado')
    expect(alert).toHaveTextContent('El servicio de puntos de licencia excedió el tiempo de espera.')
    expect(alert).toHaveTextContent(requestId)

    fetchMock.mockRejectedValueOnce(new TypeError('synthetic network failure'))
    await submitSyntheticCedula(user)
    alert = await screen.findByRole('alert')
    expect(alert).toHaveTextContent('No se pudo conectar con el backend local de demostración.')
  })

  it('no expone cuerpos externos ni acepta una fuente que no sea SIMULATED', async () => {
    const user = userEvent.setup()
    fetchMock.mockResolvedValueOnce(jsonResponse({
      error: { code: 'PROVIDER_BAD_RESPONSE', message: `external body ${syntheticCedula}`, requestId },
    }, 502))
    render(<AntPointsDemo onBack={() => undefined} />)
    await submitSyntheticCedula(user)

    let alert = await screen.findByRole('alert')
    expect(alert).toHaveTextContent('Respuesta simulada no válida')
    expect(alert).not.toHaveTextContent(syntheticCedula)
    expect(alert).not.toHaveTextContent('external body')

    fetchMock.mockResolvedValueOnce(jsonResponse(successEnvelope(14, 'CACHE')))
    await submitSyntheticCedula(user)
    alert = await screen.findByRole('alert')
    expect(alert).toHaveTextContent('La respuesta no coincide con el contrato local de simulación.')
    expect(screen.queryByRole('status', { name: 'Resultado simulado' })).not.toBeInTheDocument()
  })

  it('rechaza formato inválido sin enviar ni conservar el dato', async () => {
    const user = userEvent.setup()
    render(<AntPointsDemo onBack={() => undefined} />)
    await user.type(screen.getByLabelText('Identificación sintética'), '123')
    await user.click(screen.getByRole('button', { name: /consultar escenario local/i }))

    expect(await screen.findByRole('alert')).toHaveTextContent('fixture sintético 0000000000')
    expect(fetchMock).not.toHaveBeenCalled()
    expect(screen.getByLabelText('Identificación sintética')).toHaveValue('')
    expect(screen.queryByText('123')).not.toBeInTheDocument()
  })
})
