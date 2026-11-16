import { expect, test, type Page, type Route } from '@playwright/test'
import { mkdir } from 'node:fs/promises'
import path from 'node:path'

const syntheticCedula = '0000000000'
const requestId = '550e8400-e29b-41d4-a716-446655440000'
const evidenceDirectory = process.env.E2E_ANT_EVIDENCE_DIRECTORY
let externalRequestObserved = false

function successEnvelope(totalPuntos = 18, source = 'SIMULATED') {
  return {
    data: { totalPuntos },
    meta: {
      requestId,
      source,
      originSource: source === 'CACHE' ? 'SIMULATED' : source,
      retrievedAt: '2026-11-15T16:00:00Z',
      fetchedAt: source === 'CACHE' ? '2026-10-01T12:30:00Z' : '2026-11-15T16:00:00Z',
      stale: source === 'CACHE',
      warning: source === 'CACHE'
        ? 'Fixture sintético de interfaz; ilustra una respuesta CACHE con posible antigüedad.'
        : 'SIMULATED local test fixture; not official ANT information.',
    },
  }
}

async function captureEvidence(page: Page, fileName: string) {
  if (!evidenceDirectory) return
  await mkdir(evidenceDirectory, { recursive: true })
  await page.screenshot({ path: path.join(evidenceDirectory, fileName), fullPage: true, animations: 'disabled' })
}

async function openDemo(page: Page) {
  await page.goto('/')
  await expect(page.getByRole('heading', { name: /consulta de contribuyente/i })).toBeVisible()
  const officialProcedureLink = page.getByRole('link', { name: /trámite oficial del certificado/i })
  await expect(officialProcedureLink).toBeVisible()
  await expect(officialProcedureLink).toHaveAttribute(
    'href',
    'https://www.gob.ec/ant/tramites/emision-certificado-informacion-conductor',
  )
  await expect(page.getByRole('button', { name: 'Demostración ANT' })).toBeVisible()
  const homeDemoButton = page.getByRole('button', { name: 'Simular consulta ANT' })
  await expect(homeDemoButton).toBeVisible()
  await homeDemoButton.click()
  await expect(page.getByRole('note', { name: 'Aviso permanente de simulación' }))
    .toContainText('MODO DEMOSTRACIÓN — DATOS SIMULADOS — NO OFICIALES')
}

async function submitFixture(page: Page) {
  await page.getByLabel('Cédula sintética').fill(syntheticCedula)
  await page.getByRole('button', { name: /consultar escenario local/i }).click()
}

async function fulfillApi(route: Route, status: number, body: unknown) {
  await route.fulfill({
    status,
    contentType: 'application/json',
    body: JSON.stringify(body),
  })
}

test.beforeEach(async ({ page }) => {
  externalRequestObserved = false
  await page.route('**/*', async route => {
    const url = new URL(route.request().url())
    const loopback = ['127.0.0.1', 'localhost', '[::1]'].includes(url.hostname)
    if (url.protocol !== 'http:' || !loopback) {
      externalRequestObserved = true
      await route.abort()
      return
    }
    await route.continue()
  })
})

test('la demo ANT recorre React, Vite, Spring Boot y el simulador local', async ({ page, context }) => {
  const requestUrls: string[] = []
  const consoleMessages: string[] = []
  page.on('request', request => requestUrls.push(request.url()))
  page.on('console', message => consoleMessages.push(message.text()))
  await openDemo(page)

  const responsePromise = page.waitForResponse(response =>
    new URL(response.url()).pathname === '/api/v1/licencias/puntos/consultas'
      && response.request().method() === 'POST')
  await submitFixture(page)
  const response = await responsePromise
  expect(response.status()).toBe(200)
  expect(response.request().postDataJSON()).toEqual({ cedula: syntheticCedula })

  const payload = await response.json()
  expect(payload.data.totalPuntos).toBe(20)
  expect(payload.meta.source).toBe('SIMULATED')
  expect(payload.meta.stale).toBe(false)
  expect(payload.meta.warning).toContain('no es información oficial de ANT')
  expect(response.headers()['x-request-id']).toBe(payload.meta.requestId)

  const result = page.getByRole('status', { name: 'Resultado simulado' })
  await expect(result).toContainText('Total de puntos simulado')
  await expect(result).toContainText('20')
  await expect(result).toContainText('SIMULATED')
  await expect(result).toContainText('no proviene de la ANT')
  await expect(page.getByLabel('Cédula sintética')).toHaveValue('')
  expect(await page.evaluate(() => [localStorage.length, sessionStorage.length])).toEqual([0, 0])
  expect(await context.cookies()).toEqual([])
  expect(requestUrls.every(url => !url.includes(syntheticCedula))).toBe(true)
  expect(consoleMessages.some(message => message.includes(syntheticCedula))).toBe(false)
  expect(externalRequestObserved).toBe(false)
  await captureEvidence(page, '09-ant-simulado.png')

  await page.getByRole('button', { name: /volver al inicio/i }).click()
  await expect(page.getByRole('heading', { name: /consulta de contribuyente/i })).toBeVisible()
  await expect(page.getByRole('button', { name: 'Demostración ANT' })).toBeVisible()
  await expect(page.getByRole('button', { name: 'Simular consulta ANT' })).toBeVisible()
  await expect(page.getByRole('note', { name: 'Aviso permanente de simulación' })).toHaveCount(0)
})

