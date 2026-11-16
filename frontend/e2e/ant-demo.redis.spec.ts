import { expect, test, type Page } from '@playwright/test'
import { mkdir } from 'node:fs/promises'
import path from 'node:path'

const syntheticCedula = '0000000000'
const evidenceDirectory = process.env.E2E_ANT_REDIS_EVIDENCE_DIRECTORY
const externalHosts: string[] = []

async function capture(page: Page, fileName: string) {
  if (!evidenceDirectory) return
  await mkdir(evidenceDirectory, { recursive: true })
  await page.screenshot({ path: path.join(evidenceDirectory, fileName), fullPage: true, animations: 'disabled' })
}

test.beforeEach(async ({ page }) => {
  externalHosts.length = 0
  await page.route('**/*', async route => {
    const url = new URL(route.request().url())
    const loopback = ['127.0.0.1', 'localhost', '[::1]'].includes(url.hostname)
    if (url.protocol !== 'http:' || !loopback) {
      externalHosts.push(url.hostname)
      await route.abort()
      return
    }
    await route.continue()
  })
})

test('real Redis backs simulated success and timeout recovery through React and Spring Boot', async ({ page, context }) => {
  const requestUrls: string[] = []
  const consoleMessages: string[] = []
  page.on('request', request => requestUrls.push(request.url()))
  page.on('console', message => consoleMessages.push(message.text()))

  await page.goto('/')
  await expect(page.getByRole('heading', { name: /consulta de contribuyente/i })).toBeVisible()
  await page.getByRole('button', { name: 'Demostración ANT' }).click()
  await expect(page.getByRole('note', { name: 'Aviso permanente de simulación' }))
    .toContainText('MODO DEMOSTRACIÓN — DATOS SIMULADOS — NO OFICIALES')

  const endpoint = '/api/v1/licencias/puntos/consultas'
  const firstResponsePromise = page.waitForResponse(response =>
    new URL(response.url()).pathname === endpoint && response.request().method() === 'POST')
  await page.getByLabel('Cédula sintética').fill(syntheticCedula)
  await page.getByRole('button', { name: /consultar escenario local/i }).click()
  const firstResponse = await firstResponsePromise
  expect(firstResponse.status()).toBe(200)
  expect(firstResponse.request().postDataJSON()).toEqual({ cedula: syntheticCedula })
  const first = await firstResponse.json()
  expect(first.meta.source).toBe('SIMULATED')
  expect(first.meta.originSource).toBe('SIMULATED')
  expect(first.meta.stale).toBe(false)
  expect(first.data.totalPuntos).toBe(20)
  expect(first.meta.fetchedAt).toBeTruthy()
  expect(firstResponse.headers()['x-request-id']).toBe(first.meta.requestId)

  const simulated = page.getByRole('status', { name: 'Resultado simulado' })
  await expect(simulated).toContainText('SIMULATED')
  await expect(simulated).toContainText('no proviene de la ANT')
  await capture(page, 'ant-redis-simulated-success.png')

  const secondResponsePromise = page.waitForResponse(response =>
    new URL(response.url()).pathname === endpoint && response.request().method() === 'POST')
  await page.getByLabel('Cédula sintética').fill(syntheticCedula)
  await page.getByRole('button', { name: /consultar escenario local/i }).click()
  const secondResponse = await secondResponsePromise
  expect(secondResponse.status()).toBe(200)
  const second = await secondResponse.json()
  expect(second.meta.source).toBe('CACHE')
  expect(second.meta.originSource).toBe('SIMULATED')
  expect(second.meta.stale).toBe(true)
  expect(second.data.totalPuntos).toBe(first.data.totalPuntos)
  expect(second.meta.fetchedAt).toBe(first.meta.fetchedAt)
  expect(second.meta.warning).toContain('SIMULATED')

  const cached = page.getByRole('status', { name: 'Resultado recuperado desde caché' })
  await expect(cached).toContainText('CACHE')
  await expect(cached).toContainText('origen SIMULATED')
  await expect(cached).toContainText('Información posiblemente desactualizada')
  await expect(cached).toContainText('no es oficial de ANT')
  await expect(cached).toContainText('Obtenido originalmente')
  await capture(page, 'ant-redis-cache-recovery.png')

  await expect(page.getByLabel('Cédula sintética')).toHaveValue('')
  expect(await page.evaluate(() => [localStorage.length, sessionStorage.length])).toEqual([0, 0])
  expect(await context.cookies()).toEqual([])
  expect(requestUrls.every(url => !url.includes(syntheticCedula))).toBe(true)
  expect(consoleMessages.some(message => message.includes(syntheticCedula))).toBe(false)
  expect(externalHosts).toEqual([])
})
