import { expect, test } from '@playwright/test'

const syntheticEmail = 'e2e-user@example.invalid'
const syntheticSuccessRuc = '1234567890123'
const syntheticProviderFailureRuc = '9999999999999'
const syntheticPlate = 'E2E1234'
const syntheticProviderBodyMarker = 'SYNTHETIC_PROVIDER_BODY'
const stubBaseUrl = process.env.E2E_SRI_STUB_URL

if (!stubBaseUrl) {
  throw new Error('The isolated SRI fixture server is required for the E2E suite.')
}

interface StubRequest {
  operation: string
  method: string
  parameterNames: string[]
  status: number
}

let externalBrowserRequestObserved = false

async function clearStubRequests() {
  const response = await fetch(`${stubBaseUrl}/__test/reset`, { method: 'POST' })
  expect(response.status).toBe(204)
}

async function readStubRequests(): Promise<StubRequest[]> {
  const response = await fetch(`${stubBaseUrl}/__test/requests`)
  expect(response.ok).toBeTruthy()
  return response.json() as Promise<StubRequest[]>
}

async function enterTaxpayerDetails(page: import('@playwright/test').Page, ruc: string) {
  await page.getByLabel(/correo electrónico/i).fill(syntheticEmail)
  await page.getByLabel(/^RUC/i).fill(ruc)
}

test.beforeEach(async ({ page }) => {
  externalBrowserRequestObserved = false
  await clearStubRequests()
  await page.route('**/*', async route => {
    const requestUrl = new URL(route.request().url())
    const isLoopback = ['127.0.0.1', 'localhost', '[::1]'].includes(requestUrl.hostname)
    if (requestUrl.protocol !== 'http:' || !isLoopback) {
      externalBrowserRequestObserved = true
      await route.abort()
      return
    }
    await route.continue()
  })
})

test('consulta contribuyente y vehículo recorre React, Vite, Spring Boot y el stub SRI', async ({ page }) => {
  await page.goto('/')
  await expect(page.getByRole('heading', { name: /consulta de contribuyente/i })).toBeVisible()

  await enterTaxpayerDetails(page, syntheticSuccessRuc)
  const taxpayerResponsePromise = page.waitForResponse(response =>
    new URL(response.url()).pathname === '/api/v1/contribuyentes/consultas'
      && response.request().method() === 'POST')
  await page.getByRole('button', { name: /consultar contribuyente/i }).click()
  const taxpayerResponse = await taxpayerResponsePromise
  expect(taxpayerResponse.status()).toBe(200)
  const taxpayerPayload = await taxpayerResponse.json()
  expect(taxpayerResponse.headers()['x-request-id']).toBe(taxpayerPayload.meta.requestId)
  await expect(page.getByText('Persona natural confirmada')).toBeVisible()
  await expect(page.getByText('ACTIVO SINTÉTICO')).toBeVisible()

  await page.getByLabel(/^Placa/i).fill(syntheticPlate)
  const vehicleResponsePromise = page.waitForResponse(response =>
    new URL(response.url()).pathname === '/api/v1/vehiculos/consultas'
      && response.request().method() === 'POST')
  await page.getByRole('button', { name: /consultar vehículo/i }).click()
  const vehicleResponse = await vehicleResponsePromise
  expect(vehicleResponse.status()).toBe(200)
  const vehiclePayload = await vehicleResponse.json()
  expect(vehicleResponse.headers()['x-request-id']).toBe(vehiclePayload.meta.requestId)
  await expect(page.getByRole('region', { name: 'Resultado de vehículos' })).toBeVisible()
  await expect(page.getByRole('heading', { name: `Vehículo ${syntheticPlate}` })).toBeVisible()
  await expect(page.getByText('MARCA E2E SINTÉTICA')).toBeVisible()

  expect(await readStubRequests()).toEqual([
    { operation: 'taxpayer-existence', method: 'GET', parameterNames: ['numeroRuc'], status: 200 },
    { operation: 'taxpayer-details', method: 'GET', parameterNames: ['ruc'], status: 200 },
    { operation: 'vehicle-lookup', method: 'GET', parameterNames: ['numeroPlacaCampvCpn'], status: 200 },
  ])
  expect(await page.evaluate(() => [localStorage.length, sessionStorage.length])).toEqual([0, 0])
  expect(externalBrowserRequestObserved).toBe(false)
})

test('traduce un HTTP 503 del proveedor a un error seguro en la interfaz', async ({ page }) => {
  await page.goto('/')
  await enterTaxpayerDetails(page, syntheticProviderFailureRuc)
  const taxpayerResponsePromise = page.waitForResponse(response =>
    new URL(response.url()).pathname === '/api/v1/contribuyentes/consultas'
      && response.request().method() === 'POST')
  await page.getByRole('button', { name: /consultar contribuyente/i }).click()
  const taxpayerResponse = await taxpayerResponsePromise

  expect(taxpayerResponse.status()).toBe(502)
  const payload = await taxpayerResponse.json()
  expect(payload.error.code).toBe('PROVIDER_BAD_RESPONSE')
  expect(taxpayerResponse.headers()['x-request-id']).toBe(payload.error.requestId)
  const alert = page.getByRole('alert')
  await expect(alert).toContainText('El SRI devolvió una respuesta que no pudimos procesar.')
  const visibleError = await alert.innerText()
  expect(visibleError).not.toContain(syntheticEmail)
  expect(visibleError).not.toContain(syntheticProviderFailureRuc)
  expect(visibleError).not.toContain(syntheticProviderBodyMarker)
  expect(visibleError).not.toContain('Exception')
  expect(visibleError).not.toContain('stack trace')
  expect(await readStubRequests()).toEqual([
    { operation: 'taxpayer-existence', method: 'GET', parameterNames: ['numeroRuc'], status: 503 },
  ])
  expect(await page.evaluate(() => [localStorage.length, sessionStorage.length])).toEqual([0, 0])
  expect(externalBrowserRequestObserved).toBe(false)
})