test('presenta cero puntos como resultado simulado distinto a la ausencia', async ({ page }) => {
  await page.route('**/api/v1/licencias/puntos/consultas', route => fulfillApi(route, 200, successEnvelope(0)))
  await openDemo(page)
  await submitFixture(page)
  const result = page.getByRole('status', { name: 'Resultado simulado' })
  await expect(result).toContainText('Total de puntos simulado')
  await expect(result.getByText('0', { exact: true })).toBeVisible()
  await expect(result).toContainText('Cero es un resultado válido')
  await expect(result).not.toContainText('no se recibió')
  expect(externalRequestObserved).toBe(false)
  await captureEvidence(page, '10-ant-cero-puntos.png')
})

test('presenta una respuesta CACHE con fecha original y aviso de posible desactualización', async ({ page }) => {
  await page.route('**/api/v1/licencias/puntos/consultas', route => fulfillApi(route, 200, successEnvelope(13, 'CACHE')))
  await openDemo(page)
  await submitFixture(page)
  const result = page.getByRole('status', { name: 'Resultado recuperado desde caché' })
  await expect(result).toContainText('CACHE')
  await expect(result).toContainText('origen SIMULATED')
  await expect(result).toContainText('no es oficial de ANT')
  await expect(result).toContainText('Último resultado disponible')
  await expect(result).toContainText('13')
  await expect(result).toContainText('Información posiblemente desactualizada')
  await expect(result).toContainText('Obtenido originalmente')
  await expect(result).toContainText('Fixture sintético de interfaz')
  expect(externalRequestObserved).toBe(false)
  await captureEvidence(page, '11-ant-cache-fixture-interfaz.png')
})

test('distingue la ausencia de información de cero puntos', async ({ page }) => {
  await page.route('**/api/v1/licencias/puntos/consultas', route => fulfillApi(route, 404, {
    error: { code: 'NOT_FOUND', message: 'No se encontró información de puntos de licencia.', requestId },
  }))
  await openDemo(page)
  await submitFixture(page)
  const empty = page.getByRole('status')
  await expect(empty).toContainText('Sin información en este escenario')
  await expect(empty).toContainText('no equivale a cero puntos')
  await expect(empty).not.toContainText('Total de puntos')
  expect(externalRequestObserved).toBe(false)
  await captureEvidence(page, '12-ant-sin-informacion.png')
})

test('muestra indisponibilidad, requestId y conserva un mensaje seguro', async ({ page }) => {
  await page.route('**/api/v1/licencias/puntos/consultas', route => fulfillApi(route, 503, {
    error: { code: 'PROVIDER_UNAVAILABLE', message: 'safe fixture message', requestId },
  }))
  await openDemo(page)
  await submitFixture(page)
  const alert = page.getByRole('alert')
  await expect(alert).toContainText('Servicio simulado no disponible')
  await expect(alert).toContainText('no se pudo recuperar un resultado previo desde la caché')
  await expect(alert).toContainText(requestId)
  await expect(alert).not.toContainText(syntheticCedula)
  expect(externalRequestObserved).toBe(false)
  await captureEvidence(page, '13-ant-servicio-indisponible-fixture.png')
})

test('muestra el timeout sin convertirlo en un saldo', async ({ page }) => {
  await page.route('**/api/v1/licencias/puntos/consultas', route => fulfillApi(route, 504, {
    error: { code: 'PROVIDER_TIMEOUT', message: 'safe timeout fixture', requestId },
  }))
  await openDemo(page)
  await submitFixture(page)
  const alert = page.getByRole('alert')
  await expect(alert).toContainText('Tiempo de espera agotado')
  await expect(alert).toContainText('El servicio de puntos de licencia excedió el tiempo de espera.')
  await expect(alert).toContainText(requestId)
  await expect(page.locator('.ant-demo-result__total')).toHaveCount(0)
  await expect(alert).not.toContainText('0 puntos')
  expect(externalRequestObserved).toBe(false)
  await captureEvidence(page, '14-ant-timeout-fixture.png')
})

test('presenta una respuesta inválida como error controlado', async ({ page }) => {
  await page.route('**/api/v1/licencias/puntos/consultas', route => fulfillApi(route, 502, {
    error: { code: 'PROVIDER_BAD_RESPONSE', message: 'synthetic body must not be shown', requestId },
  }))
  await openDemo(page)
  await submitFixture(page)
  const alert = page.getByRole('alert')
  await expect(alert).toContainText('Respuesta simulada no válida')
  await expect(alert).toContainText('El servicio de puntos de licencia devolvió una respuesta no utilizable.')
  await expect(alert).not.toContainText('synthetic body')
  await expect(alert).not.toContainText(syntheticCedula)
  expect(externalRequestObserved).toBe(false)
  await captureEvidence(page, '15-ant-respuesta-invalida-fixture.png')
})
