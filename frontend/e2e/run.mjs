import { spawn } from 'node:child_process'
import { createServer as createHttpServer } from 'node:http'
import { createServer as createNetServer } from 'node:net'
import { setTimeout as delay } from 'node:timers/promises'
import { fileURLToPath } from 'node:url'
import path from 'node:path'
import process from 'node:process'

const frontendDirectory = path.dirname(fileURLToPath(import.meta.url))
const frontendRoot = path.resolve(frontendDirectory, '..')
const repositoryRoot = path.resolve(frontendRoot, '..')
const backendRoot = path.join(repositoryRoot, 'backend')
const jarPath = path.join(backendRoot, 'target', 'consultas-sri-ant-backend-0.0.1-SNAPSHOT.jar')
const children = []

const syntheticSuccessRuc = '1234567890123'
const syntheticProviderFailureRuc = '9999999999999'
const syntheticPlate = 'E2E1234'
const syntheticEmail = 'e2e-user@example.invalid'
const syntheticProviderBodyMarker = 'SYNTHETIC_PROVIDER_BODY'
const operationByPath = new Map([
  ['/sri/taxpayer-existence', 'taxpayer-existence'],
  ['/sri/taxpayer-details', 'taxpayer-details'],
  ['/sri/vehicle', 'vehicle-lookup'],
])

function sendJson(response, status, value) {
  response.writeHead(status, { 'content-type': 'application/json; charset=utf-8' })
  response.end(JSON.stringify(value))
}

