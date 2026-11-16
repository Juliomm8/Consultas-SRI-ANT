import { spawn } from 'node:child_process'
import { existsSync, readFileSync } from 'node:fs'
import { copyFile, mkdtemp, rmdir, unlink } from 'node:fs/promises'
import { createServer as createNetServer } from 'node:net'
import { tmpdir } from 'node:os'
import { setTimeout as delay } from 'node:timers/promises'
import { fileURLToPath } from 'node:url'
import path from 'node:path'
import process from 'node:process'
import { createSriStub, syntheticSriFixtures } from './sri-test-stub.mjs'

const e2eDirectory = path.dirname(fileURLToPath(import.meta.url))
const frontendRoot = path.resolve(e2eDirectory, '..')
const repositoryRoot = path.resolve(frontendRoot, '..')
const backendRoot = path.join(repositoryRoot, 'backend')
const jarPath = path.join(backendRoot, 'target', 'consultas-sri-ant-backend-0.0.1-SNAPSHOT.jar')
const jarFileName = path.basename(jarPath)
const antSyntheticCedula = '0000000000'
const ownedChildren = []
const serviceChildren = []
const watchedLogValues = [
  syntheticSriFixtures.successRuc,
  syntheticSriFixtures.providerFailureRuc,
  syntheticSriFixtures.plate,
  syntheticSriFixtures.email,
  syntheticSriFixtures.providerBodyMarker,
  antSyntheticCedula,
]

let sriStub
let sessionArtifactDirectory
let sessionJarPath
let backendLogContainsSyntheticData = false
let stopSignalName
let resolveStopSignal
const stopSignal = new Promise(resolve => { resolveStopSignal = resolve })

function requestStop(signal) {
  if (stopSignalName) return
  stopSignalName = signal
  resolveStopSignal(signal)
}

process.on('SIGINT', () => requestStop('Ctrl+C'))
process.on('SIGTERM', () => requestStop('termination signal'))

function withStopSignal(promise) {
  const interrupted = stopSignal.then(signal => {
    throw new Error(`Session startup interrupted by ${signal}.`)
  })
  return Promise.race([promise, interrupted])
}

async function availableDistinctLoopbackPorts(count) {
  const reservations = []
  try {
    for (let index = 0; index < count; index += 1) {
      const server = createNetServer()
      await new Promise((resolve, reject) => {
        server.once('error', reject)
        server.listen(0, '127.0.0.1', resolve)
      })
      reservations.push(server)
    }

    return reservations.map(server => {
      const address = server.address()
      if (!address || typeof address === 'string') {
        throw new Error('Could not reserve a loopback port for the usability session.')
      }
      return address.port
    })
  } finally {
    await Promise.all(reservations.map(server => new Promise((resolve, reject) => {
      server.close(error => error ? reject(error) : resolve())
    })))
  }
}

function startOwnedProcess(label, command, args, options = {}) {
  const child = spawn(command, args, {
    cwd: options.cwd,
    env: options.env ?? process.env,
    stdio: options.stdio ?? ['ignore', 'pipe', 'pipe'],
    shell: false,
    windowsHide: true,
  })
  child.sessionLabel = label
  child.spawnError = null
  child.on('error', error => { child.spawnError = error })
  ownedChildren.push(child)
  return child
}

function isBootableSpringBootJar(filePath) {
  try {
    const archive = readFileSync(filePath)
    return [
      'BOOT-INF/classes/',
      'BOOT-INF/lib/',
      'org/springframework/boot/loader/launch/JarLauncher.class',
    ].every(entry => archive.includes(Buffer.from(entry)))
  } catch {
    return false
  }
}

function monitorBackendLogs(stream) {
  let tail = ''
  const tailLength = Math.max(...watchedLogValues.map(value => value.length)) - 1
  stream.on('data', chunk => {
    const text = tail + chunk.toString()
    if (watchedLogValues.some(value => text.includes(value))) {
      backendLogContainsSyntheticData = true
    }
    tail = text.slice(-tailLength)
  })
}

