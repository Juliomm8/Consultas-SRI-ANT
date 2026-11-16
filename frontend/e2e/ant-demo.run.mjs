import { spawn } from 'node:child_process'
import { createServer as createHttpServer } from 'node:http'
import { createServer as createNetServer } from 'node:net'
import { setTimeout as delay } from 'node:timers/promises'
import { existsSync } from 'node:fs'
import { mkdir } from 'node:fs/promises'
import { fileURLToPath } from 'node:url'
import path from 'node:path'
import process from 'node:process'
import { randomBytes } from 'node:crypto'

const frontendDirectory = path.dirname(fileURLToPath(import.meta.url))
const frontendRoot = path.resolve(frontendDirectory, '..')
const repositoryRoot = path.resolve(frontendRoot, '..')
const backendRoot = path.join(repositoryRoot, 'backend')
const jarPath = path.join(backendRoot, 'target', 'consultas-sri-ant-backend-0.0.1-SNAPSHOT.jar')
const redisMode = process.argv.includes('--redis')
const defaultEvidenceDirectory = redisMode
  ? path.join(repositoryRoot, 'docs', 'pruebas', 'evidencias-ant-redis')
  : path.join(repositoryRoot, 'docs', 'pruebas', 'evidencias-ant-local')
const evidenceRoot = path.join(repositoryRoot, 'docs', 'pruebas', 'evidencias-ant-redis')
const evidenceDirectory = process.env.E2E_ANT_EVIDENCE_DIR
  ? path.resolve(repositoryRoot, process.env.E2E_ANT_EVIDENCE_DIR)
  : defaultEvidenceDirectory
if (process.env.E2E_ANT_EVIDENCE_DIR) {
  const relativeEvidencePath = path.relative(evidenceRoot, evidenceDirectory)
  if (relativeEvidencePath.startsWith('..') || path.isAbsolute(relativeEvidencePath)) {
    throw new Error('E2E_ANT_EVIDENCE_DIR must stay inside docs/pruebas/evidencias-ant-redis/.')
  }
}
const evidencePath = path.join(evidenceDirectory, redisMode ? 'ant-redis-simulated-success.png' : '09-ant-simulado.png')
const children = []
const syntheticCedula = '0000000000'
const redisContainerName = `consultas-sri-ant-redis-demo-${process.pid}`
let redisContainerStarted = false

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

