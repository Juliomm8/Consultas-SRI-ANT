import { expect, test } from '@playwright/test'
import { mkdir } from 'node:fs/promises'
import path from 'node:path'

const syntheticEmail = 'e2e-user@example.invalid'
const syntheticSuccessRuc = '1234567890123'
const syntheticProviderFailureRuc = '9999999999999'
const syntheticPlate = 'E2E1234'
const syntheticProviderBodyMarker = 'SYNTHETIC_PROVIDER_BODY'
const stubBaseUrl = process.env.E2E_SRI_STUB_URL
const uiEvidenceDirectory = path.resolve(process.cwd(), '../docs/diseno/evidencias-ui')

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

async function captureUiEvidence(
  page: import('@playwright/test').Page,
  fileName: string,
  fullPage = true,
) {
  await mkdir(uiEvidenceDirectory, { recursive: true })
  const imagesLoaded = await page.evaluate(async () => {
    const images = [...document.images]
    await Promise.all(images.map(async image => {
      image.loading = 'eager'
      await image.decode().catch(() => undefined)
    }))
    return images.every(image => image.naturalWidth > 0)
  })
  expect(imagesLoaded, `all local photographs load for ${fileName}`).toBe(true)
  await page.screenshot({ path: path.join(uiEvidenceDirectory, fileName), fullPage })
}

async function expectNoHorizontalOverflow(page: import('@playwright/test').Page, width: number) {
  const dimensions = await page.evaluate(() => ({
    documentWidth: document.documentElement.scrollWidth,
    viewportWidth: window.innerWidth,
  }))
  expect(dimensions.documentWidth, `horizontal overflow at ${width}px`).toBeLessThanOrEqual(dimensions.viewportWidth)
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
  await page.setViewportSize({ width: 1440, height: 1000 })
  await expect(page.getByRole('heading', { name: /consulta de contribuyente/i })).toBeVisible()
  await captureUiEvidence(page, 'home-1440.png')

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
  await captureUiEvidence(page, 'taxpayer-success.png')
  await page.setViewportSize({ width: 390, height: 844 })
  await expectNoHorizontalOverflow(page, 390)
  await page.getByText('Persona natural confirmada').scrollIntoViewIfNeeded()
  await captureUiEvidence(page, 'taxpayer-success-390.png', false)
  await page.setViewportSize({ width: 1440, height: 1000 })

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
  await captureUiEvidence(page, 'vehicle-success.png')
  await page.setViewportSize({ width: 390, height: 844 })
  await expectNoHorizontalOverflow(page, 390)
  await page.getByRole('heading', { name: `Vehículo ${syntheticPlate}` }).scrollIntoViewIfNeeded()
  await captureUiEvidence(page, 'vehicle-success-390.png', false)

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
  await captureUiEvidence(page, 'provider-error.png')
  await page.setViewportSize({ width: 390, height: 844 })
  await expectNoHorizontalOverflow(page, 390)
  await page.getByRole('alert').scrollIntoViewIfNeeded()
  await captureUiEvidence(page, 'provider-error-390.png', false)
  expect(await readStubRequests()).toEqual([
    { operation: 'taxpayer-existence', method: 'GET', parameterNames: ['numeroRuc'], status: 503 },
  ])
  expect(await page.evaluate(() => [localStorage.length, sessionStorage.length])).toEqual([0, 0])
  expect(externalBrowserRequestObserved).toBe(false)
})

test('mantiene la portada usable y sin desbordamiento en los anchos objetivo', async ({ page }) => {
  const viewports = [
    { width: 1440, height: 1000 },
    { width: 1280, height: 900 },
    { width: 768, height: 1024 },
    { width: 390, height: 844 },
    { width: 320, height: 740 },
  ]

  await page.goto('/')
  await expect(page.getByRole('heading', { name: /tu información, más clara y a tu alcance/i })).toBeVisible()
  await expect(page.getByRole('img', { name: /carretera andina/i })).toBeVisible()

  for (const viewport of viewports) {
    await page.setViewportSize(viewport)
    await page.evaluate(() => document.fonts.ready)
    await expect(page.getByRole('link', { name: /comenzar consulta/i })).toBeVisible()
    const dimensions = await page.evaluate(() => ({
      documentWidth: document.documentElement.scrollWidth,
      viewportWidth: window.innerWidth,
      heroWidth: document.querySelector('.hero')?.getBoundingClientRect().width ?? 0,
    }))
    expect(dimensions.documentWidth, `horizontal overflow at ${viewport.width}px`).toBeLessThanOrEqual(dimensions.viewportWidth)
    expect(dimensions.heroWidth).toBeGreaterThan(0)
    await captureUiEvidence(page, `home-${viewport.width}.png`)
  }

  await page.setViewportSize({ width: 390, height: 844 })
  await page.reload()
  const menu = page.locator('.menu-toggle')
  await page.keyboard.press('Tab')
  await expect(page.getByRole('link', { name: 'Consultas SRI ANT, inicio' })).toBeFocused()
  await page.keyboard.press('Tab')
  await expect(menu).toBeFocused()
  const menuFocusStyle = await menu.evaluate((element) => {
    const style = getComputedStyle(element)
    return { outlineStyle: style.outlineStyle, boxShadow: style.boxShadow }
  })
  expect(menuFocusStyle.outlineStyle).toBe('solid')
  expect(menuFocusStyle.boxShadow).not.toBe('none')
  await page.keyboard.press('Enter')
  await expect(menu).toHaveAttribute('aria-expanded', 'true')
  const navigation = page.getByRole('navigation', { name: 'Navegación principal' })
  await expect(navigation).toBeVisible()
  await page.keyboard.press('Tab')
  await expect(navigation.getByRole('link', { name: 'Servicios' })).toBeFocused()
  await page.keyboard.press('Escape')
  await expect(menu).toHaveAttribute('aria-expanded', 'false')
  await expect(menu).toBeFocused()
  await menu.click()
  await expect(menu).toHaveAttribute('aria-expanded', 'true')
  await navigation.getByRole('link', { name: 'Estado ANT' }).click()
  await expect(page).toHaveURL(/#estado-ant$/)
  await expect(page.getByRole('heading', { name: /puntos de licencia ant/i })).toBeVisible()
  expect(externalBrowserRequestObserved).toBe(false)
})