async function waitForReady(url, child, timeoutMs = 90_000) {
  const deadline = Date.now() + timeoutMs
  while (Date.now() < deadline) {
    if (stopSignalName) throw new Error('Session startup was cancelled.')
    if (child.spawnError) throw new Error(`${child.sessionLabel} could not be started.`)
    if (child.exitCode !== null) throw new Error(`${child.sessionLabel} exited before becoming ready.`)
    try {
      const response = await fetch(url, { signal: AbortSignal.timeout(1_500) })
      if (response.ok) return
    } catch {
      // The local service refuses connections until startup completes.
    }
    await delay(350)
  }
  throw new Error(`${child.sessionLabel} did not become ready within ${timeoutMs} ms.`)
}

async function stopOwnedProcesses() {
  for (const child of [...ownedChildren].reverse()) {
    if (child.pid === undefined || child.exitCode !== null || child.killed) continue
    child.kill('SIGTERM')
  }

  await Promise.all(ownedChildren.map(async child => {
    if (child.exitCode !== null || child.pid === undefined) return
    await Promise.race([
      new Promise(resolve => child.once('exit', resolve)),
      delay(8_000).then(() => {
        if (child.exitCode === null) child.kill('SIGKILL')
      }),
    ])
  }))
}

function waitForManualStop(services) {
  return Promise.race([
    stopSignal,
    ...services.map(({ child, label }) => new Promise((resolve, reject) => {
      child.once('exit', (code, signal) => {
        reject(new Error(`${label} stopped unexpectedly (${code ?? signal}).`))
      })
    })),
  ])
}

function printSriStubSummary() {
  const requests = sriStub?.getRequests() ?? []
  if (requests.length === 0) {
    console.log('SRI stub: no consultation requests were made during this session.')
    return
  }

  const counts = new Map()
  for (const request of requests) {
    const key = `${request.operation} HTTP ${request.status}`
    counts.set(key, (counts.get(key) ?? 0) + 1)
  }
  console.log('SRI stub requests (metadata only; query values are not printed):')
  for (const [operation, count] of counts) console.log(`  ${operation}: ${count}`)
}

async function closeSriStub() {
  if (!sriStub?.server) return
  sriStub.server.closeAllConnections()
  await new Promise(resolve => sriStub.server.close(() => resolve()))
}

async function removeSessionArtifact() {
  if (!sessionArtifactDirectory) return
  if (sessionJarPath) await unlink(sessionJarPath).catch(() => undefined)
  await rmdir(sessionArtifactDirectory).catch(() => undefined)
}