function runCommandCapture(command, args, cwd = repositoryRoot) {
  return new Promise((resolve, reject) => {
    const child = spawn(command, args, {
      cwd,
      env: process.env,
      stdio: ['ignore', 'pipe', 'pipe'],
      shell: false,
      windowsHide: true,
    })
    let stdout = ''
    child.stdout.on('data', chunk => { stdout += chunk.toString() })
    child.stderr.on('data', () => undefined)
    child.once('error', () => reject(new Error('A required local command could not be started.')))
    child.once('exit', code => code === 0
      ? resolve(stdout.trim())
      : reject(new Error('A required local command exited unsuccessfully.')))
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
let redisPort
let redisSecret
let redisServerVersion
try {
  if (redisMode) {
    redisPort = await availablePort()
    await runCommandCapture('docker', [
      'run', '--rm', '--detach', '--name', redisContainerName,
      '--publish', `127.0.0.1:${redisPort}:6379`, 'redis:7.4-alpine',
    ])
    redisContainerStarted = true
    const deadline = Date.now() + 45_000
    let redisReady = false
    while (Date.now() < deadline) {
      try {
        const pong = await runCommandCapture('docker', ['exec', redisContainerName, 'redis-cli', 'ping'])
        if (pong === 'PONG') {
          redisReady = true
          break
        }
      } catch {
        await delay(350)
      }
    }
    if (!redisReady) throw new Error('The isolated Redis 7.4 container did not become ready.')
    const redisInfo = await runCommandCapture('docker', [
      'exec', redisContainerName, 'redis-cli', 'INFO', 'server',
    ])
    const redisVersionMatch = /^redis_version:(7\.4\.\d+)/m.exec(redisInfo)
    if (!redisVersionMatch) {
      throw new Error('The isolated Redis container did not report version 7.4.')
    }
    redisServerVersion = redisVersionMatch[1]
    console.log(`Isolated Redis server version verified: ${redisServerVersion}; published on loopback only.`)
    redisSecret = randomBytes(32).toString('hex')
  }

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

  const backendArgs = [
    '-jar', jarPath,
    '--spring.profiles.active=e2e,local-simulation',
    '--server.address=127.0.0.1',
    `--server.port=${backendPort}`,
    '--app.ant.simulation.enabled=true',
    `--app.ant.simulation.scenario=${redisMode ? 'SUCCESS_THEN_TIMEOUT' : 'SUCCESS'}`,
    '--app.ant.simulation.total-points=20',
    `--integrations.sri.taxpayer-existence-url=${stubBase}/sri/taxpayer-existence`,
    `--integrations.sri.taxpayer-details-url=${stubBase}/sri/taxpayer-details`,
    `--integrations.sri.vehicle-url=${stubBase}/sri/vehicle`,
  ]
  const backendEnvironment = redisMode
    ? {
        ...process.env,
        REDIS_CACHE_ENABLED: 'true',
        REDIS_HOST: '127.0.0.1',
        REDIS_PORT: String(redisPort),
        REDIS_CACHE_TTL: '5m',
        REDIS_CACHE_HMAC_SECRET: redisSecret,
      }
    : { ...process.env, REDIS_CACHE_ENABLED: 'false' }
  if (!redisMode) backendArgs.push('--app.cache.redis.enabled=false')
  else backendArgs.push('--app.cache.redis.enabled=true')
  const backend = startProcess('java', backendArgs, { cwd: backendRoot, env: backendEnvironment })
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
  const playwrightConfig = redisMode
    ? 'e2e/ant-demo.redis.playwright.config.ts'
    : 'e2e/ant-demo.playwright.config.ts'
  const playwrightEnvironment = {
    ...process.env,
    E2E_BASE_URL: frontendBase,
    E2E_ANT_EVIDENCE_PATH: evidencePath,
    E2E_ANT_EVIDENCE_DIRECTORY: evidenceDirectory,
    E2E_ANT_REDIS_EVIDENCE_DIRECTORY: evidenceDirectory,
  }
  await runCommand(process.execPath, [playwrightCli, 'test', `--config=${playwrightConfig}`], {
    cwd: frontendRoot,
    env: playwrightEnvironment,
    label: redisMode ? 'Redis-backed ANT demo Playwright suite' : 'Isolated ANT demo Playwright suite',
  })

  if (stub.getRequestCount() !== 0) throw new Error('The ANT demo unexpectedly invoked an SRI provider adapter.')
  if (backendOutput.join('').includes(syntheticCedula)) {
    throw new Error('Backend logs exposed the synthetic identification during the ANT demo.')
  }
  if (redisSecret && backendOutput.join('').includes(redisSecret)) {
    throw new Error('Backend logs exposed the temporary HMAC secret during the Redis demo.')
  }
  console.log(redisMode
    ? 'Local ANT demo and Redis recovery E2E passed against an isolated Redis 7.4 container; no external provider was called.'
    : 'Local ANT demo E2E passed; simulator ran on loopback, SRI stub received no request, and no external service was configured.')
  console.log(`Synthetic Playwright evidence was saved under ${path.relative(repositoryRoot, evidenceDirectory)}.`)
} catch (error) {
  console.error(error instanceof Error ? error.message : 'Local ANT demo E2E orchestration failed.')
  if (backendOutput.length > 0) {
    const safeDiagnostics = backendOutput.join('').split(/\r?\n/)
      .filter(line => /APPLICATION FAILED|Exception|Caused by|\bERROR\b|Failed to start|failure analysis/i.test(line))
      .slice(-20)
      .map(line => line
        .replaceAll(syntheticCedula, '[redacted]')
        .replaceAll(redisSecret ?? '\u0000', '[redacted]')
        .replace(/(password|hmac-secret)\s*[=:]\s*\S+/gi, '$1=[redacted]'))
    if (safeDiagnostics.length > 0) console.error(safeDiagnostics.join('\n'))
  }
  process.exitCode = 1
} finally {
  await stopChildren()
  if (stub?.server) {
    stub.server.closeAllConnections()
    await new Promise(resolve => stub.server.close(() => resolve()))
  }
  if (redisContainerStarted) {
    try {
      await runCommandCapture('docker', ['stop', redisContainerName])
    } catch {
      console.error('The isolated Redis demo container could not be stopped automatically; stop only the container created for this run.')
      process.exitCode = 1
    }
  }
}
