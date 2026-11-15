import { expect, test } from '@playwright/test'

const syntheticCedula = '0000000000'
const evidencePath = process.env.E2E_ANT_EVIDENCE_PATH
let externalRequestObserved = false

test('la demo ANT recorre React, Vite, Spring Boot y el simulador local', async ({ page, context }) => {
  externalRequestObserved = false
  const requestUrls: string[] = []
  const consoleMessages: string[] = []
  page.on('request', request => requestUrls.push(request.url()))
  page.on('console', message => consoleMessages.push(message.text()))
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

  await page.goto('/')
  await expect(page.getByRole('heading', { name: /consulta de contribuyente/i })).toBeVisible()
  const officialProcedureLink = page.getByRole('link', { name: /consultar el trámite oficial/i })
  await expect(officialProcedureLink).toBeVisible()
  await expect(officialProcedureLink).toHaveAttribute(
    'href',
    'https://www.gob.ec/ant/tramites/emision-certificado-informacion-conductor',
  )
  await page.getByRole('button', { name: 'Demo local' }).click()
  await expect(page.getByRole('note', { name: 'Aviso permanente de simulación' }))
    .toContainText('MODO DEMOSTRACIÓN — DATOS SIMULADOS — NO OFICIALES')

  await page.getByLabel('Identificación sintética').fill(syntheticCedula)
  const responsePromise = page.waitForResponse(response =>
    new URL(response.url()).pathname === '/api/v1/licencias/puntos/consultas'
      && response.request().method() === 'POST')
  await page.getByRole('button', { name: /consultar escenario local/i }).click()
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
  await expect(page.getByLabel('Identificación sintética')).toHaveValue('')
  expect(await page.evaluate(() => [localStorage.length, sessionStorage.length])).toEqual([0, 0])
  expect(await context.cookies()).toEqual([])
  expect(requestUrls.every(url => !url.includes(syntheticCedula))).toBe(true)
  expect(consoleMessages.some(message => message.includes(syntheticCedula))).toBe(false)
  expect(externalRequestObserved).toBe(false)

  if (evidencePath) {
    await page.screenshot({ path: evidencePath, fullPage: true })
  }
})