let exitCode = 0
try {
  if (!existsSync(jarPath)) {
    throw new Error('Backend JAR is missing. Build it first with the command documented in the usability guide.')
  }
  if (!isBootableSpringBootJar(jarPath)) {
    throw new Error('Backend JAR is not a repackaged Spring Boot archive. Close any session using it, then run .\\mvnw.cmd -B package from backend/.')
  }
  if (!existsSync(path.join(frontendRoot, 'node_modules', 'vite', 'bin', 'vite.js'))) {
    throw new Error('Frontend dependencies are missing. Run npm install in frontend/ first.')
  }

  // Maven E2E runners rebuild backend/target; the live session uses a private copy to avoid artifact races.
  sessionArtifactDirectory = await mkdtemp(path.join(tmpdir(), 'consultas-sri-ant-usability-'))
  sessionJarPath = path.join(sessionArtifactDirectory, jarFileName)
  await copyFile(jarPath, sessionJarPath)
  if (!isBootableSpringBootJar(sessionJarPath)) {
    throw new Error('The backend archive changed while preparing this session. Rebuild it after other Maven tasks finish, then restart.')
  }

  sriStub = await withStopSignal(createSriStub())
  const [backendPort, vitePort] = await availableDistinctLoopbackPorts(2)
  const sriBaseUrl = sriStub.baseUrl
  const backendBaseUrl = `http://127.0.0.1:${backendPort}`
  const frontendUrl = `http://127.0.0.1:${vitePort}`
  const sriUrl = new URL(sriBaseUrl)

  if (sriUrl.protocol !== 'http:' || sriUrl.hostname !== '127.0.0.1' || sriUrl.port === '') {
    throw new Error('The SRI fixture server did not bind to an approved loopback address.')
  }

  const backendEnvironment = {
    ...process.env,
    REDIS_CACHE_ENABLED: 'false',
    REDIS_HOST: '127.0.0.1',
    REDIS_USERNAME: '',
    REDIS_PASSWORD: '',
    REDIS_SSL_ENABLED: 'false',
    REDIS_CACHE_HMAC_SECRET: '',
    ANT_SIMULATION_SCENARIO: 'SUCCESS',
  }
  const backend = startOwnedProcess('Spring Boot backend', 'java', [
    '-jar', sessionJarPath,
    '--spring.profiles.active=e2e,local-simulation',
    '--server.address=127.0.0.1',
    `--server.port=${backendPort}`,
    '--app.ant.simulation.enabled=true',
    '--app.ant.simulation.scenario=SUCCESS',
    '--app.ant.simulation.total-points=20',
    '--app.cache.redis.enabled=false',
    `--integrations.sri.taxpayer-existence-url=${sriBaseUrl}/sri/taxpayer-existence`,
    `--integrations.sri.taxpayer-details-url=${sriBaseUrl}/sri/taxpayer-details`,
    `--integrations.sri.vehicle-url=${sriBaseUrl}/sri/vehicle`,
  ], { cwd: backendRoot, env: backendEnvironment })
  serviceChildren.push({ child: backend, label: 'Spring Boot backend' })
  monitorBackendLogs(backend.stdout)
  monitorBackendLogs(backend.stderr)
  await withStopSignal(waitForReady(`${backendBaseUrl}/actuator/health`, backend))

  const vite = startOwnedProcess('Vite frontend', process.execPath, [
    path.join(frontendRoot, 'node_modules', 'vite', 'bin', 'vite.js'),
    '--host', '127.0.0.1', '--port', String(vitePort), '--strictPort',
  ], {
    cwd: frontendRoot,
    env: {
      ...process.env,
      VITE_API_BASE_URL: '/api/v1',
      VITE_API_PROXY_TARGET: backendBaseUrl,
      VITE_ANT_DEMO: 'true',
      VITE_E2E: 'true',
    },
  })
  vite.stdout.resume()
  vite.stderr.resume()
  serviceChildren.push({ child: vite, label: 'Vite frontend' })
  await withStopSignal(waitForReady(frontendUrl, vite))

  console.log('Usability session ready (manual browser session; no Playwright tests are running).')
  console.log(`Open Home: ${frontendUrl}`)
  console.log(`Backend health: ${backendBaseUrl}/actuator/health`)
  console.log(`SRI fixture server: ${sriBaseUrl} (127.0.0.1 only; protected by the e2e profile guard)`)
  console.log('Spring profiles: e2e, local-simulation; ANT scenario: SUCCESS; Redis: disabled.')
  console.log('Synthetic fixtures for this session:')
  console.log(`  Email: ${syntheticSriFixtures.email}`)
  console.log(`  RUC: ${syntheticSriFixtures.successRuc}`)
  console.log(`  Plate: ${syntheticSriFixtures.plate}`)
  console.log(`  ANT demo ID: ${antSyntheticCedula}`)
  console.log('The external official ANT link is disabled in this isolated VITE_E2E session.')
  console.log('The portal app itself sends consultations only through the local Vite proxy and SRI stub.')
  console.log('Keep this process running during the session. Press Ctrl+C here to stop only this runner’s stub, backend, and Vite.')

  const signal = await waitForManualStop(serviceChildren)
  console.log(`Stopping the usability session after ${signal}.`)
} catch (error) {
  if (stopSignalName) {
    console.log('Usability session startup was cancelled.')
  } else {
    console.error(error instanceof Error ? error.message : 'Could not start the local usability session.')
    exitCode = 1
  }
} finally {
  await stopOwnedProcesses()
  await closeSriStub()
  await removeSessionArtifact()
  printSriStubSummary()
  if (backendLogContainsSyntheticData) {
    console.error('Privacy check failed: backend logs contained a synthetic session marker; log contents were not printed or saved.')
    exitCode = 1
  } else {
    console.log('Backend log privacy check: no session fixture values detected.')
  }
}

process.exitCode = exitCode