function createSriStub() {
  const requests = []
  const server = createHttpServer((request, response) => {
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
    if (operation === 'taxpayer-existence' && url.searchParams.get('numeroRuc') === syntheticProviderFailureRuc) {
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
        sendJson(response, status, { privateProviderMarker: 'SYNTHETIC_PROVIDER_BODY' })
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
      if (requestedRuc !== syntheticSuccessRuc) {
        sendJson(response, 404, { message: 'Synthetic taxpayer not found.' })
        return
      }
      sendJson(response, 200, [{
        numeroRuc: syntheticSuccessRuc,
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
        || url.searchParams.get('numeroPlacaCampvCpn') !== syntheticPlate) {
      sendJson(response, 400, { message: 'Synthetic request shape rejected.' })
      return
    }
    sendJson(response, 200, {
      codigoVehiculo: 123,
      numeroPlaca: syntheticPlate,
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
      resolve({ server, baseUrl: `http://127.0.0.1:${address.port}` })
    })
  })
}

async function availablePort() {
  const server = createNetServer()
  await new Promise((resolve, reject) => {
    server.once('error', reject)
    server.listen(0, '127.0.0.1', resolve)
  })
  const address = server.address()
  if (!address || typeof address === 'string') {
    throw new Error('Could not allocate a local service port.')
  }
  await new Promise((resolve, reject) => server.close(error => error ? reject(error) : resolve()))
  return address.port
}

function runCommand(command, args, options = {}) {
  return new Promise((resolve, reject) => {
    const child = spawn(command, args, {
      cwd: options.cwd,
      env: options.env ?? process.env,
      stdio: options.stdio ?? 'inherit',
      shell: options.shell ?? false,
      windowsHide: true,
    })
    child.once('error', reject)
    child.once('exit', (code, signal) => {
      if (code === 0) resolve()
      else reject(new Error(`${options.label ?? 'Command'} exited unsuccessfully (${code ?? signal}).`))
    })
  })
}

function startProcess(command, args, options = {}) {
  const child = spawn(command, args, {
    cwd: options.cwd,
    env: options.env ?? process.env,
    stdio: options.stdio ?? 'inherit',
    shell: options.shell ?? false,
    windowsHide: true,
  })
  child.on('error', () => {
    child.spawnError = true
  })
  children.push(child)
  return child
}

async function waitForReady(url, child, label, timeoutMs = 60_000) {
  const deadline = Date.now() + timeoutMs
  while (Date.now() < deadline) {
    if (child.spawnError) {
      throw new Error(`${label} could not be started.`)
    }
    if (child.exitCode !== null) {
      throw new Error(`${label} exited before becoming ready (exit ${child.exitCode}).`)
    }
    try {
      const response = await fetch(url, { signal: AbortSignal.timeout(1_500) })
      if (response.ok) return
    } catch {
      // A refused connection is expected until the local process finishes starting.
    }
    await delay(350)
  }
  throw new Error(`${label} did not become ready within ${timeoutMs} ms.`)
}

async function stopChildren() {
  for (const child of [...children].reverse()) {
    if (child.pid === undefined || child.exitCode !== null || child.killed) continue
    child.kill('SIGTERM')
  }
  await Promise.all(children.map(async child => {
    if (child.exitCode !== null) return
    await Promise.race([
      new Promise(resolve => child.once('exit', resolve)),
      delay(8_000).then(() => {
        if (child.exitCode === null) child.kill('SIGKILL')
      }),
    ])
  }))
}

let stub
try {
  const mavenCommand = process.platform === 'win32'
    ? (process.env.ComSpec ?? 'cmd.exe')
    : './mvnw'
  const mavenArgs = process.platform === 'win32'
    ? ['/d', '/s', '/c', '.\\mvnw.cmd -B -DskipTests package']
    : ['-B', '-DskipTests', 'package']
  await runCommand(mavenCommand, mavenArgs, {
    cwd: backendRoot,
    label: 'Maven package',
  })

  stub = await createSriStub()
  const backendPort = await availablePort()
  const frontendPort = await availablePort()
  const stubBase = stub.baseUrl
  const backendBase = `http://127.0.0.1:${backendPort}`
  const frontendBase = `http://127.0.0.1:${frontendPort}`
  const backendOutput = []

  const backend = startProcess('java', [
    '-jar', jarPath,
    '--spring.profiles.active=e2e',
    '--server.address=127.0.0.1',
    `--server.port=${backendPort}`,
    `--integrations.sri.taxpayer-existence-url=${stubBase}/sri/taxpayer-existence`,
    `--integrations.sri.taxpayer-details-url=${stubBase}/sri/taxpayer-details`,
    `--integrations.sri.vehicle-url=${stubBase}/sri/vehicle`,
  ], { cwd: backendRoot, stdio: ['ignore', 'pipe', 'pipe'] })
  backend.stdout.on('data', chunk => backendOutput.push(chunk.toString()))
  backend.stderr.on('data', chunk => backendOutput.push(chunk.toString()))
  await waitForReady(`${backendBase}/actuator/health`, backend, 'Spring Boot backend')

  const vite = startProcess(process.execPath, [
    path.join(frontendRoot, 'node_modules', 'vite', 'bin', 'vite.js'),
    '--host', '127.0.0.1', '--port', String(frontendPort), '--strictPort',
  ], {
    cwd: frontendRoot,
    env: {
      ...process.env,
      VITE_API_BASE_URL: '/api/v1',
      VITE_API_PROXY_TARGET: backendBase,
      VITE_E2E: 'true',
    },
  })
  await waitForReady(frontendBase, vite, 'Vite frontend')

  const playwrightCli = path.join(frontendRoot, 'node_modules', '@playwright', 'test', 'cli.js')
  await runCommand(process.execPath, [playwrightCli, 'test', '--config=e2e/playwright.config.ts'], {
    cwd: frontendRoot,
    env: {
      ...process.env,
      E2E_BASE_URL: frontendBase,
      E2E_SRI_STUB_URL: stubBase,
    },
    label: 'Playwright E2E suite',
  })
  const backendLog = backendOutput.join('')
  if ([syntheticSuccessRuc, syntheticProviderFailureRuc, syntheticPlate, syntheticEmail,
    syntheticProviderBodyMarker].some(value => backendLog.includes(value))) {
    throw new Error('Backend logs exposed synthetic request data during the E2E run.')
  }
  console.log('E2E passed against the local SRI stub; backend logs contained no request values and no external SRI request was configured.')
} catch (error) {
  console.error(error instanceof Error ? error.message : 'E2E orchestration failed.')
  process.exitCode = 1
} finally {
  await stopChildren()
  if (stub?.server) {
    stub.server.closeAllConnections()
    await new Promise(resolve => stub.server.close(() => resolve()))
  }
}
