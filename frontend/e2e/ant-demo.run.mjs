import { spawn } from 'node:child_process'
import { createServer as createHttpServer } from 'node:http'
import { createServer as createNetServer } from 'node:net'
import { setTimeout as delay } from 'node:timers/promises'
import { existsSync } from 'node:fs'
import { mkdir } from 'node:fs/promises'
import { fileURLToPath } from 'node:url'
import path from 'node:path'
import process from 'node:process'

const frontendDirectory = path.dirname(fileURLToPath(import.meta.url))
const frontendRoot = path.resolve(frontendDirectory, '..')
const repositoryRoot = path.resolve(frontendRoot, '..')
const backendRoot = path.join(repositoryRoot, 'backend')
const jarPath = path.join(backendRoot, 'target', 'consultas-sri-ant-backend-0.0.1-SNAPSHOT.jar')
const evidenceDirectory = path.join(repositoryRoot, 'docs', 'pruebas', 'evidencias', 'ant-demo')
const evidencePath = path.join(evidenceDirectory, 'demo-ant-local-success.png')
const children = []
const syntheticCedula = '0000000000'

function runCommand(command, args, options = {}) {
  return new Promise((resolve, reject) => {
    const child = spawn(command, args, {
      cwd: options.cwd,
      env: options.env ?? process.env,
      stdio: options.stdio ?? 'inherit',
      shell: false,
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
    stdio: ['ignore', 'pipe', 'pipe'],
    shell: false,
    windowsHide: true,
  })
  child.on('error', () => { child.spawnError = true })
  children.push(child)
  return child
}

async function availablePort() {
  const server = createNetServer()
  await new Promise((resolve, reject) => {
    server.once('error', reject)
    server.listen(0, '127.0.0.1', resolve)
  })
  const address = server.address()
  if (!address || typeof address === 'string') throw new Error('Could not allocate a loopback port.')
  await new Promise((resolve, reject) => server.close(error => error ? reject(error) : resolve()))
  return address.port
}

async function createSriGuardStub() {
  let requestCount = 0
  const server = createHttpServer((_request, response) => {
    requestCount += 1
    response.writeHead(503, { 'content-type': 'application/json' })
    response.end('{"message":"Synthetic SRI guard stub; not configured for ANT demo."}')
  })
  await new Promise((resolve, reject) => {
    server.once('error', reject)
    server.listen(0, '127.0.0.1', resolve)
  })
  const address = server.address()
  if (!address || typeof address === 'string') throw new Error('Could not start the local SRI guard stub.')
  return { server, baseUrl: `http://127.0.0.1:${address.port}`, getRequestCount: () => requestCount }
}

async function waitForReady(url, child, label, timeoutMs = 60_000) {
  const deadline = Date.now() + timeoutMs
  while (Date.now() < deadline) {
    if (child.spawnError) throw new Error(`${label} could not be started.`)
    if (child.exitCode !== null) throw new Error(`${label} exited before becoming ready.`)
    try {
      const response = await fetch(url, { signal: AbortSignal.timeout(1_500) })
      if (response.ok) return
    } catch {
      // The local process is expected to refuse connections until it starts.
    }
    await delay(350)
  }
  throw new Error(`${label} did not become ready in time.`)
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
      delay(8_000).then(() => { if (child.exitCode === null) child.kill('SIGKILL') }),
    ])
  }))
}

let stub
const backendOutput = []
try {
  const mavenCommand = process.platform === 'win32' ? (process.env.ComSpec ?? 'cmd.exe') : './mvnw'
  const mavenArgs = process.platform === 'win32'
    ? ['/d', '/s', '/c', '.\\mvnw.cmd -B -DskipTests package']
    : ['-B', '-DskipTests', 'package']
  await runCommand(mavenCommand, mavenArgs, { cwd: backendRoot, label: 'Maven package' })
  if (!existsSync(jarPath)) throw new Error('The backend JAR required for the isolated ANT demo was not generated.')

  stub = await createSriGuardStub()
  const backendPort = await availablePort()
  const frontendPort = await availablePort()
  const backendBase = `http://127.0.0.1:${backendPort}`
  const frontendBase = `http://127.0.0.1:${frontendPort}`
  const stubBase = stub.baseUrl

  const backend = startProcess('java', [
    '-jar', jarPath,
    '--spring.profiles.active=e2e,local-simulation',
    '--server.address=127.0.0.1',
    `--server.port=${backendPort}`,
    '--app.ant.simulation.enabled=true',
    '--app.ant.simulation.scenario=SUCCESS',
    '--app.ant.simulation.total-points=20',
    '--app.cache.redis.enabled=false',
    `--integrations.sri.taxpayer-existence-url=${stubBase}/sri/taxpayer-existence`,
    `--integrations.sri.taxpayer-details-url=${stubBase}/sri/taxpayer-details`,
    `--integrations.sri.vehicle-url=${stubBase}/sri/vehicle`,
  ], { cwd: backendRoot, env: { ...process.env, REDIS_CACHE_ENABLED: 'false' } })
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
      VITE_ANT_DEMO: 'true',
      VITE_E2E: 'true',
    },
  })
  await waitForReady(frontendBase, vite, 'Vite ANT demo')

  await mkdir(evidenceDirectory, { recursive: true })
  const playwrightCli = path.join(frontendRoot, 'node_modules', '@playwright', 'test', 'cli.js')
  await runCommand(process.execPath, [playwrightCli, 'test', '--config=e2e/ant-demo.playwright.config.ts'], {
    cwd: frontendRoot,
    env: {
      ...process.env,
      E2E_BASE_URL: frontendBase,
      E2E_ANT_EVIDENCE_PATH: evidencePath,
    },
    label: 'Isolated ANT demo Playwright suite',
  })

  if (stub.getRequestCount() !== 0) throw new Error('The ANT demo unexpectedly invoked an SRI provider adapter.')
  if (backendOutput.join('').includes(syntheticCedula)) {
    throw new Error('Backend logs exposed the synthetic identification during the ANT demo.')
  }
  console.log('Local ANT demo E2E passed; simulator ran on loopback, SRI stub received no request, and no external service was configured.')
  console.log('A Playwright screenshot with synthetic data was saved under docs/pruebas/evidencias/ant-demo/.')
} catch (error) {
  console.error(error instanceof Error ? error.message : 'Local ANT demo E2E orchestration failed.')
  process.exitCode = 1
} finally {
  await stopChildren()
  if (stub?.server) {
    stub.server.closeAllConnections()
    await new Promise(resolve => stub.server.close(() => resolve()))
  }
}
